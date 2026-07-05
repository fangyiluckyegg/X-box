package com.prj.framework.web.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.prj.common.constant.Constants;
import com.prj.common.core.domain.model.LoginUser;
import com.prj.common.core.redis.RedisCache;
import com.prj.common.utils.uuid.IdUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
// [P1-FIX] jjwt 0.12.x 移除 SignatureAlgorithm，改用 Jwts.SIG + Keys
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

@Component
public class TokenService
{
    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    // 令牌自定义标识
    @Value("${token.header}")
    private String header;

    // 令牌秘钥
    @Value("${token.secret}")
    private String secret;

    // 令牌有效期（默认30分钟）
    @Value("${token.expireTime}")
    private int expireTime;

    protected static final long MILLIS_SECOND = 1000;

    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;

    private static final Long MILLIS_MINUTE_TEN = 20 * 60 * 1000L;

    @Autowired
    private RedisCache redisCache;

    // 获取用户身份信息
    public LoginUser getLoginUser(HttpServletRequest request)
    {
        // 获取请求携带的令牌
        String token = getToken(request);
        if (StringUtils.isNotEmpty(token))
        {
            try
            {
                Claims claims = parseToken(token);
                // 解析对应的权限以及用户信息
                String uuid = (String) claims.get(Constants.LOGIN_USER_TOKEN_KEY);
                String userKey = getTokenKey(uuid);
                LoginUser user = redisCache.getCacheObject(userKey);
                return user;
            }
            catch (WeakKeyException e)
            {
                // [P1-FIX] jjwt 0.12.x 升级后，旧 HS512 签发的 token 验签时抛出 WeakKeyException
                // （密钥 384 bits < HS512 要求的 512 bits），属于预期行为，降级为 debug 避免日志噪音
                logger.debug("Legacy HS512 token rejected by HS256 key, user needs to re-login");
            }
            catch (Exception e)
            {
                // [P0-FIX] 补充异常日志，避免Token解析异常被静默吞没
                logger.error("Token parse error", e);
            }
        }
        return null;
    }

    //删除用户的登录信息
    public void delLoginUser(String token)
    {
        if (StringUtils.isNotEmpty(token))
        {
            String userKey = getTokenKey(token);
            redisCache.deleteObject(userKey);
        }
    }

    // 创建token
    public String createToken(LoginUser loginUser)
    {
        String token = IdUtils.fastUUID();
        loginUser.setToken(token);
        refreshToken(loginUser);

        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put(Constants.LOGIN_USER_TOKEN_KEY, token);
        return createToken(tokenMap);
    }

    // 验证并刷新用户的token有效期
    public void verifyToken(LoginUser loginUser)
    {
        long expireTime = loginUser.getExpireTime();
        long currentTime = System.currentTimeMillis();
        if (expireTime - currentTime <= MILLIS_MINUTE_TEN)
        {
            refreshToken(loginUser);
        }
    }

    // 刷新token有效期
    public void refreshToken(LoginUser loginUser)
    {
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + expireTime * MILLIS_MINUTE);
        // 根据uuid将loginUser缓存
        String userKey = getTokenKey(loginUser.getToken());
        redisCache.setCacheObject(userKey, loginUser, expireTime, TimeUnit.MINUTES);
    }

    // [P1-FIX] jjwt 0.12.x 需要使用 SecretKey 对象，密钥长度需 ≥ 32 字节（256 bits）
    //          使用 Keys.hmacShaKeyFor() 从字节数组构建密钥
    private SecretKey getSigningKey()
    {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // 生成token
    // [P1-FIX] jjwt 0.12.x API 适配：
    //   setClaims(Map) → claims(Map)
    //   signWith(SignatureAlgorithm.HS512, String) → signWith(SecretKey, Jwts.SIG.HS256)
    private String createToken(Map<String, Object> claims)
    {
        String token = Jwts.builder()
                .claims(claims)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
        return token;
    }

    /** 从令牌中获取数据声明
     * @param token 令牌
     * @return 数据声明
     */
    // [P1-FIX] jjwt 0.12.x API 适配：
    //   parser().setSigningKey(String) → parser().verifyWith(SecretKey).build()
    //   parseClaimsJws(token).getBody() → parseSignedClaims(token).getPayload()
    private Claims parseToken(String token)
    {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从令牌中获取用户名
     * @param token 令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token)
    {
        try
        {
            Claims claims = parseToken(token);
            return claims.getSubject();
        }
        catch (Exception e)
        {
            // [P1-FIX] 添加异常防护，解析失败时返回 null 而非抛出异常
            logger.debug("Failed to get username from token", e);
            return null;
        }
    }

    /** 获取请求token
     * @param request
     * @return token
     */
    private String getToken(HttpServletRequest request)
    {
        String token = request.getHeader(header);
        if (StringUtils.isNotEmpty(token) && token.startsWith(Constants.TOKEN_PREFIX))
        {
            token = token.replace(Constants.TOKEN_PREFIX, "");
        }
        return token;
    }

    private String getTokenKey(String uuid)
    {
        return Constants.LOGIN_TOKEN_KEY + uuid;
    }
}

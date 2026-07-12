package com.prj.common.core.domain.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;


/**
 * 用户实体（领域模型，可序列化）。
 *
 * <p>职责：
 * 表示系统用户的核心属性：用户 ID、账号、昵称、密码（bcrypt 哈希）、角色。
 * 同时承担"序列化安全"职责——密码字段被禁止参与 Redis（fastjson2）与 JSON 序列化，避免哈希落盘/外泄。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code UserMapper}（持久化）、{@code LoginUser}（包装为 UserDetails）、
 *           {@code UserDetailsServiceImpl}、{@code TokenService}（存入 Redis）等。
 *
 * <p>安全说明：
 * - role 取值 ADMIN / USER（最小角色列方案），见 [C1]；
 * - 密码字段上的 @JSONField(serialize=false)/@JsonIgnore 用于阻断序列化，见 [C1/C9]。
 */
public class User implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;
    /** 用户账号 */
    private String userName;
    /** 用户昵称 */
    private String nickName;
    /** 密码（bcrypt 哈希，禁止序列化落盘） */
    private String password;
    /** 角色：ADMIN / USER（C1 最小角色列方案） */
    private String role;

    /** 获取用户ID。 */
    public Long getUserId()
    {
        return userId;
    }
    /** 设置用户ID。 */
    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    /** 获取用户昵称。 */
    public String getNickName()
    {
        return nickName;
    }
    /** 设置用户昵称。 */
    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    //@NotBlank(message = "用户账号不能为空")
    /** 获取用户账号。 */
    public String getUserName()
    {
        return userName;
    }
    /** 设置用户账号。 */
    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    @JsonIgnore
    @JsonProperty
    // [C1/C9] 密码不参与 Redis（fastjson2）序列化，杜绝 bcrypt 哈希落盘
    /** 获取密码（被标记为不序列化，防止哈希外泄）。 */
    @JSONField(serialize = false)
    public String getPassword()
    {
        return password;
    }
    /** 设置密码（应为 bcrypt 哈希值）。 */
    public void setPassword(String password)
    {
        this.password = password;
    }

    // [C1] 角色字段读写（最小角色列方案，取值 ADMIN / USER）
    /** 获取角色。 */
    public String getRole()
    {
        return role;
    }
    /** 设置角色（ADMIN / USER）。 */
    public void setRole(String role)
    {
        this.role = role;
    }

    /** 以多行格式输出对象字段（不含密码）便于日志打印。 */
    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("userId", getUserId())
            .append("userName", getUserName())
            .append("nickName", getNickName())
            .append("role", getRole())
            .toString();
    }
}

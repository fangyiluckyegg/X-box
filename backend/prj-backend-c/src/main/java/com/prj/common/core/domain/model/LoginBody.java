package com.prj.common.core.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求体模型（DTO）。
 *
 * <p>职责：
 * 承载前端登录提交的用户名、密码、验证码与 uuid，并通过 JSR-303 注解声明输入校验规则，
 * 由 LoginController 的 {@code @Valid} 在绑定请求时触发。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code LoginController}（接收请求体）、{@code LoginService}（消费字段进行登录校验）。
 *
 * <p>安全说明：用户名/密码/验证码的非空与长度约束见上方 [P0-FIX] 备注，防止空/超长输入。
 */
public class LoginBody
{
    /**     * 用户名     */
    // [P0-FIX] 输入校验：用户名不能为空，长度3-50
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3到50个字符之间")
    private String username;
    /**     * 用户密码     */
    // [P0-FIX] 输入校验：密码不能为空，长度6-50
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在6到50个字符之间")
    private String password;
    /**     * 验证码     */
    @NotBlank(message = "验证码不能为空")
    private String code;
    /**     * 唯一标识（前端获取验证码时拿到的 uuid，用于服务端匹配缓存中的验证码答案）     */
    private String uuid = "";

    /** 获取用户名。 */
    public String getUsername()
    {
        return username;
    }
    /** 设置用户名。 */
    public void setUsername(String username)
    {
        this.username = username;
    }

    /** 获取密码。 */
    public String getPassword()
    {
        return password;
    }
    /** 设置密码。 */
    public void setPassword(String password)
    {
        this.password = password;
    }

    /** 获取验证码。 */
    public String getCode()
    {
        return code;
    }
    /** 设置验证码。 */
    public void setCode(String code)
    {
        this.code = code;
    }

    /** 获取uuid。 */
    public String getUuid()
    {
        return uuid;
    }
    /** 设置uuid（默认空串）。 */
    public void setUuid(String uuid)
    {
        this.uuid = uuid;
    }
}

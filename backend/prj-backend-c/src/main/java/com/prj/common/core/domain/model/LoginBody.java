package com.prj.common.core.domain.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

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
    /**     * 唯一标识     */
    private String uuid = "";

    public String getUsername()
    {
        return username;
    }
    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }
    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getCode()
    {
        return code;
    }
    public void setCode(String code)
    {
        this.code = code;
    }

    public String getUuid()
    {
        return uuid;
    }
    public void setUuid(String uuid)
    {
        this.uuid = uuid;
    }
}

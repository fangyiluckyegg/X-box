package com.prj.common.core.domain.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;


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

    public Long getUserId()
    {
        return userId;
    }
    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getNickName()
    {
        return nickName;
    }
    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    //@NotBlank(message = "用户账号不能为空")
    public String getUserName()
    {
        return userName;
    }
    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    @JsonIgnore
    @JsonProperty
    // [C1/C9] 密码不参与 Redis（fastjson2）序列化，杜绝 bcrypt 哈希落盘
    @JSONField(serialize = false)
    public String getPassword()
    {
        return password;
    }
    public void setPassword(String password)
    {
        this.password = password;
    }

    // [C1] 角色字段读写（最小角色列方案，取值 ADMIN / USER）
    public String getRole()
    {
        return role;
    }
    public void setRole(String role)
    {
        this.role = role;
    }

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

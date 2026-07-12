package com.prj.mapper;

import com.prj.common.core.domain.entity.User;


/**
 * 用户 Mapper 接口（数据访问层）。
 *
 * <p>职责：
 * 定义用户表的数据库查询契约，目前仅提供"按用户名查询用户"一种能力，
 * 由 MyBatis 实现，主要服务于登录认证时的用户加载。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code com.prj.common.core.domain.entity.User}（用户实体）。
 * - 被依赖：{@code UserDetailsServiceImpl}（Spring Security 加载用户）、
 *           {@code UserServiceImpl} 等。
 */
public interface UserMapper
{
    /**
     * 根据用户名查询用户。
     *
     * @param userName 登录用户名
     * @return 匹配的用户实体；未找到返回 null
     */
    public User selectUserByUserName(String userName);
}

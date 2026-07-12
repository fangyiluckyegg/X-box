package com.prj.service;

import com.prj.common.core.domain.entity.User;

/**
 * 用户业务服务接口（Service 层契约）。
 *
 * <p>职责：
 * 定义用户相关的业务查询能力，目前提供"按用户名查询用户"，
 * 具体实现见 {@code com.prj.service.impl.UserServiceImpl}。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code com.prj.common.core.domain.entity.User}。
 * - 被依赖：认证流程中负责加载用户信息的组件（如 UserDetailsServiceImpl）。
 */
public interface IUserService
{
    /**
     * 根据用户名查询用户。
     *
     * @param userName 登录用户名
     * @return 用户实体；未找到返回 null
     */
    public User selectUserByUserName(String userName);
}

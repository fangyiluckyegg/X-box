package com.prj.service.impl;

import com.prj.common.core.domain.entity.User;
import com.prj.mapper.UserMapper;
import com.prj.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * 用户业务服务实现类。
 *
 * <p>职责：
 * 实现 {@link IUserService}，将"按用户名查询用户"请求委派给 {@code UserMapper} 完成数据库访问。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code UserMapper}（数据访问）、{@code User}（实体）。
 * - 被依赖：{@code UserDetailsServiceImpl}（认证加载用户）等。
 */
@Service
public class UserServiceImpl implements IUserService
{
    /** 用户Mapper，由 Spring 自动注入。 */
    @Autowired
    private UserMapper userMapper;

    /**
     * 根据用户名查询用户（委托 Mapper 执行）。
     *
     * @param userName 登录用户名
     * @return 用户实体；未找到返回 null
     */
    @Override
    public User selectUserByUserName(String userName)
    {
        return userMapper.selectUserByUserName(userName);
    }


}

package com.prj.service.impl;

import com.prj.common.core.domain.entity.User;
import com.prj.mapper.UserMapper;
import com.prj.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class UserServiceImpl implements IUserService
{
    @Autowired
    private UserMapper userMapper;

    @Override
    public User selectUserByUserName(String userName)
    {
        return userMapper.selectUserByUserName(userName);
    }


}

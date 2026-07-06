package com.prj.controller;

import com.prj.common.constant.Constants;
import com.prj.common.core.domain.AjaxResult;
import com.prj.common.core.domain.model.LoginBody;
import com.prj.framework.web.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;


// [P0-FIX] 添加 @Validated 开启控制器级别输入校验
@Validated
@RestController
public class LoginController
{
    @Autowired
    private LoginService loginService;

    @PostMapping("/login")
    // [P0-FIX] @Valid 触发 LoginBody 上的 JSR-303 约束校验
    public AjaxResult login(@Valid @RequestBody LoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        // 完成登录，生成token
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

}

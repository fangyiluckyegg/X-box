package com.prj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// [P1-FIX] 移除 @EnableDiscoveryClient 及 Spring Cloud 依赖（Nacos注册已关闭，单体架构无需服务发现）


@SpringBootApplication
public class SpringBootApp
{
    public static void main(String[] args)
    {
        SpringApplication.run(SpringBootApp.class, args);
    }
}

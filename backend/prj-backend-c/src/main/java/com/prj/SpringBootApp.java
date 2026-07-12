package com.prj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// [P1-FIX] 移除 @EnableDiscoveryClient 及 Spring Cloud 依赖（Nacos注册已关闭，单体架构无需服务发现）


/**
 * 后端服务启动入口类（Spring Boot 应用引导类）。
 *
 * <p>职责：
 * 作为整个后端应用（prj-backend-c）的启动类，通过 {@code @SpringBootApplication} 开启自动装配、
 * 组件扫描与配置加载，并由 {@link #main(String[])} 方法启动内嵌 Web 容器。
 *
 * <p>与其他模块的关联：
 * 被 Spring 容器在启动时被加载；扫描 {@code com.prj} 包及其子包下的所有带注解的组件
 * （controller / service / framework / common 等），是整套后端装配的起点。
 * 依赖项由 {@code application.yml} 及各 {@code @Configuration} 配置类（com.prj.framework.config）提供。
 *
 * <p>注意事项：
 * 已关闭微服务注册中心（Nacos）相关能力，当前为单体部署架构，详见上方 [P1-FIX] 备注。
 */
@SpringBootApplication
public class SpringBootApp
{
    /**
     * 应用主入口。
     *
     * @param args 命令行参数（当前未使用，可注入 Spring 环境参数）
     * @throws Exception 启动过程中若容器初始化失败会向上抛出，导致进程退出
     */
    public static void main(String[] args)
    {
        SpringApplication.run(SpringBootApp.class, args);
    }
}

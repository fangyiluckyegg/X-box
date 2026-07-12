package com.prj.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;


/**
 * 程序级注解配置（通用 Spring 配置）。
 *
 * <p>职责：
 * 集中开启两类框架能力——
 * 1) {@code @EnableAspectJAutoProxy(exposeProxy=true)}：暴露 AOP 代理对象，使业务代码可通过 {@code AopContext} 获取自身代理（用于同类内方法调用也走切面/事务）；
 * 2) {@code @MapperScan("com.prj.**.mapper")}：指定 MyBatis Mapper 接口扫描路径，使其自动注册为 Spring Bean。
 *
 * <p>与其他模块的关联：
 * - 被依赖：Spring 容器启动时被加载；影响所有 Mapper（如 EmployeeKpiMapper、UserMapper）的装配，
 *           以及需要 AOP 代理的服务（如事务、日志切面）。
 */
/** 程序注解配置 */
@Configuration
// 表示通过aop框架暴露该代理对象,AopContext能够访问
@EnableAspectJAutoProxy(exposeProxy = true)
// 指定要扫描的Mapper类的包的路径
@MapperScan("com.prj.**.mapper")
public class ApplicationConfig
{

}

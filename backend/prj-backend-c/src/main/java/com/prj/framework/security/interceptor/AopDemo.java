package com.prj.framework.security.interceptor;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AopDemo {

    //定义切点
    @Pointcut("execution(* com.prj.controller.*.*(..))")
    private void checkMem(){}

    //前置切点
    @Before("checkMem()")
    private void before(JoinPoint joinPoint){
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        System.out.println("Url is:" + request.getRequestURL().toString());
        System.out.println("method is:" + joinPoint.getSignature().getName());
    }

    @After("checkMem()")
    private void printMem(){
        System.out.println( "After method, Mem usage is:" + Runtime.getRuntime().freeMemory()/1024/1024  + "M");
    }

    @Around("checkMem()")
    private Object  around(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println( "Around method");
        //获取方法参数值数组
        Object[] args = joinPoint.getArgs();
        Object ret = joinPoint.proceed(args);
        System.out.println( "proceed args, result is: " + ret);
        //调用方法
        return ret;
    }

    @AfterReturning(pointcut = "checkMem()",returning = "returnObj")
    private void afterReturning(Object returnObj){
        System.out.println( "return value is:" + returnObj);
    }

    @AfterThrowing(pointcut = "checkMem()",throwing = "e")
    private void afterThrowing(JoinPoint joinPoint,Exception e){
        System.out.println( "Exception is:" + e.getMessage());
    }
}


package com.prj.framework.security.interceptor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MemPrintAspect
{
    @Before("@annotation(com.prj.framework.security.interceptor.PrintMem)")
    public void printMem(JoinPoint joinPoint)
    {
        System.out.println( joinPoint);
        System.out.println( Runtime.getRuntime().freeMemory()/1024/1024  + "M");
    }
}

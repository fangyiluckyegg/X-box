package com.prj.framework.security.interceptor;

import java.lang.annotation.*;


@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PrintMem
{
    public String name() default "";
}

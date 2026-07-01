package com.prj.framework.config;

import com.prj.framework.security.filter.ReqFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<ReqFilter> addReqFilter(){
        FilterRegistrationBean<ReqFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new ReqFilter());//设置过滤器名称
        filterRegistrationBean.addUrlPatterns("/*");//配置过滤规则
        filterRegistrationBean.setOrder(2);
        return filterRegistrationBean;
    }
}

package com.prj.framework.security.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class ReqFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("ReqFilter init");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        System.out.print("ReqFilter doFilter,url is:");
        String url = ((HttpServletRequest) servletRequest).getServletPath();
        System.out.println(url);
        if (url.indexOf("hacker") == -1) {
            filterChain.doFilter(servletRequest,servletResponse);
        }else {
            System.out.print("the url is filtered");
        }

    }

    @Override
    public void destroy() {
        System.out.println("ReqFilter destroy");
    }

}
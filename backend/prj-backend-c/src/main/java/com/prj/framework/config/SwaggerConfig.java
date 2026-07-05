package com.prj.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
public class SwaggerConfig
{
    // [P1-FIX] Swagger 生产环境关闭，通过配置控制
    @Value("${swagger.enabled:false}")
    private boolean swaggerEnabled;

    @Bean
    public Docket createRestApi()
    {
        return new Docket(DocumentationType.OAS_30)
                // [P1-FIX] 根据配置决定是否启用Swagger，生产环境默认关闭
                .enable(swaggerEnabled)
                // 用来指定Swagger信息
                .apiInfo(apiInfo())
                .select()
                // 扫描指定目录的接口
                .apis(RequestHandlerSelectors.basePackage("com.prj.controller"))  //指定控制器类的路径
                .paths(PathSelectors.any())
                .build();
    }

    //配置Swagger信息
    private ApiInfo apiInfo()    
    {
        return new ApiInfoBuilder()
                // 设置标题
                .title("标题：X工具箱_接口文档")
                // 描述
                .description("描述：Swagger接口文档")
                // 版本
                .version("版本号: 1.0" )
                .build();
    }
}

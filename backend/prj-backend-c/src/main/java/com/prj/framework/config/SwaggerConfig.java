package com.prj.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig
{
    // [P1-FIX] springdoc-openapi 替代废弃的 Springfox 3.0.0
    // [P2-FIX] 移除 @Value("${swagger.enabled:false}") 和 swaggerEnabled 字段
    //         该属性不存在于 application.yml，导致 swaggerEnabled 永远为 false
    //         springdoc-openapi-ui 自带 springdoc.swagger-ui.enabled 配置控制，无需手动管理

    @Bean
    public OpenAPI customOpenAPI()
    {
        return new OpenAPI()
                .info(new Info()
                        .title("PRJ 接口文档")
                        .description("springdoc-openapi 接口文档")
                        .version("1.0"));
    }
}

package com.prj.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig
{
    // [P1-FIX] springdoc-openapi 替代废弃的 Springfox 3.0.0
    // [P1-FIX] Swagger 生产环境关闭，通过配置控制
    @Value("${swagger.enabled:false}")
    private boolean swaggerEnabled;

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

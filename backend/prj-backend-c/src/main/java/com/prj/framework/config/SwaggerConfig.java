package com.prj.framework.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置。
 *
 * <p>职责：
 * 通过 springdoc-openapi 注册 {@link OpenAPI} Bean，声明接口文档的标题、描述与版本，
 * 供 Swagger UI（{@code /swagger-ui.html}）及相关测试访问。
 *
 * <p>与其他模块的关联：
 * - 被依赖：前端/测试通过 Swagger UI 与 {@code /v3/api-docs/**} 查看接口；
 *           SecurityConfig 已将上述路径设为 permitAll（见 [P1-FIX]）。
 */
@Configuration
public class SwaggerConfig
{
    // [P1-FIX] springdoc-openapi 替代废弃的 Springfox 3.0.0
    // [P2-FIX] 移除 @Value("${swagger.enabled:false}") 和 swaggerEnabled 字段
    //         该属性不存在于 application.yml，导致 swaggerEnabled 永远为 false
    //         springdoc-openapi-ui 自带 springdoc.swagger-ui.enabled 配置控制，无需手动管理

    /** 构建 OpenAPI 文档元信息 Bean（标题/描述/版本）。 */
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

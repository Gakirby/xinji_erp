package com.xinji.erp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI xinjiOpenAPI() {
        final String scheme = "bearer";
        return new OpenAPI()
                .info(new Info()
                        .title("鑫基矿业 · 矿石配送 ERP 接口文档")
                        .version("1.0.0")
                        .description("模块化单体。客户端接口 /api/client/**，管理后台接口 /api/admin/**。"
                                + "先调用 /api/auth/login 拿 token，再点右上角 Authorize 填入即可调试。"))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}

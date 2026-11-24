package com.msa.coupon.global.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** 공개 사용자와 운영자 API만 문서화하고 내부 주문 잠금 API와 Kafka 입력은 HTTP API로 표시하지 않습니다. */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI couponOpenApi(
            @Value("${coupon.openapi.gateway-base-url:http://localhost:8000}")
                    String gatewayBaseUrl) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("STOCKDROP Coupon")
                                .version("v1")
                                .description(
                                        "쿠폰 받기와 쿠폰함 및 운영자 API를 제공합니다. 내부 주문 잠금 API는"
                                                + " 문서에서 제외합니다."))
                // 기본 진입점은 일반 Gateway이며 직접 서비스 주소는 개발 확인용입니다.
                .servers(
                        List.of(
                                new Server()
                                        .url(gatewayBaseUrl)
                                        .description("일반 Gateway"),
                                new Server()
                                        .url("http://localhost:8088")
                                        .description("로컬 개발 서비스")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
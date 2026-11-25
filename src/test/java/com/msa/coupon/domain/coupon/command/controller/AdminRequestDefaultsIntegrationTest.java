package com.msa.coupon.domain.coupon.command.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.coupon.domain.coupon.repository.CouponDefinitionRepository;
import com.msa.coupon.global.config.database.CouponDatabaseTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/** 관리 API가 빠진 선택값에 기본값을 쓰고 잘못된 값은 칸 이름과 함께 거절하는지 검증합니다. */
@SpringBootTest(properties = "coupon.api.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CouponDatabaseTestSupport.Time.class)
class AdminRequestDefaultsIntegrationTest extends CouponDatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CouponDefinitionRepository definitions;

    static JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    String definitionBody(String optional) {
        return """
                {"name":"기본값 쿠폰","discountType":"AMOUNT","amount":1000,"scope":"ALL",\
                "validityMode":"RELATIVE","validMinutes":60%s}"""
                .formatted(optional);
    }

    String campaignBody(UUID definition, String optional) {
        return """
                {"code":"D%s","definitionId":"%s","kind":"CLAIM","totalBudget":10,\
                "perAccountLimit":1,"startsAt":"2030-01-01T00:00:00Z",\
                "endsAt":"2030-01-02T00:00:00Z"%s}"""
                .formatted(
                        UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                        definition,
                        optional);
    }

    UUID createDefinition(String optional) throws Exception {
        String body =
                mvc.perform(
                                post("/api/v1/admin/coupons/definitions")
                                        .with(admin())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(definitionBody(optional)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(mapper.readTree(body).path("data").path("id").asText());
    }

    @Test
    void missingOptionalValuesUseDefaults() throws Exception {
        UUID id = createDefinition("");
        var definition = definitions.findById(id).orElseThrow();
        assertThat(definition.minItemsAmount).isZero();
        assertThat(definition.noticeLeadMinutes).isEqualTo(24 * 60);
        mvc.perform(
                        post("/api/v1/admin/coupons/campaigns")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(campaignBody(id, "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.demo").value(false));
    }

    @Test
    void explicitValuesAreKept() throws Exception {
        UUID id = createDefinition(",\"minItemsAmount\":5000,\"noticeLeadMinutes\":10");
        var definition = definitions.findById(id).orElseThrow();
        assertThat(definition.minItemsAmount).isEqualTo(5000);
        assertThat(definition.noticeLeadMinutes).isEqualTo(10);
        mvc.perform(
                        post("/api/v1/admin/coupons/campaigns")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(campaignBody(id, ",\"demo\":true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.demo").value(true));
    }

    @Test
    void invalidValuesReturnFieldViolations() throws Exception {
        mvc.perform(
                        post("/api/v1/admin/coupons/definitions")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(definitionBody(",\"noticeLeadMinutes\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.violations[0].field").value("noticeLeadMinutes"));
        mvc.perform(
                        post("/api/v1/admin/coupons/definitions")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(definitionBody(",\"minItemsAmount\":-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("minItemsAmount"));
        mvc.perform(
                        post("/api/v1/admin/coupons/definitions")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(definitionBody(",\"minItemsAmount\":\"abc\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.violations[0].field").value("minItemsAmount"));
        UUID id = createDefinition("");
        mvc.perform(
                        post("/api/v1/admin/coupons/campaigns")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(campaignBody(id, ",\"demo\":\"yes\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("demo"));
    }
}

package com.msa.coupon.global.config.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.coupon.domain.campaign.command.service.CampaignService;
import com.msa.coupon.domain.campaign.dto.request.CampaignRequest;
import com.msa.coupon.domain.coupon.command.service.CouponDefinitionService;
import com.msa.coupon.domain.coupon.dto.request.DefinitionRequest;
import com.msa.coupon.global.config.database.CouponDatabaseTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

/** 공개 캠페인과 사용자 및 운영자 경계를 실제 MVC 응답으로 검증합니다. */
@SpringBootTest(properties = {"coupon.api.enabled=true", "springdoc.api-docs.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Import(CouponDatabaseTestSupport.Time.class)
class OpenApiContractTest extends CouponDatabaseTestSupport {
    @Autowired MockMvc mvc;
    @Autowired CouponDefinitionService definitions;
    @Autowired CampaignService campaigns;
    @Autowired MutableClock clock;

    @Test
    void publicCampaignClaimIdempotencyAndAuthorization() throws Exception {
        var now = clock.instant();
        var definition =
                definitions.create(
                        new DefinitionRequest(
                                "공개 쿠폰",
                                "AMOUNT",
                                1000L,
                                null,
                                null,
                                0,
                                "ALL",
                                "RELATIVE",
                                null,
                                null,
                                60,
                                10),
                        UUID.randomUUID());
        String code = "P" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        var campaign =
                campaigns.create(
                        new CampaignRequest(
                                code,
                                definition.id(),
                                "CLAIM",
                                null,
                                10,
                                1,
                                now.minusSeconds(1),
                                now.plusSeconds(3600),
                                false));
        campaigns.state(campaign.id(), "ACTIVE");
        mvc.perform(get("/api/v1/coupons/campaigns/" + code))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/coupons/me").header("X-Account-Id", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("COMMON_UNAUTHORIZED"));
        UUID account = UUID.randomUUID();
        var buyer =
                SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject(account.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
        mvc.perform(post("/api/v1/coupons/campaigns/" + code + "/claims").with(buyer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("success").value(true));
        mvc.perform(post("/api/v1/coupons/campaigns/" + code + "/claims").with(buyer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/coupons/definitions").with(buyer))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/coupons/me/applicable")
                                .with(buyer)
                                .param("productId", UUID.randomUUID().toString())
                                .param("itemsAmount", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openApiIncludesPublicEndpointsAndExcludesInternalLocks() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/me']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/me/applicable']").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/admin/coupons/campaigns/{id}/stats']").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/coupon-locks']").doesNotExist());
    }
}

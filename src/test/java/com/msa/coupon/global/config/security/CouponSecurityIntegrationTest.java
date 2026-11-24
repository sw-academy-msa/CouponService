package com.msa.coupon.global.config.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.coupon.domain.campaign.command.service.CampaignService;
import com.msa.coupon.domain.campaign.dto.request.CampaignRequest;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.domain.coupon.command.service.CouponClaimService;
import com.msa.coupon.domain.coupon.command.service.CouponDefinitionService;
import com.msa.coupon.domain.coupon.dto.request.DefinitionRequest;
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

import java.util.UUID;

/** 공개 캠페인, 사용자, 운영자, 내부 잠금 API의 인증 경계와 소유권 검사를 실제 MVC 응답으로 검증합니다. */
@SpringBootTest(
        properties = {
            "coupon.api.enabled=true",
            "ORDER_SERVICE_SUBJECT=" + CouponSecurityIntegrationTest.ORDER_SERVICE
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CouponDatabaseTestSupport.Time.class)
class CouponSecurityIntegrationTest extends CouponDatabaseTestSupport {
    static final String ORDER_SERVICE = "6d3b8c1e-0a52-4c5e-9a3c-2f1f6d7a9b10";

    @Autowired MockMvc mvc;
    @Autowired CouponDefinitionService definitions;
    @Autowired CampaignService campaigns;
    @Autowired CouponClaimService claims;
    @Autowired MutableClock clock;

    String activeClaimCampaign() {
        var now = clock.instant();
        var definition =
                definitions.create(
                        new DefinitionRequest(
                                "보안 테스트 쿠폰",
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
        String code = "S" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
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
        campaigns.state(campaign.id(), CouponCampaign.Status.ACTIVE);
        return code;
    }

    static JwtRequestPostProcessor buyer(UUID account) {
        return jwt().jwt(j -> j.subject(account.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }

    static JwtRequestPostProcessor orderService(String scope) {
        return jwt().jwt(
                        j ->
                                j.subject(ORDER_SERVICE)
                                        .claim("principal_type", "service")
                                        .claim("client_id", "order-command-service")
                                        .claim("scope", scope));
    }

    String lockBody(UUID order, UUID issue, UUID account, UUID seller, long itemsAmount) {
        return """
                {"orderId":"%s","couponIssueId":"%s","accountId":"%s","productId":"%s",\
                "sellerId":"%s","itemsAmount":%d,"holdUntil":"%s"}"""
                .formatted(
                        order,
                        issue,
                        account,
                        UUID.randomUUID(),
                        seller,
                        itemsAmount,
                        clock.instant().plusSeconds(300));
    }

    @Test
    void publicCampaignClaimIdempotencyAndRoleBoundaries() throws Exception {
        String code = activeClaimCampaign();
        mvc.perform(get("/api/v1/coupons/campaigns/" + code))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.claimed").isEmpty());
        // Gateway 헤더의 계정 ID는 인증 근거가 아닙니다.
        mvc.perform(get("/api/v1/coupons/me").header("X-Account-Id", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("COMMON_UNAUTHORIZED"));
        var buyer = buyer(UUID.randomUUID());
        mvc.perform(post("/api/v1/coupons/campaigns/" + code + "/claims").with(buyer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("success").value(true));
        mvc.perform(post("/api/v1/coupons/campaigns/" + code + "/claims").with(buyer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/coupons/campaigns/" + code).with(buyer))
                .andExpect(jsonPath("$.data.claimed").value(true));
        mvc.perform(get("/api/v1/admin/coupons/definitions").with(buyer))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/admin/coupons/definitions")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/coupons/me/applicable")
                                .with(buyer)
                                .param("productId", UUID.randomUUID().toString())
                                .param("itemsAmount", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalLockRequiresOrderServiceScopeAndIsIdempotent() throws Exception {
        String code = activeClaimCampaign();
        UUID account = UUID.randomUUID();
        UUID issue = claims.claim(code, account).coupon().issueId();
        UUID order = UUID.randomUUID();
        String body = lockBody(order, issue, account, UUID.randomUUID(), 30000);

        mvc.perform(
                        post("/internal/v1/coupon-locks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/internal/v1/coupon-locks")
                                .with(buyer(account))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/internal/v1/coupon-locks")
                                .with(orderService("product.stock"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) {
            mvc.perform(
                            post("/internal/v1/coupon-locks")
                                    .with(orderService("product.stock coupon.lock"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.state").value("LOCKED"))
                    .andExpect(jsonPath("$.data.couponIssueId").value(issue.toString()));
        }
        mvc.perform(
                        post("/internal/v1/coupon-locks")
                                .with(orderService("coupon.lock"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(lockBody(order, issue, account, UUID.randomUUID(), 40000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COUPON_LOCK_CONFLICT"));
        // 잠긴 쿠폰은 운영자도 회수할 수 없습니다.
        mvc.perform(
                        post("/api/v1/admin/coupons/issues/" + issue + "/revoke")
                                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COUPON_NOT_AVAILABLE"));
    }

    @Test
    void lockRejectsOtherAccountAndSellerOwnProduct() throws Exception {
        String code = activeClaimCampaign();
        UUID account = UUID.randomUUID();
        UUID issue = claims.claim(code, account).coupon().issueId();

        mvc.perform(
                        post("/internal/v1/coupon-locks")
                                .with(orderService("coupon.lock"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        lockBody(
                                                UUID.randomUUID(),
                                                issue,
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                30000)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("COUPON_NOT_OWNED"));
        mvc.perform(
                        post("/internal/v1/coupon-locks")
                                .with(orderService("coupon.lock"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(lockBody(UUID.randomUUID(), issue, account, account, 30000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COUPON_SELF_PRODUCT"));
    }
}

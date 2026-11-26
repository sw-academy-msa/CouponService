package com.msa.coupon.domain.campaign;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.coupon.domain.campaign.command.service.ClaimCampaignNoticeService;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.coupon.entity.CouponDefinition;
import com.msa.coupon.domain.coupon.repository.CouponDefinitionRepository;
import com.msa.coupon.domain.outbox.repository.OutboxEventRepository;
import com.msa.coupon.global.config.database.CouponDatabaseTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

/** 실제 DB 잠금과 Outbox 원자성, 발송 조건 및 관리자 권한을 검증합니다. */
@SpringBootTest(properties = {"coupon.api.enabled=true", "coupon.claim-notification-enabled=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(CouponDatabaseTestSupport.Time.class)
class ClaimCampaignNoticeIntegrationTest extends CouponDatabaseTestSupport {
    @Autowired CouponCampaignRepository campaigns;
    @Autowired CouponDefinitionRepository definitions;
    @Autowired OutboxEventRepository outbox;
    @Autowired ClaimCampaignNoticeService service;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    CouponCampaign campaign(
            CouponCampaign.Kind kind,
            CouponCampaign.Status status,
            int issued,
            Instant starts,
            Instant ends) {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var d =
                definitions.save(
                        CouponDefinition.builder()
                                .id(UUID.randomUUID())
                                .name("새해 쿠폰")
                                .discountType("AMOUNT")
                                .amount(1000L)
                                .scope("ALL")
                                .validityMode("RELATIVE")
                                .validMinutes(60)
                                .noticeLeadMinutes(10)
                                .status(CouponDefinition.Status.ACTIVE)
                                .createdBy(UUID.randomUUID())
                                .createdAt(now)
                                .updatedAt(now)
                                .build());
        return campaigns.save(
                CouponCampaign.builder()
                        .id(UUID.randomUUID())
                        .code("C" + UUID.randomUUID().toString().replace("-", "").toUpperCase())
                        .definitionId(d.id)
                        .kind(kind)
                        .status(status)
                        .totalBudget(10)
                        .issuedCount(issued)
                        .perAccountLimit(1)
                        .startsAt(starts)
                        .endsAt(ends)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
    }

    @Test
    void concurrentRequestsCreateExactlyOneOutboxAndReturnOriginalResult() throws Exception {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var c =
                campaign(
                        CouponCampaign.Kind.CLAIM,
                        CouponCampaign.Status.ACTIVE,
                        0,
                        now,
                        now.plusSeconds(60));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.send(c.id));
            var b = pool.submit(() -> service.send(c.id));
            assertThat(a.get(10, TimeUnit.SECONDS).notificationSentAt()).isEqualTo(now);
            assertThat(b.get(10, TimeUnit.SECONDS).notificationSentAt()).isEqualTo(now);
        }
        var rows =
                outbox.findAll().stream()
                        .filter(e -> e.partitionKey.equals(c.id.toString()))
                        .toList();
        assertThat(rows).hasSize(1);
        var payload = json.readTree(rows.getFirst().payload);
        assertThat(payload.path("type").asText()).isEqualTo("CLAIM_CAMPAIGN_NOTIFICATION");
        assertThat(payload.path("campaignCode").asText()).isEqualTo(c.code);
        assertThat(payload.path("couponName").asText()).isEqualTo("새해 쿠폰");
        assertThat(rows.getFirst().topic).isEqualTo("stockdrop.notifications.campaign.v1");
    }

    @Test
    void rejectsWrongKindInactiveOutsidePeriodAndExhaustedBudget() {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var end = now.plusSeconds(60);
        for (var c :
                java.util.List.of(
                        campaign(
                                CouponCampaign.Kind.MEMBER,
                                CouponCampaign.Status.ACTIVE,
                                0,
                                now,
                                end),
                        campaign(
                                CouponCampaign.Kind.CLAIM,
                                CouponCampaign.Status.PAUSED,
                                0,
                                now,
                                end),
                        campaign(
                                CouponCampaign.Kind.CLAIM,
                                CouponCampaign.Status.ACTIVE,
                                0,
                                now.plusSeconds(1),
                                end),
                        campaign(
                                CouponCampaign.Kind.CLAIM,
                                CouponCampaign.Status.ACTIVE,
                                0,
                                now.minusSeconds(60),
                                now),
                        campaign(
                                CouponCampaign.Kind.CLAIM,
                                CouponCampaign.Status.ACTIVE,
                                10,
                                now,
                                end))) {
            assertThatThrownBy(() -> service.send(c.id))
                    .isInstanceOf(com.msa.coupon.global.error.BusinessException.class);
            assertThat(campaigns.findById(c.id).orElseThrow().notificationSentAt).isNull();
            assertThat(
                            outbox.findAll().stream()
                                    .noneMatch(e -> e.partitionKey.equals(c.id.toString())))
                    .isTrue();
        }
    }

    @Test
    void onlyAdminCanRequestNotification() throws Exception {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var c =
                campaign(
                        CouponCampaign.Kind.CLAIM,
                        CouponCampaign.Status.ACTIVE,
                        0,
                        now,
                        now.plusSeconds(60));
        String url = "/api/v1/admin/coupons/campaigns/" + c.id + "/notifications";
        mvc.perform(post(url).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationSentAt").isNotEmpty());
    }
}

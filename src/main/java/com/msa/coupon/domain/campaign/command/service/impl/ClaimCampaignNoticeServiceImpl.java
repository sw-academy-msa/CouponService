package com.msa.coupon.domain.campaign.command.service.impl;

import com.msa.coupon.domain.campaign.command.service.ClaimCampaignNoticeService;
import com.msa.coupon.domain.campaign.dto.response.CampaignResponse;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.domain.campaign.exception.*;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.coupon.exception.*;
import com.msa.coupon.domain.coupon.repository.CouponDefinitionRepository;
import com.msa.coupon.domain.outbox.entity.OutboxEvent;
import com.msa.coupon.domain.outbox.repository.OutboxEventRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** 캠페인 행 잠금으로 동시 요청을 직렬화하고 Outbox와 발송 표시를 함께 커밋합니다. */
@Service
@ConditionalOnProperty(name = "coupon.claim-notification-enabled", havingValue = "true")
@Transactional(timeout = 5)
public class ClaimCampaignNoticeServiceImpl implements ClaimCampaignNoticeService {
    private final CouponCampaignRepository campaigns;
    private final CouponDefinitionRepository definitions;
    private final OutboxEventRepository outbox;
    private final ObjectMapper json;
    private final Clock clock;

    public ClaimCampaignNoticeServiceImpl(
            CouponCampaignRepository campaigns,
            CouponDefinitionRepository definitions,
            OutboxEventRepository outbox,
            ObjectMapper json,
            Clock clock) {
        this.campaigns = campaigns;
        this.definitions = definitions;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    public CampaignResponse send(UUID id) {
        var c =
                campaigns
                        .lock(id)
                        .orElseThrow(
                                () -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        if (c.kind != CouponCampaign.Kind.CLAIM || c.productId != null)
            throw new CouponException(CouponErrorCode.INVALID);
        // 재요청은 캠페인이 마감된 뒤에도 최초 접수 결과를 반환합니다.
        if (c.notificationSentAt != null) return CampaignResponse.from(c);
        var now = clock.instant();
        if (c.status != CouponCampaign.Status.ACTIVE
                || now.isBefore(c.startsAt)
                || !now.isBefore(c.endsAt))
            throw new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_ACTIVE);
        if (c.issuedCount >= c.totalBudget)
            throw new CampaignException(CampaignErrorCode.CAMPAIGN_SOLD_OUT);
        var d =
                definitions
                        .findById(c.definitionId)
                        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
        UUID eventId =
                UUID.nameUUIDFromBytes(("CLAIM_CAMPAIGN:" + c.id).getBytes(StandardCharsets.UTF_8));
        var payload =
                Map.of(
                        "eventId",
                        eventId,
                        "schemaVersion",
                        1,
                        "type",
                        "CLAIM_CAMPAIGN_NOTIFICATION",
                        "campaignId",
                        c.id,
                        "campaignCode",
                        c.code,
                        "couponName",
                        d.name,
                        "deadline",
                        c.endsAt,
                        "occurredAt",
                        now);
        outbox.save(
                OutboxEvent.builder()
                        .id(eventId)
                        .topic("stockdrop.notifications.campaign.v1")
                        .aggregateId(c.id)
                        .payload(json.writeValueAsString(payload))
                        .now(now)
                        .build());
        c.notificationSentAt = now;
        c.updatedAt = now;
        return CampaignResponse.from(c);
    }
}

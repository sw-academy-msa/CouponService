package com.msa.coupon.domain.campaign.dto.response;

import com.msa.coupon.domain.campaign.entity.CouponCampaign;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 운영자와 공개 조회에 쓰는 캠페인 요약이며 엔티티를 그대로 응답하지 않습니다. */
@Builder
public record CampaignResponse(
        UUID id,
        String code,
        String kind,
        int totalBudget,
        int issuedCount,
        String status,
        Instant startsAt,
        Instant endsAt,
        boolean demo,
        Instant notificationSentAt,
        UUID productId) {
    @Override
    public String toString() {
        return "CampaignResponse[REDACTED]";
    }

    public static CampaignResponse from(CouponCampaign e) {
        return new CampaignResponse(
                e.id,
                e.code,
                e.kind.name(),
                e.totalBudget,
                e.issuedCount,
                e.status.name(),
                e.startsAt,
                e.endsAt,
                e.demo,
                e.notificationSentAt,
                e.productId);
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class CampaignResponseBuilder {
        @Override
        public String toString() {
            return "CampaignResponseBuilder[REDACTED]";
        }
    }
}

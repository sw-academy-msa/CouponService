package com.msa.coupon.domain.campaign.dto.response;

import jakarta.validation.constraints.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** HTTP 경계에서 원본 엔티티와 입력을 분리합니다. */
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
        boolean demo) {
    @Override
    public String toString() {
        return "CampaignResponse[REDACTED]";
    }

    public static CampaignResponse from(com.msa.coupon.domain.campaign.entity.CouponCampaign e) {
        return new CampaignResponse(
                e.id,
                e.code,
                e.kind,
                e.totalBudget,
                e.issuedCount,
                e.status,
                e.startsAt,
                e.endsAt,
                e.demo);
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class CampaignResponseBuilder {
        @Override
        public String toString() {
            return "CampaignResponseBuilder[REDACTED]";
        }
    }
}

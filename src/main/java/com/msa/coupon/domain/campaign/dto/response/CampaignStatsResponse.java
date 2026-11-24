package com.msa.coupon.domain.campaign.dto.response;

import lombok.Builder;

/** 플랫폼 할인 비용과 발급 상태를 운영 통계로 제공합니다. */
@Builder
public record CampaignStatsResponse(
        long issued, long used, long released, long expired, long discountTotal) {
    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class CampaignStatsResponseBuilder {
        @Override
        public String toString() {
            return "CampaignStatsResponseBuilder[REDACTED]";
        }
    }
}

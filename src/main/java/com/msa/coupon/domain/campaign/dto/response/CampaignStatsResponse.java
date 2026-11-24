package com.msa.coupon.domain.campaign.dto.response;

import lombok.Builder;

/** 플랫폼 할인 비용과 발급 상태를 운영 통계로 제공합니다. */
@Builder
public record CampaignStatsResponse(
        long issued, long used, long released, long expired, long discountTotal) {
    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class CampaignStatsResponseBuilder {
        @Override
        public String toString() {
            return "CampaignStatsResponseBuilder[REDACTED]";
        }
    }
}
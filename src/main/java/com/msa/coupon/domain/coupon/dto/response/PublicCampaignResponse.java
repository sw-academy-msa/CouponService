package com.msa.coupon.domain.coupon.dto.response;

import com.msa.coupon.domain.campaign.dto.response.CampaignResponse;

import lombok.Builder;

/** 비로그인 캠페인 조회 응답입니다. claimed는 로그인했을 때만 채웁니다. */
@Builder
public record PublicCampaignResponse(
        CampaignResponse campaign,
        DefinitionResponse definition,
        int remaining,
        Boolean claimed) {
    @Override
    public String toString() {
        return "PublicCampaignResponse[REDACTED]";
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class PublicCampaignResponseBuilder {
        @Override
        public String toString() {
            return "PublicCampaignResponseBuilder[REDACTED]";
        }
    }
}
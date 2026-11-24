package com.msa.coupon.domain.coupon.dto.response;

import jakarta.validation.constraints.*;

import lombok.Builder;

/** HTTP 경계에서 원본 엔티티와 입력을 분리합니다. */
@Builder
public record PublicCampaignResponse(
        com.msa.coupon.domain.campaign.dto.response.CampaignResponse campaign,
        DefinitionResponse definition,
        int remaining,
        Boolean claimed) {
    @Override
    public String toString() {
        return "PublicCampaignResponse[REDACTED]";
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class PublicCampaignResponseBuilder {
        @Override
        public String toString() {
            return "PublicCampaignResponseBuilder[REDACTED]";
        }
    }
}

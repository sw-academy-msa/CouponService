package com.msa.coupon.domain.campaign.dto.response;

import com.msa.coupon.domain.campaign.dto.request.CampaignTarget;

import java.util.UUID;

/** 외부 HTTP 동안 임대 토큰만 보유하여 연결을 반환합니다. */
public record TargetWork(UUID eventId, UUID token, CampaignTarget target) {
    @Override
    public String toString() {
        return "TargetWork[REDACTED]";
    }
}
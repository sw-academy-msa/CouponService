package com.msa.coupon.domain.campaign.dto.request;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 계산된 대상의 예산과 동의를 발급 서비스에서 다시 검증합니다. */
@Builder
public record CampaignTarget(
        UUID eventId,
        int schemaVersion,
        String type,
        String ruleCode,
        String campaignCode,
        UUID accountId,
        UUID productId,
        String subjectKey,
        boolean demo,
        Instant deadline,
        Instant occurredAt) {
    @Override
    public String toString() {
        return "CampaignTarget[REDACTED]";
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class CampaignTargetBuilder {
        @Override
        public String toString() {
            return "CampaignTargetBuilder[REDACTED]";
        }
    }
}

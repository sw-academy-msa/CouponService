package com.msa.coupon.domain.campaign.dto.request;

import jakarta.validation.constraints.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** HTTP 경계에서 원본 엔티티와 입력을 분리합니다. */
@Builder
public record CampaignRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,40}") String code,
        @NotNull UUID definitionId,
        @NotNull String kind,
        String ruleCode,
        @Positive int totalBudget,
        @Min(1) @Max(1) int perAccountLimit,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        boolean demo) {
    @Override
    public String toString() {
        return "CampaignRequest[REDACTED]";
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class CampaignRequestBuilder {
        @Override
        public String toString() {
            return "CampaignRequestBuilder[REDACTED]";
        }
    }
}

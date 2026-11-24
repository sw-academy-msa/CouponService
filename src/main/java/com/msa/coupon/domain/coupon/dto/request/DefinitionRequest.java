package com.msa.coupon.domain.coupon.dto.request;

import jakarta.validation.constraints.*;

import lombok.Builder;

import java.time.Instant;

/** HTTP 경계에서 원본 엔티티와 입력을 분리합니다. */
@Builder
public record DefinitionRequest(
        @NotBlank @Size(max = 60) String name,
        @NotNull String discountType,
        Long amount,
        Integer rateBps,
        Long maxDiscount,
        @PositiveOrZero long minItemsAmount,
        @NotNull String scope,
        @NotNull String validityMode,
        Instant validFrom,
        Instant validUntil,
        Integer validMinutes,
        @Positive int noticeLeadMinutes) {
    @Override
    public String toString() {
        return "DefinitionRequest[REDACTED]";
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class DefinitionRequestBuilder {
        @Override
        public String toString() {
            return "DefinitionRequestBuilder[REDACTED]";
        }
    }
}

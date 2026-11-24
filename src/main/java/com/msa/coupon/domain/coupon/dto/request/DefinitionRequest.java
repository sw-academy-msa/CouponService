package com.msa.coupon.domain.coupon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.Builder;

import java.time.Instant;

/** 운영자 쿠폰 정의 생성 요청입니다. 할인 방식별 필수 값은 서비스가 다시 검증합니다. */
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

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class DefinitionRequestBuilder {
        @Override
        public String toString() {
            return "DefinitionRequestBuilder[REDACTED]";
        }
    }
}
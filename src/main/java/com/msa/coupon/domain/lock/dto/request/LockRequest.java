package com.msa.coupon.domain.lock.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** order-command의 잠금 요청입니다. accountId는 서비스 토큰을 검증한 내부 호출에서만 받습니다. */
@Builder
public record LockRequest(
        @NotNull UUID orderId,
        @NotNull UUID couponIssueId,
        @NotNull UUID accountId,
        @NotNull UUID productId,
        @NotNull UUID sellerId,
        @Positive long itemsAmount,
        @NotNull Instant holdUntil) {
    @Override
    public String toString() {
        return "LockRequest[REDACTED]";
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class LockRequestBuilder {
        @Override
        public String toString() {
            return "LockRequestBuilder[REDACTED]";
        }
    }
}
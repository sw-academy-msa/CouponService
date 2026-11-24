package com.msa.coupon.domain.lock.dto.request;

import jakarta.validation.constraints.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** HTTP 경계에서 원본 엔티티와 입력을 분리합니다. */
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

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class LockRequestBuilder {
        @Override
        public String toString() {
            return "LockRequestBuilder[REDACTED]";
        }
    }
}

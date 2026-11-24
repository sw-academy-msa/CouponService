package com.msa.coupon.domain.lock.dto.request;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** Kafka 입력 계약입니다. HTTP DTO가 아닙니다. */
@Builder
public record OrderLifecycle(
        UUID eventId,
        int schemaVersion,
        String type,
        String phase,
        UUID orderId,
        long aggregateVersion,
        UUID buyerId,
        UUID sellerId,
        UUID productId,
        int quantity,
        long itemsAmount,
        UUID couponIssueId,
        boolean couponApplied,
        long couponDiscount,
        long membershipShippingDiscount,
        long totalAmount,
        String failureCode,
        boolean paymentStarted,
        Instant occurredAt) {
    @Override
    public String toString() {
        return "OrderLifecycle[REDACTED]";
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class OrderLifecycleBuilder {
        @Override
        public String toString() {
            return "OrderLifecycleBuilder[REDACTED]";
        }
    }
}

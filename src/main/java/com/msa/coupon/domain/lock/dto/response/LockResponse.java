package com.msa.coupon.domain.lock.dto.response;

import jakarta.validation.constraints.*;

import lombok.Builder;

import java.util.UUID;

/** HTTP 경계에서 원본 엔티티와 입력을 분리합니다. */
@Builder
public record LockResponse(
        UUID orderId,
        UUID couponIssueId,
        String discountType,
        Long amount,
        Integer rateBps,
        Long maxDiscount,
        long minItemsAmount,
        long definitionVersion,
        String state) {
    @Override
    public String toString() {
        return "LockResponse[REDACTED]";
    }

    public static LockResponse from(
            com.msa.coupon.domain.lock.entity.CouponLock l,
            com.msa.coupon.domain.coupon.entity.CouponIssue i) {
        return new LockResponse(
                l.orderId,
                i.id,
                i.discountType,
                i.amount,
                i.rateBps,
                i.maxDiscount,
                i.minItemsAmount,
                i.definitionVersion,
                l.state);
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class LockResponseBuilder {
        @Override
        public String toString() {
            return "LockResponseBuilder[REDACTED]";
        }
    }
}

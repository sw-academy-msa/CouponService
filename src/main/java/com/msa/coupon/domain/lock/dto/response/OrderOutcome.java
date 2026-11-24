package com.msa.coupon.domain.lock.dto.response;

import lombok.Builder;

import java.util.UUID;

/** 주문 서비스 조회 계약이며 알 수 없는 결과로 잠금을 해제하지 않습니다. */
@Builder
public record OrderOutcome(
        UUID orderId, String phase, Boolean couponApplied, Long couponDiscount, boolean notFound) {
    @Override
    public String toString() {
        return "OrderOutcome[REDACTED]";
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class OrderOutcomeBuilder {
        @Override
        public String toString() {
            return "OrderOutcomeBuilder[REDACTED]";
        }
    }
}
package com.msa.coupon.domain.coupon.dto.response;

import lombok.Builder;

/** 예상 할인은 표시용이며 주문 잠금 시 조건을 다시 검증합니다. */
@Builder
public record ApplicableCouponResponse(IssueResponse coupon, long estimatedDiscount) {
    @Override
    public String toString() {
        return "ApplicableCouponResponse[REDACTED]";
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class ApplicableCouponResponseBuilder {
        @Override
        public String toString() {
            return "ApplicableCouponResponseBuilder[REDACTED]";
        }
    }
}

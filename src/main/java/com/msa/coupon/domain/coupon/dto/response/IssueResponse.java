package com.msa.coupon.domain.coupon.dto.response;

import com.msa.coupon.domain.coupon.entity.CouponIssue;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 발급 쿠폰 응답입니다. 발급 시점 스냅샷만 보여 주고 계정 ID와 원천 참조는 내보내지 않습니다. */
@Builder
public record IssueResponse(
        UUID issueId,
        String name,
        String discountType,
        Long amount,
        Integer rateBps,
        Long maxDiscount,
        long minItemsAmount,
        String scope,
        UUID productId,
        Instant validUntil,
        String status) {
    @Override
    public String toString() {
        return "IssueResponse[REDACTED]";
    }

    public static IssueResponse from(CouponIssue e) {
        return new IssueResponse(
                e.id,
                e.name,
                e.discountType,
                e.amount,
                e.rateBps,
                e.maxDiscount,
                e.minItemsAmount,
                e.scope,
                e.productId,
                e.validUntil,
                e.status.name());
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class IssueResponseBuilder {
        @Override
        public String toString() {
            return "IssueResponseBuilder[REDACTED]";
        }
    }
}
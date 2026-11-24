package com.msa.coupon.domain.campaign.dto.request;

import jakarta.validation.constraints.Positive;

import lombok.Builder;

/** 운영자 예산 변경 요청입니다. 줄이는 요청은 서비스가 거절합니다. */
@Builder
public record BudgetRequest(@Positive int totalBudget) {
    @Override
    public String toString() {
        return "BudgetRequest[REDACTED]";
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class BudgetRequestBuilder {
        @Override
        public String toString() {
            return "BudgetRequestBuilder[REDACTED]";
        }
    }
}
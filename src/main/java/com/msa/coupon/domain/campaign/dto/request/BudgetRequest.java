package com.msa.coupon.domain.campaign.dto.request;

import jakarta.validation.constraints.*;

import lombok.Builder;

/** HTTP 경계에서 원본 엔티티와 입력을 분리합니다. */
@Builder
public record BudgetRequest(@Positive int totalBudget) {
    @Override
    public String toString() {
        return "BudgetRequest[REDACTED]";
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class BudgetRequestBuilder {
        @Override
        public String toString() {
            return "BudgetRequestBuilder[REDACTED]";
        }
    }
}

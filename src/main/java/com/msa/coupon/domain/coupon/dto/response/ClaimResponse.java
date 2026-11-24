package com.msa.coupon.domain.coupon.dto.response;

import jakarta.validation.constraints.*;

import lombok.Builder;

/** HTTP 경계에서 원본 엔티티와 입력을 분리합니다. */
@Builder
public record ClaimResponse(IssueResponse coupon, boolean created) {
    @Override
    public String toString() {
        return "ClaimResponse[REDACTED]";
    }

    /** ??? ??? ???? ?? ???? ???? ????. */
    public static class ClaimResponseBuilder {
        @Override
        public String toString() {
            return "ClaimResponseBuilder[REDACTED]";
        }
    }
}

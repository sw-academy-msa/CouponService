package com.msa.coupon.domain.coupon.dto.response;

import lombok.Builder;

/** 받기 결과와 새 발급 여부입니다. 컨트롤러가 201과 200을 고르는 데만 씁니다. */
@Builder
public record ClaimResponse(IssueResponse coupon, boolean created) {
    @Override
    public String toString() {
        return "ClaimResponse[REDACTED]";
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class ClaimResponseBuilder {
        @Override
        public String toString() {
            return "ClaimResponseBuilder[REDACTED]";
        }
    }
}
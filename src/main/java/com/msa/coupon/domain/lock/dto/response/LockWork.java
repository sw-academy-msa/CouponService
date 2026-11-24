package com.msa.coupon.domain.lock.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 외부 조회 동안 데이터베이스 연결을 반환하기 위한 작업 스냅샷입니다. */
@Builder
public record LockWork(UUID orderId, UUID token, Instant lockedAt) {
    @Override
    public String toString() {
        return "LockWork[REDACTED]";
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class LockWorkBuilder {
        @Override
        public String toString() {
            return "LockWorkBuilder[REDACTED]";
        }
    }
}
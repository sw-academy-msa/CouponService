package com.msa.coupon.domain.campaign.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 회원 구독 세대와 청구 주기를 발급 멱등 키로 사용합니다. */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record MembershipEvent(
        UUID eventId,
        int schemaVersion,
        String type,
        String source,
        UUID accountId,
        UUID subscriptionId,
        long generation,
        long aggregateVersion,
        int cycleNumber,
        Instant entitlementUntil,
        Instant occurredAt) {
    @Override
    public String toString() {
        return "MembershipEvent[REDACTED]";
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class MembershipEventBuilder {
        @Override
        public String toString() {
            return "MembershipEventBuilder[REDACTED]";
        }
    }
}
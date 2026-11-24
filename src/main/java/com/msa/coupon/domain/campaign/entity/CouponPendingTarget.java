package com.msa.coupon.domain.campaign.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 쿠폰 상태 원본을 보관하며 외부 서비스와 트랜잭션을 공유하지 않습니다. */
@Entity
@Table(name = "coupon_pending_targets", schema = "coupon")
public class CouponPendingTarget {
    /** 동의 확인 HTTP가 30분 안에 끝나지 않으면 FAILED로 남깁니다. */
    public enum Status {
        PENDING,
        DONE,
        FAILED
    }

    @Id public UUID eventId;

    @Column(columnDefinition = "text")
    public String payload;

    public int attempts;
    public Instant nextAttemptAt;
    public UUID leaseToken;
    public Instant leaseUntil;

    @Enumerated(EnumType.STRING)
    public Status status;

    public Instant createdAt;

    protected CouponPendingTarget() {}

    @Builder
    public CouponPendingTarget(
            UUID eventId,
            String payload,
            int attempts,
            Instant nextAttemptAt,
            UUID leaseToken,
            Instant leaseUntil,
            Status status,
            Instant createdAt) {
        this.eventId = eventId;
        this.payload = payload;
        this.attempts = attempts;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseToken = leaseToken;
        this.leaseUntil = leaseUntil;
        this.status = status;
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "CouponPendingTarget[REDACTED]";
    }

    /** 내부 식별자를 빌더 문자열에 노출하지 않습니다. */
    public static class CouponPendingTargetBuilder {
        @Override
        public String toString() {
            return "CouponPendingTargetBuilder[REDACTED]";
        }
    }
}
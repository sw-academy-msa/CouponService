package com.msa.coupon.domain.lock.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 주문 하나에 쿠폰 한 장을 잠근 기록입니다. orderId가 기본키라 같은 주문 재요청이 멱등합니다. */
@Entity
@Table(name = "coupon_locks", schema = "coupon")
public class CouponLock {
    /** 주문 결과를 모르면 LOCKED를 유지하고 USED, RELEASED는 끝 상태입니다. */
    public enum State {
        LOCKED,
        USED,
        RELEASED
    }

    @Id public UUID orderId;
    public UUID issueId;
    public UUID accountId;
    public UUID productId;
    public long itemsAmount;
    public String requestHash;

    @Enumerated(EnumType.STRING)
    public State state;

    public Instant holdUntil;
    public Instant nextCheckAt;
    public int checkAttempts;
    public UUID leaseToken;
    public Instant leaseUntil;
    public String finishReason;
    public Long discountApplied;
    public Instant lockedAt;
    public Instant finishedAt;
    public long version;

    protected CouponLock() {}

    @Builder
    public CouponLock(
            UUID orderId,
            UUID issueId,
            UUID accountId,
            UUID productId,
            long itemsAmount,
            String requestHash,
            State state,
            Instant holdUntil,
            Instant nextCheckAt,
            int checkAttempts,
            UUID leaseToken,
            Instant leaseUntil,
            String finishReason,
            Long discountApplied,
            Instant lockedAt,
            Instant finishedAt,
            long version) {
        this.orderId = orderId;
        this.issueId = issueId;
        this.accountId = accountId;
        this.productId = productId;
        this.itemsAmount = itemsAmount;
        this.requestHash = requestHash;
        this.state = state;
        this.holdUntil = holdUntil;
        this.nextCheckAt = nextCheckAt;
        this.checkAttempts = checkAttempts;
        this.leaseToken = leaseToken;
        this.leaseUntil = leaseUntil;
        this.finishReason = finishReason;
        this.discountApplied = discountApplied;
        this.lockedAt = lockedAt;
        this.finishedAt = finishedAt;
        this.version = version;
    }

    @Override
    public String toString() {
        return "CouponLock[REDACTED]";
    }

    /** 내부 식별자를 빌더 문자열에 노출하지 않습니다. */
    public static class CouponLockBuilder {
        @Override
        public String toString() {
            return "CouponLockBuilder[REDACTED]";
        }
    }
}
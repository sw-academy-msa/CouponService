package com.msa.coupon.domain.coupon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 발급, 잠금, 사용, 해제 이력입니다. 운영 통계와 분쟁 확인용으로 상태 변경과 같은 트랜잭션에 씁니다. */
@Entity
@Table(name = "coupon_issue_history", schema = "coupon")
public class CouponIssueHistory {
    @Id public UUID id;
    public UUID issueId;
    public UUID campaignId;
    public UUID accountId;
    public String action;
    public UUID orderId;
    public String actor;
    public String reason;
    public Instant occurredAt;

    protected CouponIssueHistory() {}

    @Builder
    public CouponIssueHistory(
            UUID id,
            UUID issueId,
            UUID campaignId,
            UUID accountId,
            String action,
            UUID orderId,
            String actor,
            String reason,
            Instant occurredAt) {
        this.id = id;
        this.issueId = issueId;
        this.campaignId = campaignId;
        this.accountId = accountId;
        this.action = action;
        this.orderId = orderId;
        this.actor = actor;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    @Override
    public String toString() {
        return "CouponIssueHistory[REDACTED]";
    }

    /** 내부 식별자를 빌더 문자열에 노출하지 않습니다. */
    public static class CouponIssueHistoryBuilder {
        @Override
        public String toString() {
            return "CouponIssueHistoryBuilder[REDACTED]";
        }
    }
}
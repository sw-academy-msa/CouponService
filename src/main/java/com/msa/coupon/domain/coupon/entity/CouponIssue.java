package com.msa.coupon.domain.coupon.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 쿠폰 상태 원본을 보관하며 외부 서비스와 트랜잭션을 공유하지 않습니다. */
@Entity
@Table(name = "coupon_issues", schema = "coupon")
public class CouponIssue {
    @Id public UUID id;
    public UUID campaignId;
    public UUID definitionId;
    public long definitionVersion;
    public UUID accountId;
    public String subjectKey;
    public UUID productId;
    public String name;
    public String discountType;
    public Long amount;
    public Integer rateBps;
    public Long maxDiscount;
    public long minItemsAmount;
    public String scope;
    public String status;
    public Instant validFrom;
    public Instant validUntil;
    public String source;
    public String sourceRef;
    public UUID usedOrderId;
    public Instant usedAt;
    public Long discountApplied;
    public Instant expiringNoticedAt;
    public Instant issuedAt;
    public long version;
    public int noticeLeadMinutes;

    protected CouponIssue() {}

    @Builder
    public CouponIssue(
            UUID id,
            UUID campaignId,
            UUID definitionId,
            long definitionVersion,
            UUID accountId,
            String subjectKey,
            UUID productId,
            String name,
            String discountType,
            Long amount,
            Integer rateBps,
            Long maxDiscount,
            long minItemsAmount,
            String scope,
            String status,
            Instant validFrom,
            Instant validUntil,
            String source,
            String sourceRef,
            UUID usedOrderId,
            Instant usedAt,
            Long discountApplied,
            Instant expiringNoticedAt,
            Instant issuedAt,
            long version,
            int noticeLeadMinutes) {
        this.id = id;
        this.campaignId = campaignId;
        this.definitionId = definitionId;
        this.definitionVersion = definitionVersion;
        this.accountId = accountId;
        this.subjectKey = subjectKey;
        this.productId = productId;
        this.name = name;
        this.discountType = discountType;
        this.amount = amount;
        this.rateBps = rateBps;
        this.maxDiscount = maxDiscount;
        this.minItemsAmount = minItemsAmount;
        this.scope = scope;
        this.status = status;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.source = source;
        this.sourceRef = sourceRef;
        this.usedOrderId = usedOrderId;
        this.usedAt = usedAt;
        this.discountApplied = discountApplied;
        this.expiringNoticedAt = expiringNoticedAt;
        this.issuedAt = issuedAt;
        this.version = version;
        this.noticeLeadMinutes = noticeLeadMinutes;
    }

    @Override
    public String toString() {
        return "CouponIssue[REDACTED]";
    }

    /** 내부 식별자를 빌더 문자열에 노출하지 않습니다. */
    public static class CouponIssueBuilder {
        @Override
        public String toString() {
            return "CouponIssueBuilder[REDACTED]";
        }
    }
}

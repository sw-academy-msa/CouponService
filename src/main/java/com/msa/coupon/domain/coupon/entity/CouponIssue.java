package com.msa.coupon.domain.coupon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 계정 한 명의 쿠폰 한 장입니다. 상태 변경은 조건부 UPDATE나 행 잠금 뒤에만 합니다. */
@Entity
@Table(name = "coupon_issues", schema = "coupon")
public class CouponIssue {
    /** LOCKED 동안에는 유효기간이 지나도 만료하지 않고 USED, EXPIRED, REVOKED는 끝 상태입니다. */
    public enum Status {
        ISSUED,
        LOCKED,
        USED,
        EXPIRED,
        REVOKED
    }

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

    @Enumerated(EnumType.STRING)
    public Status status;

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
            Status status,
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
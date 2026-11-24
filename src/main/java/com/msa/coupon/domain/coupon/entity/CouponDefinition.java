package com.msa.coupon.domain.coupon.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 쿠폰 상태 원본을 보관하며 외부 서비스와 트랜잭션을 공유하지 않습니다. */
@Entity
@Table(name = "coupon_definitions", schema = "coupon")
public class CouponDefinition {
    @Id public UUID id;
    public String name;
    public String discountType;
    public Long amount;
    public Integer rateBps;
    public Long maxDiscount;
    public long minItemsAmount;
    public String scope;
    public String validityMode;
    public Instant validFrom;
    public Instant validUntil;
    public Integer validMinutes;
    public String status;
    public long version;
    public UUID createdBy;
    public Instant createdAt;
    public Instant updatedAt;
    public int noticeLeadMinutes;

    protected CouponDefinition() {}

    @Builder
    public CouponDefinition(
            UUID id,
            String name,
            String discountType,
            Long amount,
            Integer rateBps,
            Long maxDiscount,
            long minItemsAmount,
            String scope,
            String validityMode,
            Instant validFrom,
            Instant validUntil,
            Integer validMinutes,
            String status,
            long version,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt,
            int noticeLeadMinutes) {
        this.id = id;
        this.name = name;
        this.discountType = discountType;
        this.amount = amount;
        this.rateBps = rateBps;
        this.maxDiscount = maxDiscount;
        this.minItemsAmount = minItemsAmount;
        this.scope = scope;
        this.validityMode = validityMode;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.validMinutes = validMinutes;
        this.status = status;
        this.version = version;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.noticeLeadMinutes = noticeLeadMinutes;
    }

    @Override
    public String toString() {
        return "CouponDefinition[REDACTED]";
    }

    /** 내부 식별자를 빌더 문자열에 노출하지 않습니다. */
    public static class CouponDefinitionBuilder {
        @Override
        public String toString() {
            return "CouponDefinitionBuilder[REDACTED]";
        }
    }
}

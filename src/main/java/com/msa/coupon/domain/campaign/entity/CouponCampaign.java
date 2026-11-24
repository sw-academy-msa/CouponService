package com.msa.coupon.domain.campaign.entity;

import jakarta.persistence.*;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 쿠폰 상태 원본을 보관하며 외부 서비스와 트랜잭션을 공유하지 않습니다. */
@Entity
@Table(name = "coupon_campaigns", schema = "coupon")
public class CouponCampaign {
    @Id public UUID id;
    public String code;
    public UUID definitionId;
    public String kind;
    public String ruleCode;
    public int totalBudget;
    public int issuedCount;
    public int perAccountLimit;
    public Instant startsAt;
    public Instant endsAt;
    public String status;
    public boolean demo;
    public long rowVersion;
    public Instant createdAt;
    public Instant updatedAt;

    protected CouponCampaign() {}

    @Builder
    public CouponCampaign(
            UUID id,
            String code,
            UUID definitionId,
            String kind,
            String ruleCode,
            int totalBudget,
            int issuedCount,
            int perAccountLimit,
            Instant startsAt,
            Instant endsAt,
            String status,
            boolean demo,
            long rowVersion,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.definitionId = definitionId;
        this.kind = kind;
        this.ruleCode = ruleCode;
        this.totalBudget = totalBudget;
        this.issuedCount = issuedCount;
        this.perAccountLimit = perAccountLimit;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
        this.demo = demo;
        this.rowVersion = rowVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "CouponCampaign[REDACTED]";
    }

    /** 내부 식별자를 빌더 문자열에 노출하지 않습니다. */
    public static class CouponCampaignBuilder {
        @Override
        public String toString() {
            return "CouponCampaignBuilder[REDACTED]";
        }
    }
}

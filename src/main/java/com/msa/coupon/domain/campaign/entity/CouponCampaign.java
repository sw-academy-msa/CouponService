package com.msa.coupon.domain.campaign.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 캠페인 예산 원본입니다. issued_count는 조건부 UPDATE로만 늘려 예산을 넘지 않게 합니다. */
@Entity
@Table(name = "coupon_campaigns", schema = "coupon")
public class CouponCampaign {
    /** ACTIVE이고 기간 안일 때만 발급합니다. */
    public enum Status {
        DRAFT,
        ACTIVE,
        PAUSED,
        ENDED
    }

    /** CLAIM은 사용자 받기, TARGETED는 AI 대상, MEMBER는 회원 구독 이벤트 발급입니다. */
    public enum Kind {
        CLAIM,
        TARGETED,
        MEMBER
    }

    @Id public UUID id;
    public String code;
    public UUID definitionId;

    @Enumerated(EnumType.STRING)
    public Kind kind;

    public String ruleCode;
    public int totalBudget;
    public int issuedCount;
    public int perAccountLimit;
    public Instant startsAt;
    public Instant endsAt;

    @Enumerated(EnumType.STRING)
    public Status status;

    public boolean demo;
    public long rowVersion;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant notificationSentAt;

    protected CouponCampaign() {}

    @Builder
    public CouponCampaign(
            UUID id,
            String code,
            UUID definitionId,
            Kind kind,
            String ruleCode,
            int totalBudget,
            int issuedCount,
            int perAccountLimit,
            Instant startsAt,
            Instant endsAt,
            Status status,
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

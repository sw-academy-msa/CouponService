package com.msa.coupon.domain.receipt.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 처리한 Kafka 이벤트 ID와 본문 해시입니다. 같은 ID의 다른 본문을 계약 오류로 가립니다. */
@Entity
@Table(name = "coupon_event_receipts", schema = "coupon")
public class CouponEventReceipt {
    @Id public UUID eventId;
    public String topic;
    public String payloadHash;
    public Instant processedAt;

    protected CouponEventReceipt() {}

    @Builder
    public CouponEventReceipt(UUID eventId, String topic, String payloadHash, Instant processedAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.payloadHash = payloadHash;
        this.processedAt = processedAt;
    }

    @Override
    public String toString() {
        return "CouponEventReceipt[REDACTED]";
    }

    /** 내부 식별자를 빌더 문자열에 노출하지 않습니다. */
    public static class CouponEventReceiptBuilder {
        @Override
        public String toString() {
            return "CouponEventReceiptBuilder[REDACTED]";
        }
    }
}
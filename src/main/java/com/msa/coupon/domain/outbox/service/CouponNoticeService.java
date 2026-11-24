package com.msa.coupon.domain.outbox.service;

import java.time.Instant;
import java.util.UUID;

/** 업무 트랜잭션 안에 알림 원본을 함께 기록합니다. */
public interface CouponNoticeService {
    void append(
            String source,
            String type,
            UUID aggregate,
            long version,
            UUID account,
            UUID product,
            UUID issue,
            Instant deadline);
}
package com.msa.coupon.domain.lock.client;

import com.msa.coupon.domain.lock.dto.response.OrderOutcome;

import java.util.UUID;

/** 주문 원본 조회가 확정된 경우에만 복구 판단에 사용합니다. */
public interface OrderOutcomeClient {
    OrderOutcome outcome(UUID orderId);
}
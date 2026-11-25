package com.msa.coupon.domain.lock.service;

import com.msa.coupon.domain.lock.dto.request.LockRequest;
import com.msa.coupon.domain.lock.dto.response.LockResponse;

/** 주문 식별자로 멱등 잠금을 제공하고 유니크 경합을 복구합니다. */
public interface CouponLockService {
    LockResponse lock(LockRequest request);
}
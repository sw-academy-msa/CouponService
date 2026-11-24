package com.msa.coupon.domain.lock.service;

import com.msa.coupon.domain.lock.dto.request.*;
import com.msa.coupon.domain.lock.dto.response.*;

import java.util.*;

/** 짧은 잠금·임대·완료 트랜잭션을 외부 HTTP 실행과 분리합니다. */
public interface CouponLockWorkStore {
    LockResponse lock(LockRequest r);

    List<LockWork> claim();

    void complete(LockWork work, OrderOutcome outcome);

    void lifecycle(UUID orderId, String phase, boolean applied, long discount);
}

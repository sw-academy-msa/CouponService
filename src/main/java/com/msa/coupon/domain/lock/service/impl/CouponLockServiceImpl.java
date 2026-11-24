package com.msa.coupon.domain.lock.service.impl;

import com.msa.coupon.domain.lock.dto.request.*;
import com.msa.coupon.domain.lock.dto.response.*;
import com.msa.coupon.domain.lock.service.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/** 경합 롤백 이후 재요청하여 기존 주문의 멱등 응답을 찾습니다. */
@Service
@RequiredArgsConstructor
public class CouponLockServiceImpl implements CouponLockService {
    private final CouponLockWorkStore store;

    public LockResponse lock(LockRequest r) {
        try {
            return store.lock(r);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return store.lock(r);
        } catch (com.msa.coupon.domain.coupon.exception.CouponException conflict) {
            if (conflict.getErrorCode()
                    != com.msa.coupon.domain.coupon.exception.CouponErrorCode.COUPON_NOT_AVAILABLE)
                throw conflict;
            // 경합 트랜잭션이 끝난 뒤 주문 잠금부터 다시 읽어 잠금 순서를 유지합니다.
            return store.lock(r);
        }
    }
}

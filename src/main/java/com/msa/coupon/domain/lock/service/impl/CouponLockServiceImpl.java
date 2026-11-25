package com.msa.coupon.domain.lock.service.impl;

import com.msa.coupon.domain.coupon.exception.CouponErrorCode;
import com.msa.coupon.domain.coupon.exception.CouponException;
import com.msa.coupon.domain.lock.dto.request.LockRequest;
import com.msa.coupon.domain.lock.dto.response.LockResponse;
import com.msa.coupon.domain.lock.service.CouponLockService;
import com.msa.coupon.domain.lock.service.CouponLockWorkStore;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 경합 롤백 이후 재요청하여 기존 주문의 멱등 응답을 찾습니다. */
@Service
public class CouponLockServiceImpl implements CouponLockService {
    private final CouponLockWorkStore store;

    public CouponLockServiceImpl(CouponLockWorkStore store) {
        this.store = store;
    }

    public LockResponse lock(LockRequest r) {
        try {
            return store.lock(r);
        } catch (DataIntegrityViolationException e) {
            return store.lock(r);
        } catch (CouponException conflict) {
            if (conflict.getErrorCode()
                    != CouponErrorCode.COUPON_NOT_AVAILABLE)
                throw conflict;
            // 경합 트랜잭션이 끝난 뒤 주문 잠금부터 다시 읽어 잠금 순서를 유지합니다.
            return store.lock(r);
        }
    }
}
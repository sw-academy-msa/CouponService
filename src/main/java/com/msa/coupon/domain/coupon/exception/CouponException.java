package com.msa.coupon.domain.coupon.exception;

import com.msa.coupon.global.error.BusinessException;

/** 쿠폰 오류 코드만 받아 공통 예외 처리기가 같은 형식으로 응답하게 합니다. */
public class CouponException extends BusinessException {
    public CouponException(CouponErrorCode code) {
        super(code);
    }
}
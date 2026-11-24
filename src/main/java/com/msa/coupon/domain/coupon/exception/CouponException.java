package com.msa.coupon.domain.coupon.exception;

import com.msa.coupon.global.error.BusinessException;

/** 업무 오류를 공통 응답 처리기로 전달합니다. */
public class CouponException extends BusinessException {
    public CouponException(CouponErrorCode code) {
        super(code);
    }
}
package com.msa.coupon.domain.lock.exception;

import com.msa.coupon.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 잠금 전용 오류입니다. 쿠폰 사용 불가는 CouponErrorCode를 그대로 씁니다. */
public enum LockErrorCode implements ErrorCode {
    COUPON_LOCK_CONFLICT(HttpStatus.CONFLICT, "COUPON_LOCK_CONFLICT", "이미 처리한 주문과 요청이 다릅니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    LockErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
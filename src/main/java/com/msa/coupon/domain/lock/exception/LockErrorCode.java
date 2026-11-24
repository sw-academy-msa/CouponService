package com.msa.coupon.domain.lock.exception;

import com.msa.coupon.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 입력값 없이 사용자에게 보여 줄 고정 업무 오류만 정의합니다. */
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

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}

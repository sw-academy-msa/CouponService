package com.msa.coupon.domain.coupon.exception;

import com.msa.coupon.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 쿠폰 업무 오류입니다. 잠금 거절 코드는 order-command가 쿠폰 없이 진행할지 판단하는 계약입니다. */
public enum CouponErrorCode implements ErrorCode {
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다."),
    COUPON_NOT_OWNED(HttpStatus.FORBIDDEN, "COUPON_NOT_OWNED", "본인의 쿠폰만 사용할 수 있습니다."),
    COUPON_NOT_AVAILABLE(HttpStatus.CONFLICT, "COUPON_NOT_AVAILABLE", "사용할 수 없는 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.CONFLICT, "COUPON_EXPIRED", "유효기간이 지난 쿠폰입니다."),
    COUPON_SCOPE_MISMATCH(HttpStatus.CONFLICT, "COUPON_SCOPE_MISMATCH", "이 상품에는 사용할 수 없습니다."),
    COUPON_MIN_AMOUNT(HttpStatus.CONFLICT, "COUPON_MIN_AMOUNT", "최소 상품 금액을 충족하지 않습니다."),
    COUPON_SELF_PRODUCT(HttpStatus.CONFLICT, "COUPON_SELF_PRODUCT", "본인의 상품에는 쿠폰을 사용할 수 없습니다."),
    COUPON_DEFINITION_IN_USE(
            HttpStatus.CONFLICT, "COUPON_DEFINITION_IN_USE", "이미 발급한 정의는 변경할 수 없습니다."),
    INVALID(HttpStatus.BAD_REQUEST, "COUPON_INVALID", "쿠폰 조건이 올바르지 않습니다."),
    // Kafka 계약 오류 전용입니다. HTTP 응답으로 내보내지 않고 DLT 예외 헤더에만 남습니다.
    INVALID_EVENT(HttpStatus.BAD_REQUEST, "COUPON_EVENT_INVALID", "COUPON_EVENT_INVALID");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CouponErrorCode(HttpStatus status, String code, String message) {
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
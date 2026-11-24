package com.msa.coupon.domain.receipt;

/** 계약 오류만 즉시 격리하며 외부 원문을 보관하지 않습니다. */
public class InvalidCouponEventException extends RuntimeException {
    public InvalidCouponEventException() {
        super("INVALID_COUPON_EVENT");
    }
}

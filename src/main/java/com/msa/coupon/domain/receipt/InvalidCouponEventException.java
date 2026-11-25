package com.msa.coupon.domain.receipt;

import com.msa.coupon.domain.coupon.exception.CouponErrorCode;
import com.msa.coupon.domain.coupon.exception.CouponException;

/**
 * 영구 계약 오류만 DLT로 격리하며 이벤트 원문, 계정 ID, 해시를 예외에 담지 않습니다. 타입은 Kafka 재시도 분류와 DLT 예외 헤더에
 * 쓰이므로 바꾸지 않습니다.
 */
public class InvalidCouponEventException extends CouponException {
    public InvalidCouponEventException() {
        super(CouponErrorCode.INVALID_EVENT);
    }
}

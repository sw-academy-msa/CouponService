package com.msa.coupon.domain.receipt.service;

import java.util.UUID;

/** 소비 트랜잭션과 receipt 기록을 같은 원자 경계에 묶습니다. */
public interface CouponReceiptService {
    boolean accept(UUID id, String topic, String hash);
}

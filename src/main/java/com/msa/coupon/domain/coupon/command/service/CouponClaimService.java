package com.msa.coupon.domain.coupon.command.service;

import com.msa.coupon.domain.coupon.dto.response.ClaimResponse;

import java.util.UUID;

/** 사용자 받기 요청을 처리하며 이미 받은 쿠폰은 오류가 아니라 기존 발급으로 돌려줍니다. */
public interface CouponClaimService {
    ClaimResponse claim(String code, UUID account);
}
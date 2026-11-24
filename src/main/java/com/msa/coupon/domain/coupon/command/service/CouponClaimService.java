package com.msa.coupon.domain.coupon.command.service;

import com.msa.coupon.domain.coupon.dto.response.ClaimResponse;

import java.util.UUID;

/** 도메인 경계를 인터페이스로 분리합니다. */
public interface CouponClaimService {
    ClaimResponse claim(String code, UUID account);
}
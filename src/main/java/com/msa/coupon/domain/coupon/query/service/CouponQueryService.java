package com.msa.coupon.domain.coupon.query.service;

import com.msa.coupon.domain.coupon.dto.response.ApplicableCouponResponse;
import com.msa.coupon.domain.coupon.dto.response.IssueResponse;
import com.msa.coupon.domain.coupon.dto.response.PublicCampaignResponse;

import java.util.List;
import java.util.UUID;

/** 사용자 조회는 JWT 주체의 쿠폰만 읽고 예상 할인액은 표시용으로만 계산합니다. */
public interface CouponQueryService {
    List<ApplicableCouponResponse> applicable(UUID account, UUID product, long items);

    PublicCampaignResponse campaign(String code, UUID account);

    List<IssueResponse> mine(UUID account, String status, int page, int size);
}
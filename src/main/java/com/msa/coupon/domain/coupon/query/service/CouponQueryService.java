package com.msa.coupon.domain.coupon.query.service;

import com.msa.coupon.domain.coupon.dto.response.ApplicableCouponResponse;
import com.msa.coupon.domain.coupon.dto.response.IssueResponse;
import com.msa.coupon.domain.coupon.dto.response.PublicCampaignResponse;

import java.util.List;
import java.util.UUID;

/** 도메인 경계를 인터페이스로 분리합니다. */
public interface CouponQueryService {
    List<ApplicableCouponResponse> applicable(UUID account, UUID product, long items);

    PublicCampaignResponse campaign(String code, UUID account);

    List<IssueResponse> mine(UUID account, String status, int page, int size);
}
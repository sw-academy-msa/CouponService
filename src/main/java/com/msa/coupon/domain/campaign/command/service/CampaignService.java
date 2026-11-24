package com.msa.coupon.domain.campaign.command.service;

import com.msa.coupon.domain.campaign.dto.request.CampaignRequest;
import com.msa.coupon.domain.campaign.dto.response.CampaignResponse;
import com.msa.coupon.domain.campaign.dto.response.CampaignStatsResponse;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;

import java.util.List;
import java.util.UUID;

/** 도메인 경계를 인터페이스로 분리합니다. */
public interface CampaignService {
    CampaignStatsResponse stats(UUID id);

    List<CampaignResponse> list();

    CampaignResponse create(CampaignRequest r);

    CampaignResponse state(UUID id, CouponCampaign.Status status);

    CampaignResponse budget(UUID id, int total);
}
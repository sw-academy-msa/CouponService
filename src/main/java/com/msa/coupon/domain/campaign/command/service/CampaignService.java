package com.msa.coupon.domain.campaign.command.service;

import com.msa.coupon.domain.campaign.dto.request.CampaignRequest;
import com.msa.coupon.domain.campaign.dto.response.CampaignResponse;
import com.msa.coupon.domain.campaign.dto.response.CampaignStatsResponse;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;

import java.util.List;
import java.util.UUID;

/** 캠페인은 초안으로 만들고 예산은 늘리기만 허용해 이미 발급한 수보다 줄지 않게 합니다. */
public interface CampaignService {
    CampaignStatsResponse stats(UUID id);

    List<CampaignResponse> list();

    CampaignResponse create(CampaignRequest r);

    CampaignResponse state(UUID id, CouponCampaign.Status status);

    CampaignResponse budget(UUID id, int total);
}
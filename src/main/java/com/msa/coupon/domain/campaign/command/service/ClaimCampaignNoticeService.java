package com.msa.coupon.domain.campaign.command.service;

import com.msa.coupon.domain.campaign.dto.response.CampaignResponse;

import java.util.UUID;

/** 받기형 캠페인의 광고 알림 요청을 한 번만 저장합니다. */
public interface ClaimCampaignNoticeService {
    CampaignResponse send(UUID id);
}

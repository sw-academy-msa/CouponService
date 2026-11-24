package com.msa.coupon.domain.campaign.command.service;

import com.msa.coupon.domain.campaign.dto.request.CampaignTarget;
import com.msa.coupon.domain.campaign.dto.response.TargetWork;

import java.util.List;

/** 동의 확인 HTTP와 원본 변경의 트랜잭션을 분리합니다. */
public interface PendingTargetStore {
    void enqueue(CampaignTarget event, String payload);

    List<TargetWork> claim();

    java.util.Optional<TargetWork> claim(java.util.UUID eventId);

    void complete(TargetWork work, Boolean eligible);
}

package com.msa.coupon.domain.campaign.command.service;

import com.msa.coupon.domain.coupon.dto.response.ClaimResponse;

import java.time.Instant;
import java.util.UUID;

/** 도메인 경계를 인터페이스로 분리합니다. */
public interface CampaignIssueStore {
    ClaimResponse issue(
            String code,
            UUID account,
            String subject,
            String source,
            String sourceRef,
            UUID product,
            Instant deadline);
}
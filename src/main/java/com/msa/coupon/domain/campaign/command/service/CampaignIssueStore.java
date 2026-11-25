package com.msa.coupon.domain.campaign.command.service;

import com.msa.coupon.domain.coupon.dto.response.ClaimResponse;

import java.time.Instant;
import java.util.UUID;

/** 예산 증가와 발급 INSERT를 한 트랜잭션으로 묶어 선착순 초과 발급과 중복 발급을 막습니다. */
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
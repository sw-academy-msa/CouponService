package com.msa.coupon.domain.campaign.command.service.impl;

import com.msa.coupon.domain.campaign.command.service.CampaignService;
import com.msa.coupon.domain.campaign.dto.request.*;
import com.msa.coupon.domain.campaign.dto.response.*;
import com.msa.coupon.domain.campaign.entity.*;
import com.msa.coupon.domain.campaign.exception.*;
import com.msa.coupon.domain.campaign.repository.*;
import com.msa.coupon.domain.coupon.dto.request.*;
import com.msa.coupon.domain.coupon.dto.response.*;
import com.msa.coupon.domain.coupon.entity.*;
import com.msa.coupon.domain.coupon.exception.*;
import com.msa.coupon.domain.coupon.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/** 캠페인 예산 감소와 기간 오류를 막습니다. */
@Service
@RequiredArgsConstructor
@Transactional(timeout = 5)
public class CampaignServiceImpl implements CampaignService {
    private final CouponIssueHistoryRepository histories;

    @Transactional(readOnly = true)
    public CampaignStatsResponse stats(UUID id) {
        if (!campaigns.existsById(id))
            throw new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        var row = histories.stats(id).getFirst();
        return new CampaignStatsResponse(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue());
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> list() {
        return campaigns.findAll().stream().map(CampaignResponse::from).toList();
    }

    private final CouponCampaignRepository campaigns;
    private final CouponDefinitionRepository definitions;
    private final Clock clock;

    public CampaignResponse create(CampaignRequest r) {
        if (!Set.of("CLAIM", "TARGETED", "MEMBER").contains(r.kind())
                || !r.startsAt().isBefore(r.endsAt())
                || r.perAccountLimit() != 1) throw new CouponException(CouponErrorCode.INVALID);
        var d =
                definitions
                        .findById(r.definitionId())
                        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
        if (!"ACTIVE".equals(d.status)
                || (Set.of("CLAIM", "MEMBER").contains(r.kind()) && !"ALL".equals(d.scope))
                || ("TARGETED".equals(r.kind())
                        && (!"PRODUCT".equals(d.scope)
                                || !Set.of(
                                                "SALE_OPEN",
                                                "CHECKOUT_ABANDON",
                                                "SOLD_OUT_SIMILAR",
                                                "CATEGORY_NEW")
                                        .contains(r.ruleCode()))))
            throw new CouponException(CouponErrorCode.INVALID);
        var n = clock.instant();
        return CampaignResponse.from(
                campaigns.save(
                        CouponCampaign.builder()
                                .id(UUID.randomUUID())
                                .code(r.code())
                                .definitionId(r.definitionId())
                                .kind(r.kind())
                                .ruleCode(r.ruleCode())
                                .totalBudget(r.totalBudget())
                                .perAccountLimit(1)
                                .startsAt(r.startsAt())
                                .endsAt(r.endsAt())
                                .status("DRAFT")
                                .demo(r.demo())
                                .createdAt(n)
                                .updatedAt(n)
                                .build()));
    }

    public CampaignResponse state(UUID id, String status) {
        var c = get(id);
        if (!Set.of("ACTIVE", "PAUSED").contains(status))
            throw new CouponException(CouponErrorCode.INVALID);
        c.status = status;
        c.updatedAt = clock.instant();
        return CampaignResponse.from(c);
    }

    public CampaignResponse budget(UUID id, int total) {
        var c = get(id);
        if (total < c.totalBudget)
            throw new CampaignException(CampaignErrorCode.CAMPAIGN_BUDGET_DECREASE);
        c.totalBudget = total;
        c.updatedAt = clock.instant();
        return CampaignResponse.from(c);
    }

    private CouponCampaign get(UUID id) {
        return campaigns
                .lock(id)
                .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }
}

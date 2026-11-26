package com.msa.coupon.domain.campaign.command.service.impl;

import com.msa.coupon.domain.campaign.command.service.CampaignService;
import com.msa.coupon.domain.campaign.dto.request.CampaignRequest;
import com.msa.coupon.domain.campaign.dto.response.CampaignResponse;
import com.msa.coupon.domain.campaign.dto.response.CampaignStatsResponse;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.domain.campaign.exception.CampaignErrorCode;
import com.msa.coupon.domain.campaign.exception.CampaignException;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.coupon.entity.CouponDefinition;
import com.msa.coupon.domain.coupon.exception.CouponErrorCode;
import com.msa.coupon.domain.coupon.exception.CouponException;
import com.msa.coupon.domain.coupon.repository.CouponDefinitionRepository;
import com.msa.coupon.domain.coupon.repository.CouponIssueHistoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 캠페인 예산 감소와 기간 오류를 막습니다. */
@Service
@Transactional(timeout = 5)
public class CampaignServiceImpl implements CampaignService {
    private final CouponIssueHistoryRepository histories;
    private final CouponCampaignRepository campaigns;
    private final CouponDefinitionRepository definitions;
    private final Clock clock;

    public CampaignServiceImpl(
            CouponIssueHistoryRepository histories,
            CouponCampaignRepository campaigns,
            CouponDefinitionRepository definitions,
            Clock clock) {
        this.histories = histories;
        this.campaigns = campaigns;
        this.definitions = definitions;
        this.clock = clock;
    }

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

    public CampaignResponse create(CampaignRequest r) {
        var kind = CouponCampaign.Kind.valueOf(r.kind());
        if (!r.startsAt().isBefore(r.endsAt())
                || r.perAccountLimit() != 1
                || (r.productId() != null && kind != CouponCampaign.Kind.CLAIM))
            throw new CouponException(CouponErrorCode.INVALID);
        var d =
                definitions
                        .findById(r.definitionId())
                        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
        if (d.status != CouponDefinition.Status.ACTIVE
                || (kind != CouponCampaign.Kind.TARGETED
                        && !(r.productId() == null ? "ALL" : "PRODUCT").equals(d.scope))
                || (kind == CouponCampaign.Kind.TARGETED
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
                                .productId(r.productId())
                                .kind(kind)
                                .ruleCode(r.ruleCode())
                                .totalBudget(r.totalBudget())
                                .perAccountLimit(1)
                                .startsAt(r.startsAt())
                                .endsAt(r.endsAt())
                                .status(CouponCampaign.Status.DRAFT)
                                .demo(Boolean.TRUE.equals(r.demo()))
                                .createdAt(n)
                                .updatedAt(n)
                                .build()));
    }

    public CampaignResponse state(UUID id, CouponCampaign.Status status) {
        var c = get(id);
        if (status != CouponCampaign.Status.ACTIVE && status != CouponCampaign.Status.PAUSED)
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

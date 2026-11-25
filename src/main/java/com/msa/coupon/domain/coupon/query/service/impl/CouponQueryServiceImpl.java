package com.msa.coupon.domain.coupon.query.service.impl;

import com.msa.coupon.domain.campaign.dto.response.CampaignResponse;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.domain.campaign.exception.CampaignErrorCode;
import com.msa.coupon.domain.campaign.exception.CampaignException;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.coupon.dto.response.ApplicableCouponResponse;
import com.msa.coupon.domain.coupon.dto.response.DefinitionResponse;
import com.msa.coupon.domain.coupon.dto.response.IssueResponse;
import com.msa.coupon.domain.coupon.dto.response.PublicCampaignResponse;
import com.msa.coupon.domain.coupon.exception.CouponErrorCode;
import com.msa.coupon.domain.coupon.exception.CouponException;
import com.msa.coupon.domain.coupon.query.service.CouponQueryService;
import com.msa.coupon.domain.coupon.repository.CouponDefinitionRepository;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 조회 응답에는 엔티티를 노출하지 않습니다. */
@Service
@Transactional(readOnly = true)
public class CouponQueryServiceImpl implements CouponQueryService {
    private final CouponCampaignRepository campaigns;
    private final CouponDefinitionRepository definitions;
    private final CouponIssueRepository issues;
    private final Clock clock;

    public CouponQueryServiceImpl(
            CouponCampaignRepository campaigns,
            CouponDefinitionRepository definitions,
            CouponIssueRepository issues,
            Clock clock) {
        this.campaigns = campaigns;
        this.definitions = definitions;
        this.issues = issues;
        this.clock = clock;
    }

    public PublicCampaignResponse campaign(String code, UUID account) {
        var c =
                campaigns
                        .findByCode(code)
                        .filter(x -> x.kind == CouponCampaign.Kind.CLAIM)
                        .orElseThrow(
                                () -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        var d =
                definitions
                        .findById(c.definitionId)
                        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
        return new PublicCampaignResponse(
                CampaignResponse.from(c),
                DefinitionResponse.from(d),
                Math.max(0, c.totalBudget - c.issuedCount),
                account == null
                        ? null
                        : issues.findByCampaignIdAndAccountIdAndSubjectKey(c.id, account, "CLAIM")
                                .isPresent());
    }

    public List<IssueResponse> mine(UUID account, String status, int page, int size) {
        if (!Set.of("AVAILABLE", "USED", "EXPIRED").contains(status)
                || page < 0
                || size < 1
                || size > 100) throw new CouponException(CouponErrorCode.INVALID);
        return issues
                .mine(
                        account,
                        status,
                        clock.instant(),
                        PageRequest.of(page, size, Sort.by("issuedAt").descending()))
                .stream()
                .map(IssueResponse::from)
                .toList();
    }

    public List<ApplicableCouponResponse> applicable(UUID account, UUID product, long items) {
        if (items < 0) throw new CouponException(CouponErrorCode.INVALID);
        return issues.applicable(account, product, items, clock.instant()).stream()
                .map(
                        i -> {
                            long discount =
                                    "AMOUNT".equals(i.discountType)
                                            ? i.amount
                                            : BigInteger.valueOf(items)
                                                    .multiply(BigInteger.valueOf(i.rateBps))
                                                    .divide(BigInteger.valueOf(10000))
                                                    .min(BigInteger.valueOf(i.maxDiscount))
                                                    .longValueExact();
                            return new ApplicableCouponResponse(
                                    IssueResponse.from(i),
                                    Math.min(discount, Math.max(0, items - 100)));
                        })
                .toList();
    }
}
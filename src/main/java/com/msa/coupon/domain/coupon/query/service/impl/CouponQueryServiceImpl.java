package com.msa.coupon.domain.coupon.query.service.impl;

import com.msa.coupon.domain.campaign.dto.request.*;
import com.msa.coupon.domain.campaign.dto.response.*;
import com.msa.coupon.domain.campaign.entity.*;
import com.msa.coupon.domain.campaign.exception.*;
import com.msa.coupon.domain.campaign.repository.*;
import com.msa.coupon.domain.coupon.dto.request.*;
import com.msa.coupon.domain.coupon.dto.response.*;
import com.msa.coupon.domain.coupon.entity.*;
import com.msa.coupon.domain.coupon.exception.*;
import com.msa.coupon.domain.coupon.query.service.CouponQueryService;
import com.msa.coupon.domain.coupon.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/** 조회 응답에는 엔티티를 노출하지 않습니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponQueryServiceImpl implements CouponQueryService {
    private final CouponCampaignRepository campaigns;
    private final CouponDefinitionRepository definitions;
    private final CouponIssueRepository issues;
    private final Clock clock;

    public PublicCampaignResponse campaign(String code, UUID account) {
        var c =
                campaigns
                        .findByCode(code)
                        .filter(x -> "CLAIM".equals(x.kind))
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
                        org.springframework.data.domain.PageRequest.of(
                                page,
                                size,
                                org.springframework.data.domain.Sort.by("issuedAt").descending()))
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
                                            : java.math.BigInteger.valueOf(items)
                                                    .multiply(
                                                            java.math.BigInteger.valueOf(i.rateBps))
                                                    .divide(java.math.BigInteger.valueOf(10000))
                                                    .min(
                                                            java.math.BigInteger.valueOf(
                                                                    i.maxDiscount))
                                                    .longValueExact();
                            return new ApplicableCouponResponse(
                                    IssueResponse.from(i),
                                    Math.min(discount, Math.max(0, items - 100)));
                        })
                .toList();
    }
}

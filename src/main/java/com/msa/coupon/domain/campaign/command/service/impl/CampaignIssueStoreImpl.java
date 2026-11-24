package com.msa.coupon.domain.campaign.command.service.impl;

import com.msa.coupon.domain.campaign.command.service.CampaignIssueStore;
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

/** 예산 증가와 유니크 발급을 한 트랜잭션에 기록합니다. */
@Service
@RequiredArgsConstructor
public class CampaignIssueStoreImpl implements CampaignIssueStore {
    private final CouponCampaignRepository campaigns;
    private final CouponDefinitionRepository definitions;
    private final CouponIssueRepository issues;
    private final CouponIssueHistoryRepository histories;
    private final Clock clock;

    @Transactional(timeout = 5)
    public ClaimResponse issue(
            String code,
            UUID account,
            String subject,
            String source,
            String sourceRef,
            UUID product,
            Instant deadline) {
        var c =
                campaigns
                        .findByCode(code)
                        .orElseThrow(
                                () -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        var existing = issues.findByCampaignIdAndAccountIdAndSubjectKey(c.id, account, subject);
        if (existing.isPresent())
            return new ClaimResponse(IssueResponse.from(existing.get()), false);
        if (("CLAIM".equals(source) && !"CLAIM".equals(c.kind))
                || ("TARGET".equals(source) && !"TARGETED".equals(c.kind))
                || ("MEMBER".equals(source) && !"MEMBER".equals(c.kind)))
            throw new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_ACTIVE);
        var now = clock.instant();
        if (!"ACTIVE".equals(c.status) || now.isBefore(c.startsAt) || !now.isBefore(c.endsAt))
            throw new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_ACTIVE);
        if (campaigns.reserve(c.id, now) == 0) {
            // 마지막 예산을 같은 사용자의 경합 요청이 먼저 썼다면 기존 발급을 돌려줍니다.
            var duplicate =
                    issues.findByCampaignIdAndAccountIdAndSubjectKey(c.id, account, subject);
            if (duplicate.isPresent())
                return new ClaimResponse(IssueResponse.from(duplicate.get()), false);
            throw new CampaignException(
                    "ACTIVE".equals(campaigns.currentStatus(c.id))
                            ? CampaignErrorCode.CAMPAIGN_SOLD_OUT
                            : CampaignErrorCode.CAMPAIGN_NOT_ACTIVE);
        }
        var d =
                definitions
                        .findById(c.definitionId)
                        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
        if (!"ACTIVE".equals(d.status))
            throw new CouponException(CouponErrorCode.COUPON_NOT_AVAILABLE);
        var from = "FIXED".equals(d.validityMode) ? d.validFrom : now;
        var until =
                "FIXED".equals(d.validityMode)
                        ? d.validUntil
                        : now.plusSeconds(Math.multiplyExact(d.validMinutes, 60L));
        if (deadline != null && deadline.isBefore(until)) until = deadline;
        if (!from.isBefore(until)
                || !now.isBefore(until)
                || ("PRODUCT".equals(d.scope) && product == null))
            throw new CouponException(CouponErrorCode.INVALID);
        var i =
                issues.saveAndFlush(
                        CouponIssue.builder()
                                .id(UUID.randomUUID())
                                .campaignId(c.id)
                                .definitionId(d.id)
                                .definitionVersion(d.version)
                                .accountId(account)
                                .subjectKey(subject)
                                .productId(product)
                                .name(d.name)
                                .discountType(d.discountType)
                                .amount(d.amount)
                                .rateBps(d.rateBps)
                                .maxDiscount(d.maxDiscount)
                                .minItemsAmount(d.minItemsAmount)
                                .scope(d.scope)
                                .status("ISSUED")
                                .validFrom(from)
                                .validUntil(until)
                                .source(source)
                                .sourceRef(sourceRef)
                                .issuedAt(now)
                                .noticeLeadMinutes(d.noticeLeadMinutes)
                                .build());
        histories.save(
                CouponIssueHistory.builder()
                        .id(UUID.randomUUID())
                        .issueId(i.id)
                        .campaignId(c.id)
                        .accountId(account)
                        .action("ISSUED")
                        .actor("CLAIM".equals(source) ? "USER" : "SYSTEM")
                        .occurredAt(now)
                        .build());
        return new ClaimResponse(IssueResponse.from(i), true);
    }
}

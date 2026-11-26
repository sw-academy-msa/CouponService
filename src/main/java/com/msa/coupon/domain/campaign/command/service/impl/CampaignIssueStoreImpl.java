package com.msa.coupon.domain.campaign.command.service.impl;

import com.msa.coupon.domain.campaign.command.service.CampaignIssueStore;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.domain.campaign.exception.CampaignErrorCode;
import com.msa.coupon.domain.campaign.exception.CampaignException;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.coupon.dto.response.ClaimResponse;
import com.msa.coupon.domain.coupon.dto.response.IssueResponse;
import com.msa.coupon.domain.coupon.entity.CouponDefinition;
import com.msa.coupon.domain.coupon.entity.CouponIssue;
import com.msa.coupon.domain.coupon.entity.CouponIssueHistory;
import com.msa.coupon.domain.coupon.exception.CouponErrorCode;
import com.msa.coupon.domain.coupon.exception.CouponException;
import com.msa.coupon.domain.coupon.repository.CouponDefinitionRepository;
import com.msa.coupon.domain.coupon.repository.CouponIssueHistoryRepository;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** 예산 증가와 유니크 발급을 한 트랜잭션에 기록합니다. */
@Service
public class CampaignIssueStoreImpl implements CampaignIssueStore {
    private final CouponCampaignRepository campaigns;
    private final CouponDefinitionRepository definitions;
    private final CouponIssueRepository issues;
    private final CouponIssueHistoryRepository histories;
    private final Clock clock;

    public CampaignIssueStoreImpl(
            CouponCampaignRepository campaigns,
            CouponDefinitionRepository definitions,
            CouponIssueRepository issues,
            CouponIssueHistoryRepository histories,
            Clock clock) {
        this.campaigns = campaigns;
        this.definitions = definitions;
        this.issues = issues;
        this.histories = histories;
        this.clock = clock;
    }

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
        if (("CLAIM".equals(source) && c.kind != CouponCampaign.Kind.CLAIM)
                || ("TARGET".equals(source) && c.kind != CouponCampaign.Kind.TARGETED)
                || ("MEMBER".equals(source) && c.kind != CouponCampaign.Kind.MEMBER))
            throw new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_ACTIVE);
        // 받기형의 적용 상품은 요청값 대신 캠페인에 고정된 상품을 사용합니다.
        if ("CLAIM".equals(source)) product = c.productId;
        var now = clock.instant();
        if (c.status != CouponCampaign.Status.ACTIVE
                || now.isBefore(c.startsAt)
                || !now.isBefore(c.endsAt))
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
        if (d.status != CouponDefinition.Status.ACTIVE)
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
                                .status(CouponIssue.Status.ISSUED)
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

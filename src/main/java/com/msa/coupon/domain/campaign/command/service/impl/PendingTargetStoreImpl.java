package com.msa.coupon.domain.campaign.command.service.impl;

import com.msa.coupon.domain.campaign.command.service.*;
import com.msa.coupon.domain.campaign.dto.request.CampaignTarget;
import com.msa.coupon.domain.campaign.dto.response.TargetWork;
import com.msa.coupon.domain.campaign.entity.CouponPendingTarget;
import com.msa.coupon.domain.campaign.repository.*;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;
import com.msa.coupon.domain.outbox.service.CouponNoticeService;
import com.msa.coupon.domain.receipt.InvalidCouponEventException;
import com.msa.coupon.domain.receipt.service.*;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.*;

/** 임대와 예산 및 Outbox를 같은 짧은 원본 트랜잭션으로 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional(timeout = 5)
public class PendingTargetStoreImpl implements PendingTargetStore {
    public Optional<TargetWork> claim(UUID eventId) {
        var found = pending.lock(eventId);
        if (found.isEmpty()) return Optional.empty();
        var row = found.get();
        var now = clock.instant();
        if (!"PENDING".equals(row.status)
                || row.nextAttemptAt.isAfter(now)
                || (row.leaseUntil != null && row.leaseUntil.isAfter(now))) return Optional.empty();
        row.leaseToken = UUID.randomUUID();
        row.leaseUntil = now.plusSeconds(120);
        return Optional.of(
                new TargetWork(
                        row.eventId,
                        row.leaseToken,
                        mapper.readValue(row.payload, CampaignTarget.class)));
    }

    private final CouponPendingTargetRepository pending;
    private final CouponCampaignRepository campaigns;
    private final CouponIssueRepository issues;
    private final CouponReceiptService receipts;
    private final CampaignIssueStore issuer;
    private final CouponNoticeService notices;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Value("${CAMPAIGN_DEMO_ACCOUNT_IDS:}")
    private String demoAccounts;

    public void enqueue(CampaignTarget event, String payload) {
        if (!receipts.accept(
                event.eventId(), "stockdrop.ai.campaign-targets.v1", EventHash.of(payload))) return;
        pending.save(
                CouponPendingTarget.builder()
                        .eventId(event.eventId())
                        .payload(payload)
                        .nextAttemptAt(clock.instant())
                        .status("PENDING")
                        .createdAt(clock.instant())
                        .build());
    }

    public List<TargetWork> claim() {
        var now = clock.instant();
        return pending.due(now).stream()
                .map(
                        row -> {
                            row.leaseToken = UUID.randomUUID();
                            row.leaseUntil = now.plusSeconds(120);
                            return new TargetWork(
                                    row.eventId,
                                    row.leaseToken,
                                    mapper.readValue(row.payload, CampaignTarget.class));
                        })
                .toList();
    }

    public void complete(TargetWork work, Boolean eligible) {
        var row = pending.lock(work.eventId()).orElseThrow();
        var now = clock.instant();
        if (!"PENDING".equals(row.status)
                || !Objects.equals(work.token(), row.leaseToken)
                || row.leaseUntil == null
                || !row.leaseUntil.isAfter(now)) return;
        row.leaseToken = null;
        row.leaseUntil = null;
        row.attempts++;
        if (!now.isBefore(row.createdAt.plusSeconds(1800))) {
            row.status = "FAILED";
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("coupon target exhausted eventId={}", row.eventId);
            return;
        }
        if (eligible == null) {
            row.nextAttemptAt = now.plusSeconds(Math.min(600, 30L << Math.min(4, row.attempts)));
            return;
        }
        var event = work.target();
        row.status = "DONE";
        if (!eligible
                || !now.isBefore(event.deadline())
                || (event.demo() && !allowed(event.accountId()))) return;
        var found = campaigns.findByCode(event.campaignCode());
        if (found.isEmpty()) return;
        var campaign = campaigns.lock(found.get().id).orElseThrow();
        if (!"TARGETED".equals(campaign.kind)
                || !"ACTIVE".equals(campaign.status)
                || !Objects.equals(event.ruleCode(), campaign.ruleCode)
                || event.demo() != campaign.demo
                || now.isBefore(campaign.startsAt)
                || !now.isBefore(campaign.endsAt)) return;
        if (issues.findByCampaignIdAndAccountIdAndSubjectKey(
                        campaign.id, event.accountId(), event.subjectKey())
                .isPresent()) return;
        String suffix =
                switch (event.ruleCode()) {
                    case "SALE_OPEN" -> "SALE_OPEN";
                    case "CHECKOUT_ABANDON" -> "CHECKOUT_REMINDER";
                    case "SOLD_OUT_SIMILAR" -> "SIMILAR_DROP";
                    case "CATEGORY_NEW" -> "CATEGORY_NEW";
                    default -> throw new InvalidCouponEventException();
                };
        if (campaign.issuedCount >= campaign.totalBudget) {
            notices.append(
                    "CAMPAIGN",
                    "CAMPAIGN_" + suffix,
                    event.eventId(),
                    1,
                    event.accountId(),
                    event.productId(),
                    null,
                    event.deadline());
            return;
        }
        var result =
                issuer.issue(
                        campaign.code,
                        event.accountId(),
                        event.subjectKey(),
                        "TARGET",
                        event.eventId().toString(),
                        event.productId(),
                        event.deadline());
        if (result.created()) {
            var issue = issues.findById(result.coupon().issueId()).orElseThrow();
            notices.append(
                    "COUPON",
                    "COUPON_" + suffix,
                    issue.id,
                    issue.version,
                    issue.accountId,
                    event.productId(),
                    issue.id,
                    issue.validUntil);
        }
    }

    private boolean allowed(UUID account) {
        try {
            var ids =
                    Arrays.stream(demoAccounts.split(","))
                            .map(String::trim)
                            .map(UUID::fromString)
                            .toList();
            return ids.size() <= 10 && ids.contains(account);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}

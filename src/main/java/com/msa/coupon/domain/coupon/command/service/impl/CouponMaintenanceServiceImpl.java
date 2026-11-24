package com.msa.coupon.domain.coupon.command.service.impl;

import com.msa.coupon.domain.coupon.command.service.CouponMaintenanceService;
import com.msa.coupon.domain.coupon.entity.CouponIssue;
import com.msa.coupon.domain.coupon.entity.CouponIssueHistory;
import com.msa.coupon.domain.coupon.exception.CouponErrorCode;
import com.msa.coupon.domain.coupon.exception.CouponException;
import com.msa.coupon.domain.coupon.repository.CouponIssueHistoryRepository;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;
import com.msa.coupon.domain.outbox.service.CouponNoticeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 행 잠금을 잡은 짧은 트랜잭션에서 상태와 이력을 함께 바꿉니다. */
@Service
@Transactional(timeout = 5)
public class CouponMaintenanceServiceImpl implements CouponMaintenanceService {
    private final CouponIssueRepository issues;
    private final CouponIssueHistoryRepository histories;
    private final CouponNoticeService notices;
    private final Clock clock;

    public CouponMaintenanceServiceImpl(
            CouponIssueRepository issues,
            CouponIssueHistoryRepository histories,
            CouponNoticeService notices,
            Clock clock) {
        this.issues = issues;
        this.histories = histories;
        this.notices = notices;
        this.clock = clock;
    }

    public void expire() {
        for (var issue : issues.expired(clock.instant())) {
            issue.status = CouponIssue.Status.EXPIRED;
            issue.version++;
            history(issue, "EXPIRED", "SYSTEM");
        }
    }

    public void notifyExpiring() {
        for (var issue : issues.expiring(clock.instant())) {
            issue.expiringNoticedAt = clock.instant();
            issue.version++;
            notices.append(
                    "COUPON",
                    "COUPON_EXPIRING",
                    issue.id,
                    issue.version,
                    issue.accountId,
                    null,
                    issue.id,
                    issue.validUntil);
            history(issue, "NOTICE", "SYSTEM");
        }
    }

    public void revoke(UUID id) {
        var issue =
                issues.lock(id)
                        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
        if (issue.status != CouponIssue.Status.ISSUED) {
            throw new CouponException(CouponErrorCode.COUPON_NOT_AVAILABLE);
        }
        issue.status = CouponIssue.Status.REVOKED;
        issue.version++;
        history(issue, "REVOKED", "ADMIN");
    }

    private void history(CouponIssue issue, String action, String actor) {
        histories.save(
                CouponIssueHistory.builder()
                        .id(UUID.randomUUID())
                        .issueId(issue.id)
                        .campaignId(issue.campaignId)
                        .accountId(issue.accountId)
                        .action(action)
                        .actor(actor)
                        .occurredAt(clock.instant())
                        .build());
    }
}
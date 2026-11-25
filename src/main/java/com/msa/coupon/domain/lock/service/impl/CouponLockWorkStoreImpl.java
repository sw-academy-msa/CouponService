package com.msa.coupon.domain.lock.service.impl;

import com.msa.coupon.domain.coupon.entity.CouponIssue;
import com.msa.coupon.domain.coupon.entity.CouponIssueHistory;
import com.msa.coupon.domain.coupon.exception.CouponErrorCode;
import com.msa.coupon.domain.coupon.exception.CouponException;
import com.msa.coupon.domain.coupon.repository.CouponIssueHistoryRepository;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;
import com.msa.coupon.domain.lock.dto.request.LockRequest;
import com.msa.coupon.domain.lock.dto.response.LockResponse;
import com.msa.coupon.domain.lock.dto.response.LockWork;
import com.msa.coupon.domain.lock.dto.response.OrderOutcome;
import com.msa.coupon.domain.lock.entity.CouponLock;
import com.msa.coupon.domain.lock.exception.LockErrorCode;
import com.msa.coupon.domain.lock.exception.LockException;
import com.msa.coupon.domain.lock.repository.CouponLockRepository;
import com.msa.coupon.domain.lock.service.CouponLockWorkStore;
import com.msa.coupon.domain.receipt.service.EventHash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 잠금 순서를 주문 잠금 다음 발급 원본으로 고정하고 불명 상태를 보존합니다. */
@Service
@Transactional(timeout = 5)
public class CouponLockWorkStoreImpl implements CouponLockWorkStore {
    private static final Logger log = LoggerFactory.getLogger(CouponLockWorkStoreImpl.class);

    private final long recoveryGraceMinutes;
    private final CouponLockRepository locks;
    private final CouponIssueRepository issues;
    private final CouponIssueHistoryRepository histories;
    private final Clock clock;

    public CouponLockWorkStoreImpl(
            @Value("${coupon.lock-recovery.grace-minutes:10}") long recoveryGraceMinutes,
            CouponLockRepository locks,
            CouponIssueRepository issues,
            CouponIssueHistoryRepository histories,
            Clock clock) {
        this.recoveryGraceMinutes = recoveryGraceMinutes;
        this.locks = locks;
        this.issues = issues;
        this.histories = histories;
        this.clock = clock;
    }

    public LockResponse lock(LockRequest r) {
        String hash =
                EventHash.of(
                        r.orderId()
                                + ":"
                                + r.couponIssueId()
                                + ":"
                                + r.accountId()
                                + ":"
                                + r.productId()
                                + ":"
                                + r.sellerId()
                                + ":"
                                + r.itemsAmount()
                                + ":"
                                + r.holdUntil());
        var existing = locks.lock(r.orderId());
        if (existing.isPresent()) {
            var l = existing.get();
            if (!hash.equals(l.requestHash))
                throw new LockException(LockErrorCode.COUPON_LOCK_CONFLICT);
            return LockResponse.from(
                    l,
                    issues.findById(l.issueId)
                            .orElseThrow(
                                    () -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND)));
        }
        if (r.accountId().equals(r.sellerId()))
            throw new CouponException(CouponErrorCode.COUPON_SELF_PRODUCT);
        var now = clock.instant();
        if (!r.holdUntil().isAfter(now)) throw new CouponException(CouponErrorCode.INVALID);
        if (issues.acquire(r.couponIssueId(), r.accountId(), r.productId(), r.itemsAmount(), now)
                == 0) {
            var i =
                    issues.findById(r.couponIssueId())
                            .orElseThrow(
                                    () -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
            if (!r.accountId().equals(i.accountId))
                throw new CouponException(CouponErrorCode.COUPON_NOT_OWNED);
            if (!i.validUntil.isAfter(now))
                throw new CouponException(CouponErrorCode.COUPON_EXPIRED);
            if (i.status != CouponIssue.Status.ISSUED || i.validFrom.isAfter(now))
                throw new CouponException(CouponErrorCode.COUPON_NOT_AVAILABLE);
            if ("PRODUCT".equals(i.scope) && !r.productId().equals(i.productId))
                throw new CouponException(CouponErrorCode.COUPON_SCOPE_MISMATCH);
            throw new CouponException(CouponErrorCode.COUPON_MIN_AMOUNT);
        }
        var i = issues.findById(r.couponIssueId()).orElseThrow();
        var l =
                locks.saveAndFlush(
                        CouponLock.builder()
                                .orderId(r.orderId())
                                .issueId(i.id)
                                .accountId(r.accountId())
                                .productId(r.productId())
                                .itemsAmount(r.itemsAmount())
                                .requestHash(hash)
                                .state(CouponLock.State.LOCKED)
                                .holdUntil(r.holdUntil())
                                .nextCheckAt(
                                        r.holdUntil()
                                                .plusSeconds(
                                                        Math.multiplyExact(
                                                                recoveryGraceMinutes, 60)))
                                .lockedAt(now)
                                .build());
        history(i, "LOCKED", l.orderId, null);
        return LockResponse.from(l, i);
    }

    public List<LockWork> claim() {
        var now = clock.instant();
        return locks.due(now).stream()
                .map(
                        l -> {
                            l.leaseToken = UUID.randomUUID();
                            l.leaseUntil = now.plusSeconds(120);
                            return new LockWork(l.orderId, l.leaseToken, l.lockedAt);
                        })
                .toList();
    }

    public void complete(LockWork w, OrderOutcome o) {
        var found = locks.lock(w.orderId());
        if (found.isEmpty()) return;
        var l = found.get();
        var now = clock.instant();
        if (l.state != CouponLock.State.LOCKED
                || !Objects.equals(l.leaseToken, w.token())
                || l.leaseUntil == null
                || !l.leaseUntil.isAfter(now)) return;
        l.leaseToken = null;
        l.leaseUntil = null;
        l.checkAttempts++;
        if (o != null && w.orderId().equals(o.orderId())) {
            if (o.notFound()) {
                if (!now.isBefore(l.lockedAt.plusSeconds(86400))) {
                    finish(l, false, 0, "ORDER_NOT_FOUND");
                    return;
                }
            } else if ("CONFIRMED".equals(o.phase()) && o.couponApplied() != null) {
                finish(
                        l,
                        o.couponApplied(),
                        o.couponDiscount() == null ? 0 : o.couponDiscount(),
                        "ORDER_CONFIRMED");
                return;
            } else if (Set.of("FAILED", "EXPIRED").contains(o.phase())) {
                finish(l, false, 0, o.phase());
                return;
            }
        }
        l.nextCheckAt =
                now.plusSeconds(
                        o == null ? Math.min(600, 30L << Math.min(4, l.checkAttempts)) : 300);
        if (!now.isBefore(l.lockedAt.plusSeconds(86400)))
            log.warn("coupon lock overdue orderId={}", l.orderId);
    }

    public void lifecycle(UUID orderId, String phase, boolean applied, long discount) {
        var found = locks.lock(orderId);
        if (found.isEmpty()) return;
        var l = found.get();
        if ("CONFIRMED".equals(phase)) {
            if (l.state == CouponLock.State.RELEASED && applied) {
                var i = issues.lock(l.issueId).orElseThrow();
                i.status = CouponIssue.Status.USED;
                i.usedOrderId = l.orderId;
                i.usedAt = clock.instant();
                i.discountApplied = discount;
                i.version++;
                l.state = CouponLock.State.USED;
                l.finishedAt = clock.instant();
                l.discountApplied = discount;
                l.finishReason = "MANUAL_REVIEW_LATE_CONFIRM";
                history(i, "USED", orderId, l.finishReason);
                log.error("coupon late confirmation orderId={} issueId={}", orderId, i.id);
                return;
            }
            if (l.state == CouponLock.State.LOCKED) finish(l, applied, discount, "CONFIRMED");
        } else if (Set.of("FAILED", "EXPIRED").contains(phase)
                && l.state == CouponLock.State.LOCKED)
            finish(l, false, 0, phase);
    }

    private void finish(CouponLock l, boolean used, long discount, String reason) {
        var i = issues.lock(l.issueId).orElseThrow();
        var now = clock.instant();
        l.state = used ? CouponLock.State.USED : CouponLock.State.RELEASED;
        l.finishedAt = now;
        l.finishReason = reason;
        l.discountApplied = used ? discount : 0L;
        l.leaseToken = null;
        l.leaseUntil = null;
        // 지연 확정으로 사용 처리된 원본을 다른 주문의 해제가 되돌리지 않게 합니다.
        if (i.status == CouponIssue.Status.USED && !l.orderId.equals(i.usedOrderId)) {
            l.finishReason = "MANUAL_REVIEW_USED_CONFLICT";
            log.error("coupon used conflict orderId={} issueId={}", l.orderId, i.id);
            return;
        }
        i.status =
                used
                        ? CouponIssue.Status.USED
                        : i.validUntil.isAfter(now)
                                ? CouponIssue.Status.ISSUED
                                : CouponIssue.Status.EXPIRED;
        i.version++;
        if (used) {
            i.usedOrderId = l.orderId;
            i.usedAt = now;
            i.discountApplied = discount;
        }
        history(i, used ? "USED" : "RELEASED", l.orderId, reason);
    }

    private void history(CouponIssue i, String action, UUID order, String reason) {
        histories.save(
                CouponIssueHistory.builder()
                        .id(UUID.randomUUID())
                        .issueId(i.id)
                        .campaignId(i.campaignId)
                        .accountId(i.accountId)
                        .action(action)
                        .orderId(order)
                        .actor("ORDER")
                        .reason(reason)
                        .occurredAt(clock.instant())
                        .build());
    }
}
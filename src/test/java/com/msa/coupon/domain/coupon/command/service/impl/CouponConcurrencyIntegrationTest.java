package com.msa.coupon.domain.coupon.command.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.coupon.domain.campaign.command.service.CampaignService;
import com.msa.coupon.domain.campaign.command.service.PendingTargetStore;
import com.msa.coupon.domain.campaign.consumer.MembershipEventConsumer;
import com.msa.coupon.domain.campaign.dto.request.CampaignRequest;
import com.msa.coupon.domain.campaign.dto.request.CampaignTarget;
import com.msa.coupon.domain.campaign.dto.request.MembershipEvent;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.domain.campaign.entity.CouponPendingTarget;
import com.msa.coupon.domain.campaign.exception.CampaignException;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.campaign.repository.CouponPendingTargetRepository;
import com.msa.coupon.domain.coupon.command.service.CouponClaimService;
import com.msa.coupon.domain.coupon.command.service.CouponDefinitionService;
import com.msa.coupon.domain.coupon.command.service.CouponMaintenanceService;
import com.msa.coupon.domain.coupon.dto.request.DefinitionRequest;
import com.msa.coupon.domain.coupon.entity.CouponIssue;
import com.msa.coupon.domain.coupon.exception.CouponException;
import com.msa.coupon.domain.coupon.query.service.CouponQueryService;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;
import com.msa.coupon.domain.lock.dto.request.LockRequest;
import com.msa.coupon.domain.lock.dto.response.OrderOutcome;
import com.msa.coupon.domain.lock.exception.LockException;
import com.msa.coupon.domain.lock.repository.CouponLockRepository;
import com.msa.coupon.domain.lock.service.CouponLockService;
import com.msa.coupon.domain.lock.service.CouponLockWorkStore;
import com.msa.coupon.domain.outbox.repository.OutboxEventRepository;
import com.msa.coupon.domain.receipt.InvalidCouponEventException;
import com.msa.coupon.domain.receipt.repository.CouponEventReceiptRepository;
import com.msa.coupon.global.config.database.CouponDatabaseTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/** 예산과 잠금 경합을 실제 커밋으로 실행하여 초과 발급을 검출합니다. */
@SpringBootTest(
        properties = {
            "coupon.consumer.enabled=true",
            "coupon.api.enabled=true",
            "spring.kafka.listener.auto-startup=false"
        })
@ActiveProfiles("test")
@Import(CouponDatabaseTestSupport.Time.class)
class CouponConcurrencyIntegrationTest extends CouponDatabaseTestSupport {
    @Test
    void productClaimIsBoundToCampaignAndCannotLockAnotherProduct() {
        var now = clock.instant();
        var product = UUID.randomUUID();
        var account = UUID.randomUUID();
        var definition =
                definitions.create(
                        new DefinitionRequest(
                                "상품 쿠폰",
                                "AMOUNT",
                                1000L,
                                null,
                                null,
                                10000L,
                                "PRODUCT",
                                "RELATIVE",
                                null,
                                null,
                                60,
                                10),
                        UUID.randomUUID());
        var campaign =
                campaigns.create(
                        new CampaignRequest(
                                "P" + UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                                definition.id(),
                                "CLAIM",
                                null,
                                10,
                                1,
                                now.minusSeconds(1),
                                now.plusSeconds(3600),
                                false,
                                product));
        campaigns.state(campaign.id(), CouponCampaign.Status.ACTIVE);
        assertThat(queries.campaign(campaign.code(), account).campaign().productId())
                .isEqualTo(product);
        var claimed = claims.claim(campaign.code(), account);
        assertThat(claimed.coupon().productId()).isEqualTo(product);
        assertThat(claimed.coupon().scope()).isEqualTo("PRODUCT");
        assertThat(claims.claim(campaign.code(), account).created()).isFalse();
        assertThat(queries.applicable(account, UUID.randomUUID(), 20000)).isEmpty();
        assertThat(queries.applicable(account, product, 20000)).hasSize(1);
        assertThatThrownBy(
                        () ->
                                lockService.lock(
                                        new LockRequest(
                                                UUID.randomUUID(),
                                                claimed.coupon().issueId(),
                                                account,
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                20000,
                                                now.plusSeconds(300))))
                .isInstanceOf(CouponException.class)
                .hasMessageContaining("상품");
        assertThat(
                        lockService.lock(
                                new LockRequest(
                                        UUID.randomUUID(),
                                        claimed.coupon().issueId(),
                                        account,
                                        product,
                                        UUID.randomUUID(),
                                        20000,
                                        now.plusSeconds(300))))
                .isNotNull();
        assertThatThrownBy(
                        () ->
                                campaigns.create(
                                        new CampaignRequest(
                                                "WRONG",
                                                definition.id(),
                                                "CLAIM",
                                                null,
                                                10,
                                                1,
                                                now,
                                                now.plusSeconds(3600),
                                                false)))
                .isInstanceOf(CouponException.class);
    }

    @Test
    void concurrentSameOrderReturnsOneLock() throws Exception {
        UUID account = UUID.randomUUID();
        UUID issue = claims.claim(campaign(2), account).coupon().issueId();
        var request =
                new LockRequest(
                        UUID.randomUUID(),
                        issue,
                        account,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        20000,
                        clock.instant().plusSeconds(300));
        var results = concurrent(10, n -> lockService.lock(request));
        assertThat(new HashSet<>(results)).hasSize(1);
    }

    @Test
    void staleLeaseCannotCompleteAndMissingOrderWaitsTwentyFourHours() {
        UUID account = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        UUID issue = claims.claim(campaign(2), account).coupon().issueId();
        lockService.lock(
                new LockRequest(
                        order,
                        issue,
                        account,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        20000,
                        clock.instant().plusSeconds(300)));
        var lockedAt = clock.instant();
        clock.set(lockedAt.plusSeconds(901));
        var first =
                lockStore.claim().stream()
                        .filter(w -> w.orderId().equals(order))
                        .findFirst()
                        .orElseThrow();
        clock.set(clock.instant().plusSeconds(121));
        var second =
                lockStore.claim().stream()
                        .filter(w -> w.orderId().equals(order))
                        .findFirst()
                        .orElseThrow();
        lockStore.complete(first, new OrderOutcome(order, "CONFIRMED", true, 1000L, false));
        assertThat(issues.findById(issue).orElseThrow().status)
                .isEqualTo(CouponIssue.Status.LOCKED);
        lockStore.complete(second, new OrderOutcome(order, null, null, null, true));
        assertThat(issues.findById(issue).orElseThrow().status)
                .isEqualTo(CouponIssue.Status.LOCKED);
        clock.set(lockedAt.plusSeconds(86401));
        var finalWork =
                lockStore.claim().stream()
                        .filter(w -> w.orderId().equals(order))
                        .findFirst()
                        .orElseThrow();
        lockStore.complete(finalWork, new OrderOutcome(order, null, null, null, true));
        assertThat(locks.findById(order).orElseThrow().finishReason).isEqualTo("ORDER_NOT_FOUND");
        assertThat(issues.findById(issue).orElseThrow().status)
                .isEqualTo(CouponIssue.Status.EXPIRED);
    }

    @Test
    void targetBudgetExhaustionProducesCouponlessNotice() {
        var now = clock.instant();
        var definition =
                definitions.create(
                        new DefinitionRequest(
                                "맞춤 쿠폰",
                                "RATE",
                                null,
                                1000,
                                3000L,
                                10000L,
                                "PRODUCT",
                                "RELATIVE",
                                null,
                                null,
                                4320,
                                1440),
                        UUID.randomUUID());
        var campaign =
                campaigns.create(
                        new CampaignRequest(
                                "T" + UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                                definition.id(),
                                "TARGETED",
                                "SALE_OPEN",
                                1,
                                1,
                                now.minusSeconds(1),
                                now.plusSeconds(3600),
                                false));
        campaigns.state(campaign.id(), CouponCampaign.Status.ACTIVE);
        for (int n = 0; n < 2; n++) {
            UUID product = UUID.randomUUID();
            var event =
                    new CampaignTarget(
                            UUID.randomUUID(),
                            1,
                            "CAMPAIGN_TARGET",
                            "SALE_OPEN",
                            campaign.code(),
                            UUID.randomUUID(),
                            product,
                            product.toString(),
                            false,
                            now.plusSeconds(600),
                            now);
            targets.enqueue(event, mapper.writeValueAsString(event));
            var work =
                    targets.claim().stream()
                            .filter(w -> w.eventId().equals(event.eventId()))
                            .findFirst()
                            .orElseThrow();
            targets.complete(work, true);
            if (n == 1) {
                assertThat(issues.findBySourceAndSourceRef("TARGET", event.eventId().toString()))
                        .isEmpty();
                assertThat(outbox.findAll())
                        .anyMatch(
                                e ->
                                        e.partitionKey.equals(event.accountId().toString())
                                                && e.payload.contains("CAMPAIGN_SALE_OPEN"));
            }
        }
        assertThat(campaigns.stats(campaign.id()).issued()).isEqualTo(1);
    }

    @Autowired PendingTargetStore targets;
    @Autowired CouponPendingTargetRepository pending;
    @Autowired OutboxEventRepository outbox;
    @Autowired MembershipEventConsumer membership;
    @Autowired ObjectMapper mapper;
    @Autowired CouponEventReceiptRepository receipts;

    @ParameterizedTest
    @CsvSource({
        "MEMBERSHIP_CANCELED,0",
        "MEMBERSHIP_WITHDRAWN,0",
        "MEMBERSHIP_RENEWAL_PENDING,2",
        "MEMBERSHIP_PAYMENT_FAILED,1",
        "MEMBERSHIP_EXPIRED,1"
    })
    void nonIssuingMembershipResultsOnlyCommitAnIdempotentReceipt(String type, long cycle) {
        long issuedBefore = issues.count();
        long outboxBefore = outbox.count();
        var event =
                new MembershipEvent(
                        UUID.randomUUID(),
                        1,
                        type,
                        "PAYMENT",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        1,
                        cycle,
                        clock.instant().plusSeconds(1200),
                        clock.instant());
        byte[] body = mapper.writeValueAsBytes(event);
        membership.receive(body);
        membership.receive(body);
        assertThat(receipts.findById(event.eventId())).isPresent();
        assertThat(issues.count()).isEqualTo(issuedBefore);
        assertThat(outbox.count()).isEqualTo(outboxBefore);
        assertThatThrownBy(
                        () ->
                                membership.receive(
                                        (mapper.writeValueAsString(event) + " ")
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidCouponEventException.class);
        assertThat(receipts.findById(event.eventId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBERSHIP_STARTED", "MEMBERSHIP_RENEWED"})
    void issuingMembershipResultsRejectCycleZeroWithoutReceipt(String type) {
        var event =
                new MembershipEvent(
                        UUID.randomUUID(),
                        1,
                        type,
                        "PAYMENT",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        1,
                        0,
                        clock.instant().plusSeconds(1200),
                        clock.instant());
        assertThatThrownBy(() -> membership.receive(mapper.writeValueAsBytes(event)))
                .isInstanceOf(InvalidCouponEventException.class);
        assertThat(receipts.findById(event.eventId())).isEmpty();
    }

    @Test
    void pendingConsentFailureRetainsWorkAndRejectsPayloadConflict() {
        var event =
                new CampaignTarget(
                        UUID.randomUUID(),
                        1,
                        "CAMPAIGN_TARGET",
                        "SALE_OPEN",
                        "MISSING",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "subject",
                        false,
                        clock.instant().plusSeconds(600),
                        clock.instant());
        String body = mapper.writeValueAsString(event);
        targets.enqueue(event, body);
        targets.enqueue(event, body);
        assertThatThrownBy(() -> targets.enqueue(event, body + " "))
                .isInstanceOf(InvalidCouponEventException.class);
        var work =
                targets.claim().stream()
                        .filter(w -> w.eventId().equals(event.eventId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(targets.claim()).noneMatch(w -> w.eventId().equals(event.eventId()));
        targets.complete(work, null);
        assertThat(pending.findById(event.eventId()).orElseThrow().status)
                .isEqualTo(CouponPendingTarget.Status.PENDING);
        clock.set(clock.instant().plusSeconds(601));
        var retry =
                targets.claim().stream()
                        .filter(w -> w.eventId().equals(event.eventId()))
                        .findFirst()
                        .orElseThrow();
        targets.complete(work, true);
        assertThat(pending.findById(event.eventId()).orElseThrow().leaseToken)
                .isEqualTo(retry.token());
        targets.complete(retry, false);
        assertThat(pending.findById(event.eventId()).orElseThrow().status)
                .isEqualTo(CouponPendingTarget.Status.DONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBERSHIP_STARTED", "MEMBERSHIP_RENEWED"})
    void membershipCycleIssuesOnceAndWritesNoticeInSameTransaction(String type) {
        var now = clock.instant();
        var definition =
                definitions.create(
                        new DefinitionRequest(
                                "회원 쿠폰",
                                "AMOUNT",
                                3000L,
                                null,
                                null,
                                20000L,
                                "ALL",
                                "RELATIVE",
                                null,
                                null,
                                43200,
                                1440),
                        UUID.randomUUID());
        var campaign =
                campaigns.create(
                        new CampaignRequest(
                                "M" + UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                                definition.id(),
                                "MEMBER",
                                null,
                                100,
                                1,
                                now.minusSeconds(1),
                                now.plusSeconds(3600),
                                false));
        campaigns.state(campaign.id(), CouponCampaign.Status.ACTIVE);
        UUID subscription = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        var event =
                new MembershipEvent(
                        UUID.randomUUID(),
                        1,
                        type,
                        "PAYMENT",
                        account,
                        subscription,
                        1,
                        1,
                        1,
                        now.plusSeconds(1200),
                        now);
        byte[] body = mapper.writeValueAsBytes(event);
        membership.receive(body);
        membership.receive(body);
        var issue = issues.findBySourceAndSourceRef("MEMBER", subscription + ":1:1").orElseThrow();
        assertThat(issue.validUntil).isEqualTo(event.entitlementUntil());
        assertThat(campaignRows.findById(campaign.id()).orElseThrow().issuedCount).isEqualTo(1);
        assertThat(outbox.findAll())
                .anyMatch(
                        e ->
                                e.payload.contains("COUPON_MEMBER_ARRIVED")
                                        && e.partitionKey.equals(account.toString()));
        campaigns.state(campaign.id(), CouponCampaign.Status.PAUSED);
    }

    @Test
    void expiryPreservesLockedIssueAndNoticeIsEmittedOnce() {
        UUID account = UUID.randomUUID();
        UUID issue = claims.claim(campaign(3), account).coupon().issueId();
        clock.set(clock.instant().plusSeconds(3001));
        maintenance.notifyExpiring();
        maintenance.notifyExpiring();
        assertThat(
                        outbox.findAll().stream()
                                .filter(
                                        e ->
                                                e.payload.contains(issue.toString())
                                                        && e.payload.contains("COUPON_EXPIRING")))
                .hasSize(1);
        UUID order = UUID.randomUUID();
        lockService.lock(
                new LockRequest(
                        order,
                        issue,
                        account,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        20000,
                        clock.instant().plusSeconds(300)));
        clock.set(clock.instant().plusSeconds(1000));
        maintenance.expire();
        assertThat(issues.findById(issue).orElseThrow().status)
                .isEqualTo(CouponIssue.Status.LOCKED);
        lockStore.lifecycle(order, "EXPIRED", false, 0);
        assertThat(issues.findById(issue).orElseThrow().status)
                .isEqualTo(CouponIssue.Status.EXPIRED);
    }

    @Autowired CouponDefinitionService definitions;
    @Autowired CampaignService campaigns;
    @Autowired CouponClaimService claims;
    @Autowired CouponCampaignRepository campaignRows;
    @Autowired CouponIssueRepository issues;
    @Autowired CouponLockService lockService;
    @Autowired CouponLockWorkStore lockStore;
    @Autowired CouponLockRepository locks;
    @Autowired CouponMaintenanceService maintenance;
    @Autowired CouponQueryService queries;
    @Autowired MutableClock clock;

    String campaign(int budget) {
        var now = clock.instant();
        var definition =
                definitions.create(
                        new DefinitionRequest(
                                "테스트 쿠폰",
                                "AMOUNT",
                                1000L,
                                null,
                                null,
                                0L,
                                "ALL",
                                "RELATIVE",
                                null,
                                null,
                                60,
                                10),
                        UUID.randomUUID());
        var campaign =
                campaigns.create(
                        new CampaignRequest(
                                "T" + UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                                definition.id(),
                                "CLAIM",
                                null,
                                budget,
                                1,
                                now.minusSeconds(1),
                                now.plusSeconds(3600),
                                false));
        campaigns.state(campaign.id(), CouponCampaign.Status.ACTIVE);
        return campaign.code();
    }

    <T> List<T> concurrent(int count, IntFunction<T> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(12)) {
            var start = new CountDownLatch(1);
            var results = new ArrayList<Future<T>>();
            for (int n = 0; n < count; n++) {
                final int index = n;
                results.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return action.apply(index);
                                }));
            }
            start.countDown();
            var values = new ArrayList<T>();
            for (var future : results) values.add(future.get(30, TimeUnit.SECONDS));
            return values;
        }
    }

    @Test
    void concurrentClaimsNeverExceedBudget() throws Exception {
        String code = campaign(7);
        var results =
                concurrent(
                        57,
                        n -> {
                            try {
                                return claims.claim(code, UUID.randomUUID()).created();
                            } catch (CampaignException soldOut) {
                                return false;
                            }
                        });
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(7);
        assertThat(campaignRows.findByCode(code).orElseThrow().issuedCount).isEqualTo(7);
    }

    @Test
    void concurrentSameAccountConsumesOneBudget() throws Exception {
        String code = campaign(1);
        UUID account = UUID.randomUUID();
        var results = concurrent(10, n -> claims.claim(code, account).coupon().issueId());
        assertThat(new HashSet<>(results)).hasSize(1);
        assertThat(campaignRows.findByCode(code).orElseThrow().issuedCount).isEqualTo(1);
    }

    @Test
    void lockIsIdempotentAndUnknownRecoveryKeepsIssueLocked() {
        UUID account = UUID.randomUUID();
        UUID issue = claims.claim(campaign(3), account).coupon().issueId();
        UUID order = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        var request =
                new LockRequest(
                        order,
                        issue,
                        account,
                        product,
                        UUID.randomUUID(),
                        20000,
                        clock.instant().plusSeconds(300));
        assertThat(lockService.lock(request)).isEqualTo(lockService.lock(request));
        assertThatThrownBy(
                        () ->
                                lockService.lock(
                                        new LockRequest(
                                                order,
                                                issue,
                                                account,
                                                product,
                                                request.sellerId(),
                                                30000,
                                                request.holdUntil())))
                .isInstanceOf(LockException.class);
        clock.set(clock.instant().plusSeconds(901));
        var work =
                lockStore.claim().stream()
                        .filter(w -> w.orderId().equals(order))
                        .findFirst()
                        .orElseThrow();
        assertThat(lockStore.claim()).noneMatch(w -> w.orderId().equals(order));
        lockStore.complete(work, null);
        assertThat(issues.findById(issue).orElseThrow().status)
                .isEqualTo(CouponIssue.Status.LOCKED);
        lockStore.lifecycle(order, "CONFIRMED", true, 1000);
        lockStore.lifecycle(order, "CONFIRMED", true, 1000);
        lockStore.lifecycle(order, "FAILED", false, 0);
        assertThat(issues.findById(issue).orElseThrow().status).isEqualTo(CouponIssue.Status.USED);
    }

    @Test
    void twoOrdersCannotLockTheSameIssue() throws Exception {
        UUID account = UUID.randomUUID();
        UUID issue = claims.claim(campaign(3), account).coupon().issueId();
        var results =
                concurrent(
                        2,
                        n -> {
                            try {
                                lockService.lock(
                                        new LockRequest(
                                                UUID.randomUUID(),
                                                issue,
                                                account,
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                20000,
                                                clock.instant().plusSeconds(300)));
                                return true;
                            } catch (CouponException conflict) {
                                return false;
                            }
                        });
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
    }

    @Test
    void selfProductIsRejectedAndEstimateRetainsMinimumCharge() {
        UUID account = UUID.randomUUID();
        UUID issue = claims.claim(campaign(3), account).coupon().issueId();
        assertThatThrownBy(
                        () ->
                                lockService.lock(
                                        new LockRequest(
                                                UUID.randomUUID(),
                                                issue,
                                                account,
                                                UUID.randomUUID(),
                                                account,
                                                20000,
                                                clock.instant().plusSeconds(300))))
                .isInstanceOf(CouponException.class);
        assertThat(
                        queries.applicable(account, UUID.randomUUID(), 150)
                                .getFirst()
                                .estimatedDiscount())
                .isEqualTo(50);
    }
}

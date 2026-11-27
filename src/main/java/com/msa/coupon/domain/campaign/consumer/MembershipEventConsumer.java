package com.msa.coupon.domain.campaign.consumer;

import com.msa.coupon.domain.campaign.command.service.CampaignIssueStore;
import com.msa.coupon.domain.campaign.dto.request.MembershipEvent;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;
import com.msa.coupon.domain.outbox.service.CouponNoticeService;
import com.msa.coupon.domain.receipt.InvalidCouponEventException;
import com.msa.coupon.domain.receipt.service.CouponReceiptService;
import com.msa.coupon.domain.receipt.service.EventHash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;

/** 회원 혜택 발급과 알림 및 receipt를 같은 트랜잭션으로 확정합니다. */
@Component
@ConditionalOnProperty(name = "coupon.consumer.enabled", havingValue = "true")
public class MembershipEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(MembershipEventConsumer.class);
    private static final Set<String> TYPES =
            Set.of(
                    "MEMBERSHIP_STARTED",
                    "MEMBERSHIP_RENEWED",
                    "MEMBERSHIP_RENEWAL_PENDING",
                    "MEMBERSHIP_PAYMENT_FAILED",
                    "MEMBERSHIP_CANCELED",
                    "MEMBERSHIP_CANCEL_WITHDRAWN",
                    "MEMBERSHIP_CANCEL_WITHDRAW_REJECTED",
                    "MEMBERSHIP_EXPIRED",
                    "MEMBERSHIP_WITHDRAWN");
    private static final Set<String> ISSUING_TYPES =
            Set.of("MEMBERSHIP_STARTED", "MEMBERSHIP_RENEWED");
    private final ObjectMapper mapper;
    private final CouponReceiptService receipts;
    private final CouponCampaignRepository campaigns;
    private final CampaignIssueStore store;
    private final CouponIssueRepository issues;
    private final CouponNoticeService notices;
    private final Clock clock;

    public MembershipEventConsumer(
            ObjectMapper mapper,
            CouponReceiptService receipts,
            CouponCampaignRepository campaigns,
            CampaignIssueStore store,
            CouponIssueRepository issues,
            CouponNoticeService notices,
            Clock clock) {
        this.mapper = mapper;
        this.receipts = receipts;
        this.campaigns = campaigns;
        this.store = store;
        this.issues = issues;
        this.notices = notices;
        this.clock = clock;
    }

    @KafkaListener(
            topics = "stockdrop.membership.events.private.v1",
            groupId = "coupon-membership-v1",
            containerFactory = "couponKafkaListenerContainerFactory")
    @Transactional(timeout = 5)
    public void receive(byte[] body) {
        MembershipEvent event;
        try {
            if (body == null || body.length > 16384) throw new InvalidCouponEventException();
            event = mapper.readValue(body, MembershipEvent.class);
            if (event.eventId() == null
                    || event.accountId() == null
                    || event.subscriptionId() == null
                    || event.schemaVersion() != 1
                    || !"PAYMENT".equals(event.source())
                    || event.generation() < 1
                    || event.aggregateVersion() < 1
                    || event.cycleNumber() < 0
                    || (ISSUING_TYPES.contains(event.type()) && event.cycleNumber() == 0)
                    || event.occurredAt() == null
                    || !TYPES.contains(event.type())) {
                throw new InvalidCouponEventException();
            }
        } catch (Exception failure) {
            throw new InvalidCouponEventException();
        }
        if (!receipts.accept(
                event.eventId(),
                "stockdrop.membership.events.private.v1",
                EventHash.of(new String(body, StandardCharsets.UTF_8)))) return;
        // 해지와 탈퇴의 주기 0도 처리 기록만 남기고 기존 쿠폰과 예산은 바꾸지 않습니다.
        if (!ISSUING_TYPES.contains(event.type())) return;
        if (event.entitlementUntil() == null) throw new InvalidCouponEventException();
        if (!event.entitlementUntil().isAfter(clock.instant())) return;

        String reference =
                event.subscriptionId() + ":" + event.generation() + ":" + event.cycleNumber();
        if (issues.findBySourceAndSourceRef("MEMBER", reference).isPresent()) return;
        var now = clock.instant();
        var active =
                campaigns.findByKindAndStatusOrderByCreatedAtAsc(
                        CouponCampaign.Kind.MEMBER, CouponCampaign.Status.ACTIVE);
        var running =
                active.stream()
                        .filter(c -> !now.isBefore(c.startsAt) && now.isBefore(c.endsAt))
                        .toList();
        // 진행 중 캠페인이 둘 이상이면 지급 대상을 고를 수 없는 설정 오류라 재시도 뒤 격리합니다.
        if (running.size() > 1) throw new IllegalStateException("MEMBER_CAMPAIGN_CONFIGURATION");
        // 없음, 일시정지, 기간 밖, 예산 소진은 정상 운영 상황이라 기록만 남기고 오프셋을 진행합니다.
        String skipped =
                active.isEmpty()
                        ? "NO_ACTIVE_CAMPAIGN"
                        : running.isEmpty()
                                ? "OUT_OF_PERIOD"
                                : running.getFirst().issuedCount >= running.getFirst().totalBudget
                                        ? "BUDGET_EXHAUSTED"
                                        : null;
        if (skipped != null) {
            log.warn(
                    "coupon member issue skipped eventId={} subscriptionId={} reason={}",
                    event.eventId(),
                    event.subscriptionId(),
                    skipped);
            return;
        }
        var result =
                store.issue(
                        running.getFirst().code,
                        event.accountId(),
                        reference,
                        "MEMBER",
                        reference,
                        null,
                        event.entitlementUntil());
        if (result.created()) {
            var issue = issues.findById(result.coupon().issueId()).orElseThrow();
            notices.append(
                    "COUPON",
                    "COUPON_MEMBER_ARRIVED",
                    issue.id,
                    issue.version,
                    issue.accountId,
                    null,
                    issue.id,
                    issue.validUntil);
        }
    }
}

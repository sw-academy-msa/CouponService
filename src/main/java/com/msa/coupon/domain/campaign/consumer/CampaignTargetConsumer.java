package com.msa.coupon.domain.campaign.consumer;

import com.msa.coupon.domain.campaign.command.service.PendingTargetStore;
import com.msa.coupon.domain.campaign.dto.request.CampaignTarget;
import com.msa.coupon.domain.receipt.InvalidCouponEventException;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 외부 동의 장애가 Kafka를 막지 않도록 내구성 큐에 먼저 기록합니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coupon.consumer.enabled", havingValue = "true")
public class CampaignTargetConsumer {
    private final ObjectMapper mapper;
    private final PendingTargetStore store;
    private final com.msa.coupon.domain.eligibility.client.AccountEligibilityClient eligibility;

    @KafkaListener(
            topics = "stockdrop.ai.campaign-targets.v1",
            groupId = "coupon-campaign-target-v1",
            containerFactory = "couponKafkaListenerContainerFactory")
    public void receive(byte[] body) {
        CampaignTarget event;
        try {
            if (body == null || body.length > 16384) throw new InvalidCouponEventException();
            event = mapper.readValue(body, CampaignTarget.class);
            if (event.eventId() == null
                    || event.accountId() == null
                    || event.productId() == null
                    || event.schemaVersion() != 1
                    || !"CAMPAIGN_TARGET".equals(event.type())
                    || event.subjectKey() == null
                    || event.subjectKey().isBlank()
                    || event.subjectKey().length() > 80
                    || event.campaignCode() == null
                    || !event.campaignCode().matches("[A-Z0-9_]{1,40}")
                    || event.deadline() == null
                    || event.occurredAt() == null
                    || !Set.of("SALE_OPEN", "CHECKOUT_ABANDON", "SOLD_OUT_SIMILAR", "CATEGORY_NEW")
                            .contains(event.ruleCode())) throw new InvalidCouponEventException();
        } catch (Exception invalid) {
            throw new InvalidCouponEventException();
        }
        store.enqueue(event, new String(body, StandardCharsets.UTF_8));
        // 원본 큐 커밋 뒤 즉시 시도하고 동의 HTTP가 실패한 경우만 잡에 맡깁니다.
        store.claim(event.eventId())
                .ifPresent(
                        work -> {
                            Boolean allowed = null;
                            try {
                                allowed = eligibility.eligible(work.target().accountId());
                            } catch (
                                    com.msa.coupon.domain.eligibility.client.RemoteUnavailable
                                            unavailable) {
                                // 같은 이벤트를 내구성 큐에서 다시 확인합니다.
                            }
                            store.complete(work, allowed);
                        });
    }
}

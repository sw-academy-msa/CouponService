package com.msa.coupon.domain.lock.scheduling;

import com.msa.coupon.domain.eligibility.client.RemoteUnavailable;
import com.msa.coupon.domain.lock.client.OrderOutcomeClient;
import com.msa.coupon.domain.lock.dto.response.OrderOutcome;
import com.msa.coupon.domain.lock.service.CouponLockWorkStore;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 임대 커밋 뒤 HTTP를 실행하고 동일 토큰만 완료할 수 있게 합니다. */
@Component
@ConditionalOnProperty(name = "coupon.scheduler.enabled", havingValue = "true")
public class CouponLockRecoveryJob {
    private final CouponLockWorkStore store;
    private final OrderOutcomeClient client;

    public CouponLockRecoveryJob(CouponLockWorkStore store, OrderOutcomeClient client) {
        this.store = store;
        this.client = client;
    }

    @Scheduled(
            fixedDelayString = "${coupon.lock-recovery.delay:PT30S}",
            scheduler = "couponScheduler")
    public void run() {
        for (var work : store.claim()) {
            OrderOutcome outcome = null;
            try {
                outcome = client.outcome(work.orderId());
            } catch (RemoteUnavailable ex) {
            }
            store.complete(work, outcome);
        }
    }
}
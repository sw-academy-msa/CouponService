package com.msa.coupon.domain.campaign.scheduling;

import com.msa.coupon.domain.campaign.command.service.PendingTargetStore;
import com.msa.coupon.domain.eligibility.client.AccountEligibilityClient;
import com.msa.coupon.domain.eligibility.client.RemoteUnavailable;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 임대 커밋 뒤 동의를 조회하여 HTTP 대기 중 연결을 반환합니다. */
@Component
@ConditionalOnProperty(name = "coupon.scheduler.enabled", havingValue = "true")
public class PendingTargetJob {
    private final PendingTargetStore store;
    private final AccountEligibilityClient eligibility;

    public PendingTargetJob(PendingTargetStore store, AccountEligibilityClient eligibility) {
        this.store = store;
        this.eligibility = eligibility;
    }

    @Scheduled(
            fixedDelayString = "${coupon.pending-target.delay:PT30S}",
            scheduler = "couponScheduler")
    public void run() {
        for (var work : store.claim()) {
            Boolean allowed = null;
            try {
                allowed = eligibility.eligible(work.target().accountId());
            } catch (RemoteUnavailable unavailable) {
                /* 다음 임대에서 다시 확인합니다. */
            }
            store.complete(work, allowed);
        }
    }
}
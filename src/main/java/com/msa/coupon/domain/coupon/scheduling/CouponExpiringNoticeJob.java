package com.msa.coupon.domain.coupon.scheduling;

import com.msa.coupon.domain.coupon.command.service.CouponMaintenanceService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 알림 표시와 Outbox를 함께 저장하여 서버 간 중복을 막습니다. */
@Component
@ConditionalOnProperty(name = "coupon.scheduler.enabled", havingValue = "true")
public class CouponExpiringNoticeJob {
    private final CouponMaintenanceService service;

    public CouponExpiringNoticeJob(CouponMaintenanceService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${coupon.expiring-notice.delay:PT1M}",
            scheduler = "couponScheduler")
    public void run() {
        service.notifyExpiring();
    }
}
package com.msa.coupon.domain.coupon.scheduling;

import com.msa.coupon.domain.coupon.command.service.CouponMaintenanceService;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 발급 상태만 작은 묶음으로 처리하여 진행 중인 주문을 보호합니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coupon.scheduler.enabled", havingValue = "true")
public class CouponExpiryJob {
    private final CouponMaintenanceService service;

    @Scheduled(fixedDelayString = "${coupon.expiry.delay:PT1M}", scheduler = "couponScheduler")
    public void run() {
        service.expire();
    }
}

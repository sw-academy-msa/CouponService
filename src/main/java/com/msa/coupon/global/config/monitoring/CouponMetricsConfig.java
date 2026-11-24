package com.msa.coupon.global.config.monitoring;

import com.msa.coupon.domain.campaign.entity.CouponPendingTarget;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.campaign.repository.CouponPendingTargetRepository;
import com.msa.coupon.domain.lock.repository.CouponLockRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/** 업무 경보 집계를 캐시하여 메트릭 수집 요청이 데이터베이스를 점유하지 않게 합니다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "monitoring.coupon-enabled", havingValue = "true")
public class CouponMetricsConfig {
    private static final Logger log = LoggerFactory.getLogger(CouponMetricsConfig.class);

    private final CouponCampaignRepository campaigns;
    private final CouponPendingTargetRepository pending;
    private final CouponLockRepository locks;
    private final Clock clock;
    private final AtomicLong budgetWarning = new AtomicLong(-1);
    private final AtomicLong pendingFailed = new AtomicLong(-1);
    private final AtomicLong overdue = new AtomicLong(-1);

    public CouponMetricsConfig(
            CouponCampaignRepository campaigns,
            CouponPendingTargetRepository pending,
            CouponLockRepository locks,
            Clock clock,
            MeterRegistry registry) {
        this.campaigns = campaigns;
        this.pending = pending;
        this.locks = locks;
        this.clock = clock;
        Gauge.builder("stockdrop.coupon.campaign.budget.warning", budgetWarning, AtomicLong::get)
                .register(registry);
        Gauge.builder("stockdrop.coupon.pending.failed", pendingFailed, AtomicLong::get)
                .register(registry);
        Gauge.builder("stockdrop.coupon.locks.overdue", overdue, AtomicLong::get)
                .register(registry);
    }

    @Scheduled(
            fixedDelayString = "${monitoring.coupon-poll-ms:30000}",
            scheduler = "couponScheduler")
    public void sample() {
        try {
            budgetWarning.set(campaigns.countBudgetWarning());
            pendingFailed.set(pending.countByStatus(CouponPendingTarget.Status.FAILED));
            overdue.set(locks.countOverdue(clock.instant().minusSeconds(86400)));
        } catch (RuntimeException unavailable) {
            log.warn("coupon metrics unavailable");
        }
    }
}
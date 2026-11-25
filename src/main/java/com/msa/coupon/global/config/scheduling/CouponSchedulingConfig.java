package com.msa.coupon.global.config.scheduling;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 복제본 간 중복은 데이터베이스 잠금으로 제어합니다. */
@Configuration
@EnableScheduling
public class CouponSchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler couponScheduler() {
        var s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("coupon-");
        return s;
    }
}
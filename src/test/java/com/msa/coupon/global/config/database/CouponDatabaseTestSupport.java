package com.msa.coupon.global.config.database;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 실제 PostgreSQL과 주입 시각으로 유니크 및 임대를 검증합니다. */
public abstract class CouponDatabaseTestSupport {
    static final PostgreSQLContainer DB =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("coupon")
                    .withUsername("coupon_test");

    static {
        DB.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DB::getJdbcUrl);
        properties.add("spring.datasource.username", DB::getUsername);
        properties.add("spring.datasource.password", DB::getPassword);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> true);
    }

    /** 잠금 만료를 실제 대기 없이 재현하도록 테스트 시각을 주입합니다. */
    @TestConfiguration
    public static class Time {
        @Bean
        @Primary
        public MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    /** 여러 작업 스레드가 같은 시각 경계를 읽도록 합니다. */
    public static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2030-01-01T00:00:00Z");

        public void set(Instant instant) {
            now = instant;
        }

        public Instant instant() {
            return now;
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
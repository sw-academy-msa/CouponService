package com.msa.coupon.global.config.database;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 빈 PostgreSQL에 Flyway를 적용하고 Hibernate 검증 및 핵심 제약을 확인합니다. */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest extends CouponDatabaseTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationCreatesUniqueIssueAndActiveLockConstraints() {
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from coupon.flyway_schema_history where"
                                        + " success=true and version='1'",
                                Long.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select indexdef from pg_indexes where schemaname='coupon' and"
                                        + " indexname='uq_active_issue_lock'",
                                String.class))
                .contains("UNIQUE", "LOCKED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from pg_constraint where conname in"
                                        + " ('uq_issue_subject','uq_issue_source',"
                                        + "'ck_campaign_budget')",
                                Long.class))
                .isEqualTo(3);
    }
}
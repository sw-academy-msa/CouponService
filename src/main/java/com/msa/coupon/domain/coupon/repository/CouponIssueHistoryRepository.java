package com.msa.coupon.domain.coupon.repository;

import com.msa.coupon.domain.coupon.entity.CouponIssueHistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** 원본 행 잠금과 조건부 갱신으로 복제본 간 경합을 제어합니다. */
public interface CouponIssueHistoryRepository extends JpaRepository<CouponIssueHistory, UUID> {
    @Query(
            value =
                    "select count(*) filter(where action='ISSUED'), count(*) filter(where"
                            + " action='USED'), count(*) filter(where action='RELEASED'),count(*)"
                            + " filter(where action='EXPIRED'), (select"
                            + " coalesce(sum(discount_applied),0) from coupon.coupon_issues where"
                            + " campaign_id=:id) from coupon.coupon_issue_history where"
                            + " campaign_id=:id",
            nativeQuery = true)
    List<Object[]> stats(@Param("id") UUID id);
}
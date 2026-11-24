package com.msa.coupon.domain.campaign.repository;

import com.msa.coupon.domain.campaign.entity.CouponPendingTarget;

import org.springframework.data.jpa.repository.*;

import java.util.*;

/** 원본 행 잠금과 조건부 갱신으로 복제본 간 경합을 제어합니다. */
public interface CouponPendingTargetRepository extends JpaRepository<CouponPendingTarget, UUID> {
    long countByStatus(String status);

    @Query(
            value =
                    "select * from coupon.coupon_pending_targets where status='PENDING' and"
                        + " next_attempt_at<=:now and (lease_until is null or lease_until<=:now)"
                        + " order by next_attempt_at limit 10 for update skip locked",
            nativeQuery = true)
    List<CouponPendingTarget> due(
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CouponPendingTarget p where p.eventId=:id")
    Optional<CouponPendingTarget> lock(
            @org.springframework.data.repository.query.Param("id") UUID id);
}

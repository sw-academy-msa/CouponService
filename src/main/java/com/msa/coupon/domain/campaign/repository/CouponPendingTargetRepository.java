package com.msa.coupon.domain.campaign.repository;

import com.msa.coupon.domain.campaign.entity.CouponPendingTarget;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 원본 행 잠금과 조건부 갱신으로 복제본 간 경합을 제어합니다. */
public interface CouponPendingTargetRepository extends JpaRepository<CouponPendingTarget, UUID> {
    long countByStatus(CouponPendingTarget.Status status);

    @Query(
            value =
                    "select * from coupon.coupon_pending_targets where status='PENDING' and"
                        + " next_attempt_at<=:now and (lease_until is null or lease_until<=:now)"
                        + " order by next_attempt_at limit 10 for update skip locked",
            nativeQuery = true)
    List<CouponPendingTarget> due(
            @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CouponPendingTarget p where p.eventId=:id")
    Optional<CouponPendingTarget> lock(
            @Param("id") UUID id);
}
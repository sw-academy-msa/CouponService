package com.msa.coupon.domain.lock.repository;

import com.msa.coupon.domain.lock.entity.CouponLock;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

/** 원본 행 잠금과 조건부 갱신으로 복제본 간 경합을 제어합니다. */
public interface CouponLockRepository extends JpaRepository<CouponLock, UUID> {
    @Query(
            value =
                    "select count(*) from coupon.coupon_locks where state='LOCKED' and"
                            + " locked_at<:cutoff",
            nativeQuery = true)
    long countOverdue(@Param("cutoff") Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CouponLock l where l.orderId=:id")
    Optional<CouponLock> lock(@Param("id") UUID id);

    @Query(
            value =
                    "select * from coupon.coupon_locks where state='LOCKED' and next_check_at<=:now"
                        + " and (lease_until is null or lease_until<=:now) order by next_check_at"
                        + " limit 10 for update skip locked",
            nativeQuery = true)
    List<CouponLock> due(@Param("now") Instant now);
}

package com.msa.coupon.domain.lock.repository;

import com.msa.coupon.domain.lock.entity.CouponLock;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 복구 대상은 SKIP LOCKED로 나눠 가져오고 완료는 행 잠금 뒤 lease token으로 확인합니다. */
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
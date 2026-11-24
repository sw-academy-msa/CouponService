package com.msa.coupon.domain.receipt.repository;

import com.msa.coupon.domain.receipt.entity.CouponEventReceipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** 원본 행 잠금과 조건부 갱신으로 복제본 간 경합을 제어합니다. */
public interface CouponEventReceiptRepository extends JpaRepository<CouponEventReceipt, UUID> {
    @Modifying
    @Query(
            value =
                    "insert into"
                        + " coupon.coupon_event_receipts(event_id,topic,payload_hash,processed_at)"
                        + " values (:id,:topic,:hash,:now) on conflict(event_id) do nothing",
            nativeQuery = true)
    int insertOnce(
            @Param("id") UUID id,
            @Param("topic") String topic,
            @Param("hash") String hash,
            @Param("now") Instant now);
}
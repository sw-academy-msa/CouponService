package com.msa.coupon.domain.receipt.repository;

import com.msa.coupon.domain.receipt.entity.CouponEventReceipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** receipt는 ON CONFLICT DO NOTHING으로 한 번만 넣어 동시 재전달에서도 한 번만 처리합니다. */
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
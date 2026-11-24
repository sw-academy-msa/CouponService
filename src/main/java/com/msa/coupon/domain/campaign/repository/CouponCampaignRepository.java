package com.msa.coupon.domain.campaign.repository;

import com.msa.coupon.domain.campaign.entity.CouponCampaign;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

/** 원본 행 잠금과 조건부 갱신으로 복제본 간 경합을 제어합니다. */
public interface CouponCampaignRepository extends JpaRepository<CouponCampaign, UUID> {
    @Query(
            value =
                    "select count(*) from coupon.coupon_campaigns where status='ACTIVE' and"
                            + " issued_count*10::bigint>=total_budget*9::bigint",
            nativeQuery = true)
    long countBudgetWarning();

    @Query(value = "select status from coupon.coupon_campaigns where id=:id", nativeQuery = true)
    String currentStatus(@Param("id") UUID id);

    Optional<CouponCampaign> findByCode(String code);

    List<CouponCampaign> findByKindAndStatusOrderByCreatedAtAsc(String kind, String status);

    @Modifying
    @Query(
            value =
                    "update coupon.coupon_campaigns set"
                        + " issued_count=issued_count+1,row_version=row_version+1 where id=:id and"
                        + " status='ACTIVE' and :now >= starts_at and :now < ends_at and"
                        + " issued_count < total_budget",
            nativeQuery = true)
    int reserve(@Param("id") UUID id, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CouponCampaign c where c.id=:id")
    Optional<CouponCampaign> lock(@Param("id") UUID id);
}

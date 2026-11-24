package com.msa.coupon.domain.coupon.repository;

import com.msa.coupon.domain.coupon.entity.CouponIssue;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 원본 행 잠금과 조건부 갱신으로 복제본 간 경합을 제어합니다. */
public interface CouponIssueRepository extends JpaRepository<CouponIssue, UUID> {
    @Query(
            "select i from CouponIssue i where i.accountId=:account and ((:status='AVAILABLE' and"
                    + " (i.status=LOCKED or (i.status=ISSUED and i.validUntil>:now))) or"
                    + " (:status='USED' and i.status=USED) or (:status='EXPIRED' and"
                    + " (i.status=EXPIRED or (i.status=ISSUED and i.validUntil<=:now))))")
    Page<CouponIssue> mine(
            @Param("account") UUID account,
            @Param("status") String status,
            @Param("now") Instant now,
            Pageable page);

    @Query(
            "select i from CouponIssue i where i.accountId=:account and i.status=ISSUED "
                    + "and i.validFrom<=:now and i.validUntil>:now and i.minItemsAmount<=:items "
                    + "and (i.scope='ALL' or i.productId=:product) order by i.validUntil")
    List<CouponIssue> applicable(
            @Param("account") UUID account,
            @Param("product") UUID product,
            @Param("items") long items,
            @Param("now") Instant now);

    Optional<CouponIssue> findByCampaignIdAndAccountIdAndSubjectKey(
            UUID campaignId, UUID accountId, String subjectKey);

    Optional<CouponIssue> findBySourceAndSourceRef(String source, String sourceRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CouponIssue i where i.id=:id")
    Optional<CouponIssue> lock(@Param("id") UUID id);

    @Modifying
    @Query(
            value =
                    "update coupon.coupon_issues set status='LOCKED',version=version+1 where id=:id"
                        + " and account_id=:account and status='ISSUED' and valid_from<=:now and"
                        + " valid_until>:now and (scope='ALL' or product_id=:product) and"
                        + " min_items_amount<=:items",
            nativeQuery = true)
    int acquire(
            @Param("id") UUID id,
            @Param("account") UUID account,
            @Param("product") UUID product,
            @Param("items") long items,
            @Param("now") Instant now);

    @Query(
            value =
                    "select * from coupon.coupon_issues where status='ISSUED' and valid_until<=:now"
                            + " order by valid_until limit 200 for update skip locked",
            nativeQuery = true)
    List<CouponIssue> expired(@Param("now") Instant now);

    @Query(
            value =
                    "select * from coupon.coupon_issues where status='ISSUED' and"
                        + " expiring_noticed_at is null and valid_until>:now and valid_until -"
                        + " notice_lead_minutes * interval '1 minute'<=:now order by valid_until"
                        + " limit 50 for update skip locked",
            nativeQuery = true)
    List<CouponIssue> expiring(@Param("now") Instant now);
}
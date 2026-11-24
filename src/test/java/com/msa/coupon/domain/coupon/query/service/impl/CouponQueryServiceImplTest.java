package com.msa.coupon.domain.coupon.query.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.coupon.entity.CouponIssue;
import com.msa.coupon.domain.coupon.repository.CouponDefinitionRepository;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;
import com.msa.coupon.domain.lock.dto.request.LockRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** 예상 할인은 내림과 상한 및 최소 상품 결제액을 지키며 오버플로를 피합니다. */
class CouponQueryServiceImplTest {
    @Test
    void sensitiveDtoBuilderDoesNotExposeAccount() {
        UUID account = UUID.randomUUID();
        assertThat(
                        LockRequest.builder()
                                .accountId(account)
                                .toString())
                .isEqualTo("LockRequestBuilder[REDACTED]")
                .doesNotContain(account.toString());
    }

    @ParameterizedTest
    @CsvSource({
        "AMOUNT,150,1000,50",
        "RATE,10001,1000,1000",
        "RATE,9223372036854775807,1000,3000",
        "RATE,100,10000,0"
    })
    void estimateFloorsCapsAndRetainsMinimumCharge(
            String type, long items, int rate, long expected) {
        var repository = mock(CouponIssueRepository.class);
        UUID account = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var issue =
                CouponIssue.builder()
                        .id(UUID.randomUUID())
                        .discountType(type)
                        .amount(1000L)
                        .rateBps(rate)
                        .maxDiscount(3000L)
                        .scope("ALL")
                        .status(CouponIssue.Status.ISSUED)
                        .build();
        when(repository.applicable(account, product, items, now)).thenReturn(List.of(issue));
        var query =
                new CouponQueryServiceImpl(
                        mock(CouponCampaignRepository.class),
                        mock(CouponDefinitionRepository.class),
                        repository,
                        Clock.fixed(now, ZoneOffset.UTC));
        assertThat(query.applicable(account, product, items).getFirst().estimatedDiscount())
                .isEqualTo(expected);
    }
}
package com.msa.coupon.domain.coupon.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.msa.coupon.domain.campaign.dto.request.CampaignTarget;
import com.msa.coupon.domain.campaign.dto.request.MembershipEvent;
import com.msa.coupon.domain.coupon.entity.CouponIssue;
import com.msa.coupon.domain.lock.dto.request.LockRequest;
import com.msa.coupon.domain.lock.dto.response.LockResponse;
import com.msa.coupon.global.config.campaign.CampaignProperties;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/** 계정, 주문, 쿠폰 식별자가 DTO와 빌더 문자열로 로그에 나가지 않는지 검증합니다. */
class CouponBuilderTest {
    @Test
    void buildersAndRecordsAreRedacted() {
        UUID account = UUID.randomUUID();
        var lock = LockRequest.builder().accountId(account).itemsAmount(30000).build();

        assertThat(lock.toString()).isEqualTo("LockRequest[REDACTED]").doesNotContain("30000");
        assertThat(LockRequest.builder().accountId(account).toString())
                .isEqualTo("LockRequestBuilder[REDACTED]");
        assertThat(LockResponse.builder().orderId(account).toString())
                .isEqualTo("LockResponseBuilder[REDACTED]");
        assertThat(IssueResponse.builder().issueId(account).toString())
                .isEqualTo("IssueResponseBuilder[REDACTED]");
        assertThat(CampaignTarget.builder().accountId(account).build().toString())
                .doesNotContain(account.toString());
        assertThat(MembershipEvent.builder().accountId(account).build().toString())
                .doesNotContain(account.toString());
        assertThat(CouponIssue.builder().accountId(account).build().toString())
                .isEqualTo("CouponIssue[REDACTED]");
    }

    @Test
    void demoAllowListFailsClosed() {
        UUID account = UUID.randomUUID();

        assertThat(new CampaignProperties(account.toString()).demoAllowed(account)).isTrue();
        assertThat(new CampaignProperties("").demoAllowed(account)).isFalse();
        assertThat(new CampaignProperties(account + ",not-a-uuid").demoAllowed(account)).isFalse();
        assertThat(new CampaignProperties(account.toString()).toString())
                .doesNotContain(account.toString());
    }
}

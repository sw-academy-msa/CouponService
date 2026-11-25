package com.msa.coupon.domain.coupon.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.msa.coupon.domain.campaign.exception.CampaignErrorCode;
import com.msa.coupon.domain.campaign.exception.CampaignException;
import com.msa.coupon.domain.lock.exception.LockErrorCode;
import com.msa.coupon.domain.lock.exception.LockException;
import com.msa.coupon.domain.receipt.InvalidCouponEventException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 도메인 예외가 설계의 HTTP 상태와 코드를 지키고 DLT 예외에 원문을 담지 않는지 검증합니다. */
class CouponExceptionTest {
    @Test
    void permanentEventFailureKeepsTypeAndFixedMessage() {
        var exception = new InvalidCouponEventException();

        assertThat(exception).isInstanceOf(CouponException.class);
        assertThat(exception.getClass().getName())
                .isEqualTo("com.msa.coupon.domain.receipt.InvalidCouponEventException");
        assertThat(exception.getMessage()).isEqualTo("COUPON_EVENT_INVALID");
        assertThat(exception.getErrorCode()).isEqualTo(CouponErrorCode.INVALID_EVENT);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void domainCodesMatchDesignStatuses() {
        assertThat(new CouponException(CouponErrorCode.COUPON_NOT_OWNED).getErrorCode().status())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(CouponErrorCode.COUPON_SELF_PRODUCT.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(CouponErrorCode.COUPON_NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(
                        new CampaignException(CampaignErrorCode.CAMPAIGN_SOLD_OUT)
                                .getErrorCode()
                                .code())
                .isEqualTo("CAMPAIGN_SOLD_OUT");
        assertThat(CampaignErrorCode.CAMPAIGN_BUDGET_DECREASE.status())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(new LockException(LockErrorCode.COUPON_LOCK_CONFLICT).getMessage())
                .isEqualTo(LockErrorCode.COUPON_LOCK_CONFLICT.message());
    }
}

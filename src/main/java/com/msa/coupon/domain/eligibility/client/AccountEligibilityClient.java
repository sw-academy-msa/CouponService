package com.msa.coupon.domain.eligibility.client;

import java.util.UUID;

/** 최신 마케팅 동의를 외부 트랜잭션 밖에서 확인합니다. */
public interface AccountEligibilityClient {
    boolean eligible(UUID account);
}

package com.msa.coupon.domain.campaign.exception;

import com.msa.coupon.global.error.BusinessException;

/** 캠페인 오류 코드만 받아 공통 예외 처리기가 같은 형식으로 응답하게 합니다. */
public class CampaignException extends BusinessException {
    public CampaignException(CampaignErrorCode code) {
        super(code);
    }
}
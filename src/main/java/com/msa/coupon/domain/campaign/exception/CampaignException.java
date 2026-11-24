package com.msa.coupon.domain.campaign.exception;

import com.msa.coupon.global.error.BusinessException;

/** 업무 오류를 공통 응답 처리기로 전달합니다. */
public class CampaignException extends BusinessException {
    public CampaignException(CampaignErrorCode code) {
        super(code);
    }
}

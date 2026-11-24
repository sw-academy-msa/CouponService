package com.msa.coupon.domain.campaign.exception;

import com.msa.coupon.global.error.ErrorCode;

import org.springframework.http.HttpStatus;

/** 입력값 없이 사용자에게 보여 줄 고정 업무 오류만 정의합니다. */
public enum CampaignErrorCode implements ErrorCode {
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없습니다."),
    CAMPAIGN_NOT_ACTIVE(HttpStatus.CONFLICT, "CAMPAIGN_NOT_ACTIVE", "진행 중인 캠페인이 아닙니다."),
    CAMPAIGN_SOLD_OUT(HttpStatus.CONFLICT, "CAMPAIGN_SOLD_OUT", "쿠폰이 모두 소진되었습니다."),
    CAMPAIGN_BUDGET_DECREASE(HttpStatus.CONFLICT, "CAMPAIGN_BUDGET_DECREASE", "예산은 늘리기만 가능합니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;

    CampaignErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}

package com.msa.coupon.domain.campaign.command.controller;

import com.msa.coupon.domain.campaign.command.service.ClaimCampaignNoticeService;
import com.msa.coupon.domain.campaign.dto.response.CampaignResponse;
import com.msa.coupon.global.response.*;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 운영자의 받기형 쿠폰 알림 요청은 기능 플래그가 켜진 경우에만 받습니다. */
@RestController
@RequestMapping(value = "/api/v1/admin/coupons/campaigns", produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(
        name = {"coupon.api.enabled", "coupon.claim-notification-enabled"},
        havingValue = "true")
public class AdminClaimCampaignNoticeController {
    private final ClaimCampaignNoticeService service;

    public AdminClaimCampaignNoticeController(ClaimCampaignNoticeService service) {
        this.service = service;
    }

    @PostMapping("/{id}/notifications")
    @Operation(
            summary = "받기형 캠페인 알림 보내기",
            description = "광고 동의한 비관리자 구매자에게 쿠폰 받기 알림을 요청합니다. 재요청은 최초 결과를 반환합니다.")
    public ApiResponse<CampaignResponse> send(@PathVariable UUID id) {
        return ApiResponse.onSuccess(service.send(id), SuccessCode.OK);
    }
}

package com.msa.coupon.domain.campaign.command.controller;

import com.msa.coupon.domain.campaign.command.service.CampaignService;
import com.msa.coupon.domain.campaign.dto.request.BudgetRequest;
import com.msa.coupon.domain.campaign.dto.request.CampaignRequest;
import com.msa.coupon.domain.campaign.dto.response.CampaignResponse;
import com.msa.coupon.domain.campaign.dto.response.CampaignStatsResponse;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.global.response.ApiResponse;
import com.msa.coupon.global.response.SuccessCode;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 예산과 활성화 상태 변경은 운영자 권한으로 제한합니다. */
@RestController
@RequestMapping(value = "/api/v1/admin/coupons/campaigns", produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = "coupon.api.enabled", havingValue = "true")
public class AdminCampaignController {
    private final CampaignService service;

    public AdminCampaignController(CampaignService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "캠페인 목록", description = "운영자가 캠페인의 예산과 상태를 확인합니다.")
    public ApiResponse<List<CampaignResponse>> list() {
        return ApiResponse.onSuccess(service.list(), SuccessCode.OK);
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "캠페인 통계", description = "발급과 사용 및 플랫폼 할인 합계를 조회합니다.")
    public ApiResponse<CampaignStatsResponse> stats(
            @PathVariable UUID id) {
        return ApiResponse.onSuccess(service.stats(id), SuccessCode.OK);
    }

    @PostMapping
    @Operation(summary = "캠페인 생성", description = "새 캠페인은 초안 상태로 생성합니다.")
    public ResponseEntity<ApiResponse<CampaignResponse>> create(
            @Valid @RequestBody CampaignRequest r) {
        return ResponseEntity.status(201)
                .body(ApiResponse.onSuccess(service.create(r), SuccessCode.CREATED));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "캠페인 활성화", description = "발급 기간 안에서 쿠폰 받기를 허용합니다.")
    public ApiResponse<CampaignResponse> activate(@PathVariable UUID id) {
        return ApiResponse.onSuccess(
                service.state(id, CouponCampaign.Status.ACTIVE), SuccessCode.OK);
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "캠페인 일시정지", description = "새로운 발급을 중단합니다.")
    public ApiResponse<CampaignResponse> pause(@PathVariable UUID id) {
        return ApiResponse.onSuccess(
                service.state(id, CouponCampaign.Status.PAUSED), SuccessCode.OK);
    }

    @PatchMapping("/{id}/budget")
    @Operation(summary = "캠페인 예산 증액", description = "총 장수 예산을 늘릴 수 있습니다.")
    public ApiResponse<CampaignResponse> budget(
            @PathVariable UUID id, @Valid @RequestBody BudgetRequest r) {
        return ApiResponse.onSuccess(service.budget(id, r.totalBudget()), SuccessCode.OK);
    }
}
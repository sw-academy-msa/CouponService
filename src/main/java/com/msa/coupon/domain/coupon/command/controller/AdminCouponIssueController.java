package com.msa.coupon.domain.coupon.command.controller;

import com.msa.coupon.domain.coupon.command.service.CouponMaintenanceService;
import com.msa.coupon.global.response.ApiResponse;
import com.msa.coupon.global.response.SuccessCode;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 사용 중이거나 사용한 쿠폰은 운영자도 회수할 수 없습니다. */
@RestController
@RequestMapping(value = "/api/v1/admin/coupons/issues", produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coupon.api.enabled", havingValue = "true")
public class AdminCouponIssueController {
    private final CouponMaintenanceService service;

    @PostMapping("/{id}/revoke")
    @Operation(summary = "미사용 쿠폰 회수", description = "발급 상태 쿠폰만 회수할 수 있습니다.")
    public ApiResponse<Void> revoke(@PathVariable UUID id) {
        service.revoke(id);
        return ApiResponse.onSuccess(null, SuccessCode.OK);
    }
}

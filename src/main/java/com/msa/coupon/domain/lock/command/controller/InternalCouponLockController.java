package com.msa.coupon.domain.lock.command.controller;

import com.msa.coupon.domain.lock.dto.request.LockRequest;
import com.msa.coupon.domain.lock.dto.response.LockResponse;
import com.msa.coupon.domain.lock.service.CouponLockService;
import com.msa.coupon.global.response.ApiResponse;
import com.msa.coupon.global.response.SuccessCode;

import io.swagger.v3.oas.annotations.Hidden;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 주문 서비스 주체와 scope를 모두 확인한 내부 잠금만 처리합니다. */
@RestController
@RequestMapping(value = "/internal/v1/coupon-locks", produces = "application/json")
@Hidden
@ConditionalOnProperty(name = "coupon.api.enabled", havingValue = "true")
public class InternalCouponLockController {
    private final CouponLockService service;

    public InternalCouponLockController(CouponLockService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@orderServiceAccess.allowed(#jwt)")
    public ResponseEntity<ApiResponse<LockResponse>> lock(
            @Valid @RequestBody LockRequest r, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.onSuccess(service.lock(r), SuccessCode.OK));
    }
}
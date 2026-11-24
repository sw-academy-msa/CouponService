package com.msa.coupon.domain.lock.command.controller;

import com.msa.coupon.domain.lock.dto.request.LockRequest;
import com.msa.coupon.domain.lock.dto.response.LockResponse;
import com.msa.coupon.domain.lock.service.CouponLockService;
import com.msa.coupon.global.response.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 주문 서비스 주체와 scope를 모두 확인한 내부 잠금만 처리합니다. */
@RestController
@RequestMapping(value = "/internal/v1/coupon-locks", produces = "application/json")
@io.swagger.v3.oas.annotations.Hidden
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coupon.api.enabled", havingValue = "true")
public class InternalCouponLockController {
    private final CouponLockService service;

    @PostMapping
    @PreAuthorize("@orderServiceAccess.allowed(#jwt)")
    public ResponseEntity<ApiResponse<LockResponse>> lock(
            @Valid @RequestBody LockRequest r, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.onSuccess(service.lock(r), SuccessCode.OK));
    }
}

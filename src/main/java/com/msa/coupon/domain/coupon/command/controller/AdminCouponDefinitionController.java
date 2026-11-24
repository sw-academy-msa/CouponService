package com.msa.coupon.domain.coupon.command.controller;

import com.msa.coupon.domain.coupon.command.service.CouponDefinitionService;
import com.msa.coupon.domain.coupon.dto.request.DefinitionRequest;
import com.msa.coupon.domain.coupon.dto.response.DefinitionResponse;
import com.msa.coupon.global.response.*;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 운영자만 불변 쿠폰 정의를 생성합니다. */
@RestController
@RequestMapping(value = "/api/v1/admin/coupons/definitions", produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coupon.api.enabled", havingValue = "true")
public class AdminCouponDefinitionController {
    @PostMapping("/{id}/retire")
    @Operation(summary = "쿠폰 정의 종료", description = "기존 발급 조건은 보존하고 새 발급만 중단합니다.")
    public ApiResponse<DefinitionResponse> retire(@PathVariable UUID id) {
        return ApiResponse.onSuccess(service.retire(id), SuccessCode.OK);
    }

    private final CouponDefinitionService service;

    @PostMapping
    @Operation(summary = "쿠폰 정의 생성", description = "발급 시 복사할 할인 조건을 등록합니다.")
    public ResponseEntity<ApiResponse<DefinitionResponse>> create(
            @Valid @RequestBody DefinitionRequest r, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(201)
                .body(
                        ApiResponse.onSuccess(
                                service.create(r, UUID.fromString(jwt.getSubject())),
                                SuccessCode.CREATED));
    }

    @GetMapping
    @Operation(summary = "쿠폰 정의 목록", description = "운영자가 등록한 정의를 조회합니다.")
    public ApiResponse<List<DefinitionResponse>> list() {
        return ApiResponse.onSuccess(service.list(), SuccessCode.OK);
    }
}

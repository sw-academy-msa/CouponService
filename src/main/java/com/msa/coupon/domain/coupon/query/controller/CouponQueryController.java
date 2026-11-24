package com.msa.coupon.domain.coupon.query.controller;

import com.msa.coupon.domain.coupon.command.service.CouponClaimService;
import com.msa.coupon.domain.coupon.dto.response.ApplicableCouponResponse;
import com.msa.coupon.domain.coupon.dto.response.IssueResponse;
import com.msa.coupon.domain.coupon.dto.response.PublicCampaignResponse;
import com.msa.coupon.domain.coupon.query.service.CouponQueryService;
import com.msa.coupon.global.response.ApiResponse;
import com.msa.coupon.global.response.SuccessCode;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 사용자 식별은 검증된 JWT 주체에서만 읽습니다. */
@RestController
@RequestMapping(value = "/api/v1/coupons", produces = "application/json")
@ConditionalOnProperty(name = "coupon.api.enabled", havingValue = "true")
public class CouponQueryController {
    private final CouponQueryService service;
    private final CouponClaimService claims;

    public CouponQueryController(CouponQueryService service, CouponClaimService claims) {
        this.service = service;
        this.claims = claims;
    }

    @GetMapping("/me/applicable")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(
            summary = "적용 가능 쿠폰 조회",
            description = "상품에 쓸 수 있는 쿠폰과 예상 할인액입니다. 실제 적용은 주문 잠금 때 다시 판단합니다.")
    public ResponseEntity<ApiResponse<List<ApplicableCouponResponse>>> applicable(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID productId,
            @RequestParam long itemsAmount) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.onSuccess(
                                service.applicable(
                                        UUID.fromString(jwt.getSubject()), productId, itemsAmount),
                                SuccessCode.OK));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "내 쿠폰 조회", description = "사용 중인 쿠폰도 사용 가능 탭에 표시합니다.")
    public ResponseEntity<ApiResponse<List<IssueResponse>>> mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "AVAILABLE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.onSuccess(
                                service.mine(UUID.fromString(jwt.getSubject()), status, page, size),
                                SuccessCode.OK));
    }

    @GetMapping("/campaigns/{code}")
    @Operation(summary = "받기 캠페인 조회", description = "로그인한 경우에만 이미 받았는지 표시합니다.")
    public ResponseEntity<ApiResponse<PublicCampaignResponse>> campaign(
            @PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.onSuccess(
                                service.campaign(
                                        code,
                                        jwt == null ? null : UUID.fromString(jwt.getSubject())),
                                SuccessCode.OK));
    }

    @PostMapping("/campaigns/{code}/claims")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "쿠폰 받기", description = "같은 캠페인에서 이미 받았다면 기존 발급을 돌려줍니다.")
    public ResponseEntity<ApiResponse<IssueResponse>> claim(
            @PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        var r = claims.claim(code, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.status(r.created() ? 201 : 200)
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.onSuccess(
                                r.coupon(), r.created() ? SuccessCode.CREATED : SuccessCode.OK));
    }
}
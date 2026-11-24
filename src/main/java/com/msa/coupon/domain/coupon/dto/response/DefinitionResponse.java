package com.msa.coupon.domain.coupon.dto.response;

import com.msa.coupon.domain.coupon.entity.CouponDefinition;

import lombok.Builder;

import java.util.UUID;

/** HTTP 경계에서 원본 엔티티와 입력을 분리합니다. */
@Builder
public record DefinitionResponse(
        UUID id,
        String name,
        String discountType,
        Long amount,
        Integer rateBps,
        Long maxDiscount,
        long minItemsAmount,
        String scope,
        String status,
        long version) {
    @Override
    public String toString() {
        return "DefinitionResponse[REDACTED]";
    }

    public static DefinitionResponse from(CouponDefinition e) {
        return new DefinitionResponse(
                e.id,
                e.name,
                e.discountType,
                e.amount,
                e.rateBps,
                e.maxDiscount,
                e.minItemsAmount,
                e.scope,
                e.status.name(),
                e.version);
    }

    /** 빌더 문자열에 담긴 값을 로그에 노출하지 않습니다. */
    public static class DefinitionResponseBuilder {
        @Override
        public String toString() {
            return "DefinitionResponseBuilder[REDACTED]";
        }
    }
}
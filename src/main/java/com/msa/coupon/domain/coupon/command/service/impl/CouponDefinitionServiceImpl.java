package com.msa.coupon.domain.coupon.command.service.impl;

import com.msa.coupon.domain.campaign.dto.request.*;
import com.msa.coupon.domain.campaign.dto.response.*;
import com.msa.coupon.domain.campaign.entity.*;
import com.msa.coupon.domain.campaign.exception.*;
import com.msa.coupon.domain.campaign.repository.*;
import com.msa.coupon.domain.coupon.command.service.CouponDefinitionService;
import com.msa.coupon.domain.coupon.dto.request.*;
import com.msa.coupon.domain.coupon.dto.response.*;
import com.msa.coupon.domain.coupon.entity.*;
import com.msa.coupon.domain.coupon.exception.*;
import com.msa.coupon.domain.coupon.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/** 정의는 생성 후 불변 조건으로 발급 시점 스냅샷을 보장합니다. */
@Service
@RequiredArgsConstructor
@Transactional(timeout = 5)
public class CouponDefinitionServiceImpl implements CouponDefinitionService {
    public DefinitionResponse retire(UUID id) {
        var definition =
                definitions
                        .findById(id)
                        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
        definition.status = "RETIRED";
        definition.updatedAt = clock.instant();
        return DefinitionResponse.from(definition);
    }

    private final CouponDefinitionRepository definitions;
    private final Clock clock;

    public DefinitionResponse create(DefinitionRequest r, UUID actor) {
        boolean discount =
                "AMOUNT".equals(r.discountType())
                        ? r.amount() != null && r.amount() > 0
                        : "RATE".equals(r.discountType())
                                && r.rateBps() != null
                                && r.rateBps() > 0
                                && r.rateBps() <= 10000
                                && r.maxDiscount() != null
                                && r.maxDiscount() > 0;
        boolean validity =
                "FIXED".equals(r.validityMode())
                        ? r.validFrom() != null
                                && r.validUntil() != null
                                && r.validFrom().isBefore(r.validUntil())
                        : "RELATIVE".equals(r.validityMode())
                                && r.validMinutes() != null
                                && r.validMinutes() > 0;
        if (!discount || !validity || !Set.of("ALL", "PRODUCT").contains(r.scope()))
            throw new CouponException(CouponErrorCode.INVALID);
        var now = clock.instant();
        return DefinitionResponse.from(
                definitions.save(
                        CouponDefinition.builder()
                                .id(UUID.randomUUID())
                                .name(r.name())
                                .discountType(r.discountType())
                                .amount(r.amount())
                                .rateBps(r.rateBps())
                                .maxDiscount(r.maxDiscount())
                                .minItemsAmount(r.minItemsAmount())
                                .scope(r.scope())
                                .validityMode(r.validityMode())
                                .validFrom(r.validFrom())
                                .validUntil(r.validUntil())
                                .validMinutes(r.validMinutes())
                                .noticeLeadMinutes(r.noticeLeadMinutes())
                                .status("ACTIVE")
                                .version(1)
                                .createdBy(actor)
                                .createdAt(now)
                                .updatedAt(now)
                                .build()));
    }

    @Transactional(readOnly = true)
    public List<DefinitionResponse> list() {
        return definitions.findAll().stream().map(DefinitionResponse::from).toList();
    }
}

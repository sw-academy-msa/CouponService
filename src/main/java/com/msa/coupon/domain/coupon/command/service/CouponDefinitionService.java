package com.msa.coupon.domain.coupon.command.service;

import com.msa.coupon.domain.coupon.dto.request.DefinitionRequest;
import com.msa.coupon.domain.coupon.dto.response.DefinitionResponse;

import java.util.List;
import java.util.UUID;

/** 쿠폰 정의는 만든 뒤 조건을 바꾸지 않고 종료 후 새로 만들어 발급 스냅샷을 지킵니다. */
public interface CouponDefinitionService {
    DefinitionResponse retire(UUID id);

    DefinitionResponse create(DefinitionRequest r, UUID actor);

    List<DefinitionResponse> list();
}
package com.msa.coupon.domain.coupon.command.service;

import com.msa.coupon.domain.coupon.dto.request.DefinitionRequest;
import com.msa.coupon.domain.coupon.dto.response.DefinitionResponse;

import java.util.List;
import java.util.UUID;

/** 도메인 경계를 인터페이스로 분리합니다. */
public interface CouponDefinitionService {
    DefinitionResponse retire(UUID id);

    DefinitionResponse create(DefinitionRequest r, UUID actor);

    List<DefinitionResponse> list();
}
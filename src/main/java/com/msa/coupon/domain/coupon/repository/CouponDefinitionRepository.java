package com.msa.coupon.domain.coupon.repository;

import com.msa.coupon.domain.coupon.entity.CouponDefinition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 원본 행 잠금과 조건부 갱신으로 복제본 간 경합을 제어합니다. */
public interface CouponDefinitionRepository extends JpaRepository<CouponDefinition, UUID> {}
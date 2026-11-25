package com.msa.coupon.domain.coupon.repository;

import com.msa.coupon.domain.coupon.entity.CouponDefinition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 정의는 운영자만 쓰고 발급 경합이 없어 기본 조회만 둡니다. */
public interface CouponDefinitionRepository extends JpaRepository<CouponDefinition, UUID> {}
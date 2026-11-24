package com.msa.coupon.domain.coupon.command.service;

import java.util.UUID;

/** 만료와 회수 및 알림을 원본 상태와 함께 기록합니다. */
public interface CouponMaintenanceService {
    void expire();

    void notifyExpiring();

    void revoke(UUID id);
}

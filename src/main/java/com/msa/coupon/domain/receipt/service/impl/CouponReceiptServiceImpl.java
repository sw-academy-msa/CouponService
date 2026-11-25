package com.msa.coupon.domain.receipt.service.impl;

import com.msa.coupon.domain.receipt.InvalidCouponEventException;
import com.msa.coupon.domain.receipt.repository.CouponEventReceiptRepository;
import com.msa.coupon.domain.receipt.service.CouponReceiptService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** 같은 식별자에 다른 본문이 들어오면 영구 계약 오류로 격리합니다. */
@Service
public class CouponReceiptServiceImpl implements CouponReceiptService {
    private final CouponEventReceiptRepository receipts;
    private final Clock clock;

    public CouponReceiptServiceImpl(CouponEventReceiptRepository receipts, Clock clock) {
        this.receipts = receipts;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean accept(UUID id, String topic, String hash) {
        if (receipts.insertOnce(id, topic, hash, clock.instant()) == 1) return true;
        var old = receipts.findById(id);
        if (old.isPresent()) {
            if (!hash.equals(old.get().payloadHash) || !topic.equals(old.get().topic))
                throw new InvalidCouponEventException();
            return false;
        }
        throw new IllegalStateException("RECEIPT_NOT_FOUND");
    }
}
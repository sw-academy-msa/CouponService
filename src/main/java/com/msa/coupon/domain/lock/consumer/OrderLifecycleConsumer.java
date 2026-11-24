package com.msa.coupon.domain.lock.consumer;

import com.msa.coupon.domain.lock.dto.request.OrderLifecycle;
import com.msa.coupon.domain.lock.repository.CouponLockRepository;
import com.msa.coupon.domain.lock.service.CouponLockWorkStore;
import com.msa.coupon.domain.receipt.InvalidCouponEventException;
import com.msa.coupon.domain.receipt.service.CouponReceiptService;
import com.msa.coupon.domain.receipt.service.EventHash;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/** 이벤트와 receipt를 함께 커밋하여 재전달에도 한 번만 상태를 바꿉니다. */
@Component
@ConditionalOnProperty(name = "coupon.consumer.enabled", havingValue = "true")
public class OrderLifecycleConsumer {
    private final ObjectMapper mapper;
    private final CouponReceiptService receipts;
    private final CouponLockWorkStore store;
    private final CouponLockRepository locks;

    public OrderLifecycleConsumer(
            ObjectMapper mapper,
            CouponReceiptService receipts,
            CouponLockWorkStore store,
            CouponLockRepository locks) {
        this.mapper = mapper;
        this.receipts = receipts;
        this.store = store;
        this.locks = locks;
    }

    @KafkaListener(
            topics = "stockdrop.orders.lifecycle.v1",
            groupId = "coupon-order-lifecycle-v1",
            containerFactory = "couponKafkaListenerContainerFactory")
    @Transactional(timeout = 5)
    public void receive(byte[] body) {
        OrderLifecycle e;
        try {
            if (body == null || body.length > 16384) throw new InvalidCouponEventException();
            e = mapper.readValue(body, OrderLifecycle.class);
            if (e.eventId() == null
                    || e.orderId() == null
                    || e.buyerId() == null
                    || e.schemaVersion() != 1
                    || !"ORDER_LIFECYCLE".equals(e.type())
                    || !Set.of("HELD", "CONFIRMED", "FAILED", "EXPIRED").contains(e.phase())
                    || e.couponDiscount() < 0
                    || !UUID.nameUUIDFromBytes(
                                    ("ORDER_LIFECYCLE:" + e.orderId() + ":" + e.phase())
                                            .getBytes(StandardCharsets.UTF_8))
                            .equals(e.eventId())) throw new InvalidCouponEventException();
        } catch (Exception ex) {
            throw new InvalidCouponEventException();
        }
        var locked = locks.findById(e.orderId());
        if (locked.isPresent()
                && (!locked.get().accountId.equals(e.buyerId())
                        || (e.couponApplied()
                                && !locked.get().issueId.equals(e.couponIssueId())))) {
            throw new InvalidCouponEventException();
        }
        if (receipts.accept(
                e.eventId(),
                "stockdrop.orders.lifecycle.v1",
                EventHash.of(new String(body, StandardCharsets.UTF_8))))
            store.lifecycle(e.orderId(), e.phase(), e.couponApplied(), e.couponDiscount());
    }
}
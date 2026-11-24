package com.msa.coupon.domain.outbox.service.impl;

import com.msa.coupon.domain.outbox.entity.OutboxEvent;
import com.msa.coupon.domain.outbox.repository.OutboxEventRepository;
import com.msa.coupon.domain.outbox.service.CouponNoticeService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 알림 식별자를 안정적으로 생성해 중복 전송을 소비자가 제거할 수 있게 합니다. */
@Service
@RequiredArgsConstructor
public class CouponNoticeServiceImpl implements CouponNoticeService {
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(
            String source,
            String type,
            UUID aggregate,
            long version,
            UUID account,
            UUID product,
            UUID issue,
            Instant deadline) {
        var id =
                UUID.nameUUIDFromBytes(
                        (source + ":" + type + ":" + aggregate + ":" + version + ":" + account)
                                .getBytes(StandardCharsets.UTF_8));
        if (outbox.existsById(id)) return;
        var p = new LinkedHashMap<String, Object>();
        p.put("eventId", id);
        p.put("schemaVersion", 1);
        p.put("type", "BUSINESS_NOTIFICATION");
        p.put("source", source);
        p.put("accountId", account);
        p.put("notificationType", type);
        p.put("aggregateId", aggregate);
        p.put("aggregateVersion", version);
        p.put("targetType", product == null ? "COUPON" : "PRODUCT");
        p.put("targetId", product == null ? issue : product);
        p.put("relatedId", issue);
        p.put("deadline", deadline);
        p.put("occurredAt", clock.instant());
        outbox.save(
                OutboxEvent.builder()
                        .id(id)
                        .topic(
                                "stockdrop.notifications."
                                        + ("COUPON".equals(source) ? "coupon" : "campaign")
                                        + ".v1")
                        .aggregateId(account)
                        .payload(mapper.writeValueAsString(p))
                        .now(clock.instant())
                        .build());
    }
}

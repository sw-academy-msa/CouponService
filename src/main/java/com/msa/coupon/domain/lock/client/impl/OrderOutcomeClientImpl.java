package com.msa.coupon.domain.lock.client.impl;

import com.msa.coupon.domain.eligibility.client.InternalHttp;
import com.msa.coupon.domain.eligibility.client.RemoteUnavailable;
import com.msa.coupon.domain.eligibility.client.ServiceTokenProvider;
import com.msa.coupon.domain.lock.client.OrderOutcomeClient;
import com.msa.coupon.domain.lock.dto.response.OrderOutcome;
import com.msa.coupon.global.config.client.ClientProperties;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/** 인증된 주문 조회 결과만 사용하고 타임아웃과 계약 오류는 불명으로 남깁니다. */
@Component
public class OrderOutcomeClientImpl implements OrderOutcomeClient {
    private final InternalHttp http;
    private final ClientProperties properties;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    public OrderOutcomeClientImpl(
            InternalHttp http,
            ClientProperties properties,
            ServiceTokenProvider tokens,
            ObjectMapper mapper) {
        this.http = http;
        this.properties = properties;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    public OrderOutcome outcome(UUID orderId) {
        try {
            var r =
                    http.send(
                            properties.orderCommand(),
                            "GET",
                            "/internal/v1/orders/" + orderId + "/coupon-outcome",
                            Map.of(
                                    "Authorization",
                                    "Bearer " + tokens.token(),
                                    "Accept",
                                    "application/json"),
                            null);
            if (r.status() == 404) return new OrderOutcome(orderId, null, null, null, true);
            if (r.status() == 401) tokens.invalidate();
            if (r.status() != 200) throw new RemoteUnavailable();
            var b = mapper.readTree(r.body());
            if (!b.path("success").asBoolean()) throw new RemoteUnavailable();
            var d = b.path("data");
            if (!orderId.toString().equals(d.path("orderId").asText())
                    || !d.path("couponApplied").isBoolean()
                    || !d.path("phase").isString()) throw new RemoteUnavailable();
            if (d.path("couponApplied").asBoolean()
                    && (!d.path("couponDiscount").isIntegralNumber()
                            || !d.path("couponDiscount").canConvertToLong()
                            || d.path("couponDiscount").asLong() < 0))
                throw new RemoteUnavailable();
            return new OrderOutcome(
                    orderId,
                    d.path("phase").asText(),
                    d.path("couponApplied").asBoolean(),
                    d.path("couponDiscount").asLong(),
                    false);
        } catch (Exception ex) {
            throw new RemoteUnavailable();
        }
    }
}
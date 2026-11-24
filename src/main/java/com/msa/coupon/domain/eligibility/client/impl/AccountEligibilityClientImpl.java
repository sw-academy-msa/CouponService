package com.msa.coupon.domain.eligibility.client.impl;

import com.msa.coupon.domain.eligibility.client.*;
import com.msa.coupon.global.config.client.ClientProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** 외부 오류는 거절과 구분하여 재시도 대상으로 남깁니다. */
@Component
@RequiredArgsConstructor
public class AccountEligibilityClientImpl implements AccountEligibilityClient {
    private final InternalHttp http;
    private final ClientProperties properties;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    public boolean eligible(UUID account) {
        try {
            var r =
                    http.send(
                            properties.account(),
                            "GET",
                            "/internal/v1/notification-eligibility?accountId="
                                    + account
                                    + "&purpose=PERSONALIZED",
                            Map.of(
                                    "Authorization",
                                    "Bearer " + tokens.token(),
                                    "Accept",
                                    "application/json"),
                            null);
            if (r.status() == 401) tokens.invalidate();
            if (r.status() != 200) throw new RemoteUnavailable();
            var b = mapper.readTree(r.body());
            if (!b.path("success").asBoolean() || !b.path("data").path("eligible").isBoolean())
                throw new RemoteUnavailable();
            return b.path("data").path("eligible").asBoolean();
        } catch (Exception ex) {
            throw new RemoteUnavailable();
        }
    }
}

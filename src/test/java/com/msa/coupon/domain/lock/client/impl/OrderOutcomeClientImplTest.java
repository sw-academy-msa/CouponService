package com.msa.coupon.domain.lock.client.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.coupon.domain.eligibility.client.*;
import com.msa.coupon.domain.eligibility.dto.response.InternalReply;
import com.msa.coupon.global.config.client.ClientProperties;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 주문 HTTP 오류와 계약 오류는 확정된 미존재와 구분합니다. */
class OrderOutcomeClientImplTest {
    @Test
    void fixtureDistinguishesConfirmedMissingAndUnknown() throws Exception {
        UUID order = UUID.randomUUID();
        var status = new AtomicInteger(200);
        var body =
                new AtomicReference<>(
                        "{\"success\":true,\"data\":{\"orderId\":\""
                                + order
                                + "\",\"phase\":\"CONFIRMED\""
                                + ",\"couponApplied\":true,\"couponDiscount\":1000}}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status.get(), bytes.length);
                    try (var stream = exchange.getResponseBody()) {
                        stream.write(bytes);
                    }
                });
        server.start();
        try {
            var httpClient = HttpClient.newHttpClient();
            InternalHttp transport =
                    (service, method, path, headers, requestBody) -> {
                        try {
                            var request =
                                    HttpRequest.newBuilder(
                                            URI.create(
                                                    "http://127.0.0.1:"
                                                            + server.getAddress().getPort()
                                                            + path));
                            headers.forEach(request::header);
                            var response =
                                    httpClient.send(
                                            request.GET().build(),
                                            HttpResponse.BodyHandlers.ofString());
                            return new InternalReply(response.statusCode(), response.body());
                        } catch (Exception failure) {
                            throw new RemoteUnavailable();
                        }
                    };
            ServiceTokenProvider tokens =
                    new ServiceTokenProvider() {
                        public String token() {
                            return "fixture-token";
                        }

                        public void invalidate() {}
                    };
            var properties =
                    new ClientProperties(
                            "http", "account", "ai", "product-query", "order-command", "", "");
            var client =
                    new OrderOutcomeClientImpl(
                            transport,
                            properties,
                            tokens,
                            JsonMapper.builder().findAndAddModules().build());
            assertThat(client.outcome(order).couponApplied()).isTrue();
            status.set(404);
            assertThat(client.outcome(order).notFound()).isTrue();
            status.set(500);
            assertThatThrownBy(() -> client.outcome(order)).isInstanceOf(RemoteUnavailable.class);
            status.set(200);
            body.set("{}");
            assertThatThrownBy(() -> client.outcome(order)).isInstanceOf(RemoteUnavailable.class);
        } finally {
            server.stop(0);
        }
    }
}

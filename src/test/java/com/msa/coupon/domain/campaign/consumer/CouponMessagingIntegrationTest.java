package com.msa.coupon.domain.campaign.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.msa.coupon.domain.campaign.dto.request.MembershipEvent;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;
import com.msa.coupon.domain.receipt.repository.CouponEventReceiptRepository;
import com.msa.coupon.global.config.database.CouponDatabaseTestSupport;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 실제 브로커에서 유한 재시도와 원본 파티션 DLT 전달을 확인합니다. */
@SpringBootTest(
        properties = {"coupon.consumer.enabled=true", "spring.kafka.listener.auto-startup=true"})
@ActiveProfiles("test")
@Import(CouponDatabaseTestSupport.Time.class)
class CouponMessagingIntegrationTest extends CouponDatabaseTestSupport {
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");
    static final String ORDERS = "stockdrop.orders.lifecycle.v1";
    static final String MEMBER = "stockdrop.membership.events.private.v1";

    static {
        KAFKA.start();
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            var topics = new ArrayList<NewTopic>();
            for (var topic : List.of(ORDERS, MEMBER, "stockdrop.ai.campaign-targets.v1")) {
                topics.add(new NewTopic(topic, 2, (short) 1));
                topics.add(new NewTopic(topic + ".DLT", 2, (short) 1));
            }
            admin.createTopics(topics).all().get(20, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry properties) {
        properties.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired KafkaTemplate<String, byte[]> kafka;
    @Autowired DefaultErrorHandler handler;
    @Autowired ObjectMapper mapper;
    @Autowired MutableClock clock;
    @Autowired CouponEventReceiptRepository receipts;
    @Autowired CouponIssueRepository issues;

    @Test
    void nonIssuingPaymentResultsCommitThroughKafkaWithoutCampaigns() throws Exception {
        long issuedBefore = issues.count();
        var events = new ArrayList<MembershipEvent>();
        for (String type :
                List.of(
                        "MEMBERSHIP_CANCELED",
                        "MEMBERSHIP_WITHDRAWN",
                        "MEMBERSHIP_RENEWAL_PENDING")) {
            var event =
                    new MembershipEvent(
                            UUID.randomUUID(),
                            1,
                            type,
                            "PAYMENT",
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            1,
                            1,
                            type.equals("MEMBERSHIP_RENEWAL_PENDING") ? 2 : 0,
                            clock.instant().plusSeconds(3600),
                            clock.instant());
            events.add(event);
            kafka.send(MEMBER, event.accountId().toString(), mapper.writeValueAsBytes(event))
                    .get(10, TimeUnit.SECONDS);
        }
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            for (var event : events)
                                assertThat(receipts.findById(event.eventId())).isPresent();
                        });
        assertThat(issues.count()).isEqualTo(issuedBefore);
    }

    KafkaConsumer<String, byte[]> dlt(String topic) {
        var consumer =
                new KafkaConsumer<String, byte[]>(
                        Map.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                KAFKA.getBootstrapServers(),
                                ConsumerConfig.GROUP_ID_CONFIG,
                                "verify-" + UUID.randomUUID(),
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                                "earliest",
                                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                                false),
                        new StringDeserializer(),
                        new ByteArrayDeserializer());
        consumer.subscribe(List.of(topic + ".DLT"));
        return consumer;
    }

    @Test
    void invalidContractGoesToOriginalPartitionDlt() throws Exception {
        String key = UUID.randomUUID().toString();
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        try (var consumer = dlt(ORDERS)) {
            kafka.send(ORDERS, 1, key, body).get(10, TimeUnit.SECONDS);
            var found = new ArrayList<ConsumerRecord<String, byte[]>>();
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(
                            () -> {
                                consumer.poll(Duration.ofMillis(300)).forEach(found::add);
                                assertThat(found)
                                        .anySatisfy(
                                                r -> {
                                                    assertThat(r.key()).isEqualTo(key);
                                                    assertThat(r.partition()).isEqualTo(1);
                                                    assertThat(r.value()).isEqualTo(body);
                                                });
                            });
        }
    }

    @Test
    void temporaryFailureRetriesThreeTimesThenDlt() throws Exception {
        String key = UUID.randomUUID().toString();
        var attempts = new AtomicInteger();
        handler.setRetryListeners(
                (record, exception, attempt) -> {
                    if (key.equals(record.key())) attempts.incrementAndGet();
                });
        var event =
                new MembershipEvent(
                        UUID.randomUUID(),
                        1,
                        "MEMBERSHIP_STARTED",
                        "PAYMENT",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        1,
                        1,
                        clock.instant().plusSeconds(3600),
                        clock.instant());
        try (var consumer = dlt(MEMBER)) {
            kafka.send(MEMBER, 0, key, mapper.writeValueAsBytes(event)).get(10, TimeUnit.SECONDS);
            var found = new ArrayList<ConsumerRecord<String, byte[]>>();
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(
                            () -> {
                                consumer.poll(Duration.ofMillis(300)).forEach(found::add);
                                assertThat(found).anyMatch(r -> key.equals(r.key()));
                            });
            assertThat(attempts.get()).isEqualTo(4);
        }
    }
}

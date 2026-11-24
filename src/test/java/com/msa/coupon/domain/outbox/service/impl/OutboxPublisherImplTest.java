package com.msa.coupon.domain.outbox.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.coupon.domain.outbox.entity.OutboxEvent;
import com.msa.coupon.domain.outbox.scheduling.OutboxPublishJob;
import com.msa.coupon.domain.outbox.service.OutboxPublisher;
import com.msa.coupon.global.config.database.CouponDatabaseTestSupport;

import jakarta.persistence.EntityManager;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 실제 Kafka 중단·복구와 DB 발행 완료 기록, 복제본 발행 경합을 검증합니다. */
@SpringBootTest(
        properties = {"coupon.outbox.publisher-enabled=true", "coupon.outbox.poll-ms=3600000"})
@ActiveProfiles("test")
@Import(CouponDatabaseTestSupport.Time.class)
class OutboxPublisherImplTest extends CouponDatabaseTestSupport {
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");
    static final String TOPIC = "outbox-integration-test";

    static {
        KAFKA.start();
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 2, (short) 1)))
                    .all()
                    .get(20, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    // 직접 발행을 검증하는 동안 초기 자동 실행이 같은 Outbox 행을 선점하지 않도록 격리합니다.
    @MockitoBean OutboxPublishJob automaticPublisher;

    @Autowired OutboxPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManager em;
    @Autowired MutableClock clock;

    @BeforeEach
    void clean() {
        jdbc.update("delete from coupon.outbox_events");
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
    }

    UUID enqueue() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(
                s ->
                        em.persist(
                                new OutboxEvent(
                                        id,
                                        TOPIC,
                                        UUID.randomUUID(),
                                        "{\"eventId\":\"" + id + "\"}",
                                        clock.instant())));
        return id;
    }

    @Test
    void brokerFailureRetainsEventAndRecoveryPublishesSamePayload() {
        UUID id = enqueue();
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            assertThat(publisher.publishNext()).isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "select published_at is null from coupon.outbox_events where"
                                            + " id = ?",
                                    Boolean.class,
                                    id))
                    .isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "select attempts from coupon.outbox_events where id = ?",
                                    Integer.class,
                                    id))
                    .isEqualTo(1);
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }
        clock.set(clock.instant().plusSeconds(65));
        assertThat(publisher.publishNext()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select published_at is not null from coupon.outbox_events where"
                                        + " id = ?",
                                Boolean.class,
                                id))
                .isTrue();
        assertThat(publisher.publishNext()).isFalse();
        try (var consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));
            boolean found = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!found && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    found |=
                            new String(record.value(), StandardCharsets.UTF_8)
                                    .contains(id.toString());
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Test
    void acknowledgedSendCanBeRedeliveredAfterDatabaseRollback() {
        UUID id = enqueue();
        tx.executeWithoutResult(
                status -> {
                    assertThat(publisher.publishNext()).isTrue();
                    // Kafka ack 후 DB 완료 기록이 커밋되지 못한 장애 구간을 재현합니다.
                    status.setRollbackOnly();
                });
        assertThat(
                        jdbc.queryForObject(
                                "select published_at is null from coupon.outbox_events where id ="
                                        + " ?",
                                Boolean.class,
                                id))
                .isTrue();
        assertThat(publisher.publishNext()).isTrue();
        try (var consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));
            var payloads = new ArrayList<String>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (payloads.size() < 2 && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    String body =
                            new String(record.value(), StandardCharsets.UTF_8);
                    if (body.contains(id.toString())) {
                        payloads.add(body);
                    }
                }
            }
            assertThat(payloads).hasSize(2);
            assertThat(payloads.get(0)).isEqualTo(payloads.get(1));
        }
    }

    @Test
    void concurrentPublishersDoNotSimultaneouslyClaimSameRow() throws Exception {
        enqueue();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var results = pool.invokeAll(List.of(publisher::publishNext, publisher::publishNext));
            int claimed = 0;
            for (var result : results) {
                if (Boolean.TRUE.equals(result.get())) {
                    claimed++;
                }
            }
            assertThat(claimed).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select attempts from coupon.outbox_events", Integer.class))
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "select published_at is not null from coupon.outbox_events",
                                    Boolean.class))
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void repeatedFailureIsolatesEventAfterTwentyAttempts() {
        UUID id = enqueue();
        jdbc.update("update coupon.outbox_events set attempts = 19 where id = ?", id);
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            assertThat(publisher.publishNext()).isTrue();
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }
        assertThat(
                        jdbc.queryForObject(
                                "select failed_at is not null and published_at is null from"
                                        + " coupon.outbox_events where id = ?",
                                Boolean.class,
                                id))
                .isTrue();
        // 격리된 행은 시각이 지나도 다시 발행하지 않고 운영자 재처리를 기다립니다.
        clock.set(clock.instant().plusSeconds(3600));
        assertThat(publisher.publishNext()).isFalse();
    }

    KafkaConsumer<String, byte[]> consumer() {
        return new KafkaConsumer<>(
                Map.of(
                        "bootstrap.servers",
                        KAFKA.getBootstrapServers(),
                        "group.id",
                        "outbox-test-" + UUID.randomUUID(),
                        "auto.offset.reset",
                        "earliest"),
                new StringDeserializer(),
                new ByteArrayDeserializer());
    }
}

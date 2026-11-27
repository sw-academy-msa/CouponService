package com.msa.coupon.domain.campaign.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.msa.coupon.domain.campaign.dto.request.MembershipEvent;
import com.msa.coupon.domain.campaign.entity.CouponCampaign;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.coupon.entity.CouponDefinition;
import com.msa.coupon.domain.coupon.repository.CouponDefinitionRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import java.time.Instant;
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
@ExtendWith(OutputCaptureExtension.class)
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
    @Autowired CouponCampaignRepository campaigns;
    @Autowired CouponDefinitionRepository definitions;
    private final List<UUID> createdCampaigns = new ArrayList<>();
    private final List<UUID> createdDefinitions = new ArrayList<>();

    /** 공유 테스트 DB에 회원 캠페인을 남겨 다른 발급 검증을 바꾸지 않습니다. */
    @AfterEach
    void cleanFixtures() {
        campaigns.deleteAllById(createdCampaigns);
        definitions.deleteAllById(createdDefinitions);
        createdCampaigns.clear();
        createdDefinitions.clear();
    }

    MembershipEvent started() {
        return new MembershipEvent(
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
    }

    CouponCampaign memberCampaign(
            CouponCampaign.Status status, int issued, Instant starts, Instant ends) {
        var now = clock.instant();
        var d =
                definitions.save(
                        CouponDefinition.builder()
                                .id(UUID.randomUUID())
                                .name("회원 쿠폰")
                                .discountType("AMOUNT")
                                .amount(3000L)
                                .scope("ALL")
                                .validityMode("RELATIVE")
                                .validMinutes(60)
                                .noticeLeadMinutes(10)
                                .status(CouponDefinition.Status.ACTIVE)
                                .createdBy(UUID.randomUUID())
                                .createdAt(now)
                                .updatedAt(now)
                                .build());
        createdDefinitions.add(d.id);
        var saved =
                campaigns.saveAndFlush(
                        CouponCampaign.builder()
                                .id(UUID.randomUUID())
                                .code(
                                        "M"
                                                + UUID.randomUUID()
                                                        .toString()
                                                        .replace("-", "")
                                                        .toUpperCase())
                                .definitionId(d.id)
                                .kind(CouponCampaign.Kind.MEMBER)
                                .status(status)
                                .totalBudget(10)
                                .issuedCount(issued)
                                .perAccountLimit(1)
                                .startsAt(starts)
                                .endsAt(ends)
                                .createdAt(now)
                                .updatedAt(now)
                                .build());
        createdCampaigns.add(saved.id);
        return saved;
    }

    /** 지급할 진행 중 회원 캠페인이 없는 정상 운영 상황은 재시도와 DLT 없이 커밋합니다. */
    @Test
    void startedWithoutUsableMemberCampaignCommitsWithoutDlt(CapturedOutput output)
            throws Exception {
        var now = clock.instant();
        var attempts = new AtomicInteger();
        handler.setRetryListeners((record, exception, attempt) -> attempts.incrementAndGet());
        long issuedBefore = issues.count();
        for (String reason :
                List.of("NO_ACTIVE_CAMPAIGN", "PAUSED", "OUT_OF_PERIOD", "BUDGET_EXHAUSTED")) {
            switch (reason) {
                case "PAUSED" ->
                        memberCampaign(
                                CouponCampaign.Status.PAUSED,
                                0,
                                now.minusSeconds(60),
                                now.plusSeconds(3600));
                case "OUT_OF_PERIOD" ->
                        memberCampaign(
                                CouponCampaign.Status.ACTIVE,
                                0,
                                now.plusSeconds(60),
                                now.plusSeconds(3600));
                case "BUDGET_EXHAUSTED" ->
                        memberCampaign(
                                CouponCampaign.Status.ACTIVE,
                                10,
                                now.minusSeconds(60),
                                now.plusSeconds(3600));
                default -> {}
            }
            var event = started();
            kafka.send(MEMBER, event.accountId().toString(), mapper.writeValueAsBytes(event))
                    .get(10, TimeUnit.SECONDS);
            // receipt는 발급 트랜잭션이 커밋될 때만 남으므로 오프셋 진행을 뜻합니다.
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(receipts.findById(event.eventId())).isPresent());
            assertThat(output)
                    .contains("eventId=" + event.eventId())
                    .contains("subscriptionId=" + event.subscriptionId())
                    .doesNotContain(event.accountId().toString());
            cleanFixtures();
        }
        assertThat(attempts.get()).isZero();
        assertThat(issues.count()).isEqualTo(issuedBefore);
    }

    @Test
    void nonIssuingPaymentResultsCommitThroughKafkaWithoutCampaigns() throws Exception {
        long issuedBefore = issues.count();
        var events = new ArrayList<MembershipEvent>();
        for (String type :
                List.of(
                        "MEMBERSHIP_CANCELED",
                        "MEMBERSHIP_CANCEL_WITHDRAWN",
                        "MEMBERSHIP_CANCEL_WITHDRAW_REJECTED",
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

    /** 진행 중 회원 캠페인이 둘 이상이면 지급 대상을 고를 수 없어 재시도 뒤 격리합니다. */
    @Test
    void temporaryFailureRetriesThreeTimesThenDlt(CapturedOutput output) throws Exception {
        var now = clock.instant();
        memberCampaign(CouponCampaign.Status.ACTIVE, 0, now.minusSeconds(60), now.plusSeconds(3600));
        memberCampaign(CouponCampaign.Status.ACTIVE, 0, now.minusSeconds(60), now.plusSeconds(3600));
        String key = UUID.randomUUID().toString();
        var attempts = new AtomicInteger();
        handler.setRetryListeners(
                (record, exception, attempt) -> {
                    if (key.equals(record.key())) attempts.incrementAndGet();
                });
        var event = started();
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
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () ->
                                    assertThat(output)
                                            .contains("coupon dlt published")
                                            .contains("cause=IllegalStateException"));
        }
    }
}

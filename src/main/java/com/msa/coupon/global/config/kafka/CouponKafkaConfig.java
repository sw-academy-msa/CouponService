package com.msa.coupon.global.config.kafka;

import com.msa.coupon.domain.receipt.InvalidCouponEventException;

import io.micrometer.core.instrument.MeterRegistry;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

/** 계약 오류는 즉시 격리하고 일시 실패는 세 번만 재시도합니다. */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "coupon.consumer.enabled", havingValue = "true")
public class CouponKafkaConfig {
    @Bean
    public DefaultErrorHandler couponErrorHandler(
            MeterRegistry meters,
            KafkaTemplate<String, byte[]> kafka,
            ObjectProvider<KafkaListenerEndpointRegistry> registry) {
        var r =
                new DeadLetterPublishingRecoverer(
                        kafka,
                        (record, ex) ->
                                new TopicPartition(record.topic() + ".DLT", record.partition()));
        r.setFailIfSendResultIsError(true);
        r.setWaitForSendResultTimeout(Duration.ofSeconds(10));
        var h =
                new DefaultErrorHandler(
                        (record, exception) -> {
                            try {
                                r.accept(record, exception);
                                meters.counter("stockdrop.coupon.dlt.published").increment();
                            } catch (RuntimeException failure) {
                                // 격리 토픽 장애 때 offset을 유지하고 관리자가 재개할 때까지 소비를 멈춥니다.
                                var listeners = registry.getIfAvailable();
                                if (listeners != null)
                                    listeners
                                            .getListenerContainers()
                                            .forEach(MessageListenerContainer::pause);
                                LoggerFactory.getLogger(CouponKafkaConfig.class)
                                        .error(
                                                "coupon dlt publish failed partition={}",
                                                record.partition());
                                throw failure;
                            }
                        },
                        new FixedBackOff(1000L, 3));
        h.addNotRetryableExceptions(InvalidCouponEventException.class);
        return h;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]>
            couponKafkaListenerContainerFactory(
                    ConsumerFactory<String, byte[]> consumerFactory,
                    DefaultErrorHandler couponErrorHandler,
                    @Value("${spring.kafka.listener.auto-startup:false}") boolean autoStartup) {
        var f = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        f.setConsumerFactory(consumerFactory);
        f.setConcurrency(1);
        f.setAutoStartup(autoStartup);
        f.setCommonErrorHandler(couponErrorHandler);
        f.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return f;
    }
}
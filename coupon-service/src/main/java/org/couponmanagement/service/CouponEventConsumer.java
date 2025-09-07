package org.couponmanagement.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.dto.UpdateCouponEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
@ConditionalOnProperty(name = "kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
public class CouponEventConsumer {
    private final UpdateCouponService updateCouponService;

    @KafkaListener(topics = "update-coupon-user", groupId = "coupon-service-group")
    public void listen(@Payload UpdateCouponEvent event,
                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                       @Header(KafkaHeaders.OFFSET) long offset,
                       Acknowledgment acknowledgment) {
        try {
            log.info("Received update coupon event: {} from topic: {}, partition: {}, offset: {}",
                    event, topic, partition, offset);

            updateCouponService.updateCouponUsageStatus(event);

            acknowledgment.acknowledge();

            log.info("Successfully processed update coupon event for userId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to process budget event: {} from topic: {}, partition: {}, offset: {}, error: {}",
                    event, topic, partition, offset, e.getMessage(), e);
            throw e;
        }
    }

}

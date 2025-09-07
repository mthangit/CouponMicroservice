package org.couponmanagement.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.couponmanagement.dto.RollBackBudgetEvent;
import org.couponmanagement.dto.UpdateCouponEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@ConditionalOnProperty(name = "kafka.producer.enabled", havingValue = "true", matchIfMissing = true)
@AllArgsConstructor
public class CouponEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "update-coupon-user";

    public void sendUpdateCouponEvent(UpdateCouponEvent event) {
        try {
            String key = event.getUserId().toString();

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(TOPIC, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent update coupon event for userId: {}, couponId: {} with offset: {}",
                            event.getUserId(), event.getCouponId(), result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send update coupon event for userId: {}, couponId: {} error: {}",
                            event.getUserId(), event.getCouponId(), ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("Error sending update coupon event for userId: {}, couponId: {} error: {}",
                    event.getUserId(), event.getCouponId(),  e.getMessage(), e);
            throw e;
        }
    }

    public void sendRollBackEvent(RollBackBudgetEvent event) {
        try {
            String key = event.getUserId().toString();

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(TOPIC, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent rollback budget event for userId: {}, couponId: {} with offset: {}",
                            event.getUserId(), event.getCouponId(), result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send rollback budget event for userId: {}, couponId: {}, error: {}",
                            event.getUserId(), event.getCouponId(), ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("Error sending rollback budget event for userId: {}, couponId: {} error: {}",
                    event.getUserId(), event.getCouponId(), e.getMessage(), e);
            throw e;
        }
    }


}

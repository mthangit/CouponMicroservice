package org.couponmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.dto.RollBackBudgetEvent;
import org.couponmanagement.dto.UpdateCouponEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "kafka.producer.enabled", havingValue = "false")
public class NoOpCouponEventProducer {
    public void sendUpdateCouponEvent(UpdateCouponEvent event) {
        log.info("Kafka disabled. Skipping event for userId: {}", event.getUserId());
    }

    public void sendRollBackEvent(RollBackBudgetEvent event) {
        log.info("Kafka disabled. Skipping rollback event for userId: {}", event.getUserId());
    }
}
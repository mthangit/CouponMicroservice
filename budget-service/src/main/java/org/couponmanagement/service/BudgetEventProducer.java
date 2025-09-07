package org.couponmanagement.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.dto.BudgetEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@AllArgsConstructor
public class BudgetEventProducer {
    private final KafkaTemplate<String, BudgetEvent> kafkaTemplate;

    private static final String TOPIC = "budget-usage";

    public void sendBudgetEvent(BudgetEvent event) {
        try {
            String key = event.getBudgetId().toString();

            CompletableFuture<SendResult<String, BudgetEvent>> future =
                    kafkaTemplate.send(TOPIC, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent budget event for transaction: {} with offset: {}",
                            event.getTransactionId(), result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send budget event for transaction: {}, error: {}",
                            event.getTransactionId(), ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("Error sending budget event for transaction: {}, error: {}",
                    event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }
}

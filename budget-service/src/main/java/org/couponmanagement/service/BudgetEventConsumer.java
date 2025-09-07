package org.couponmanagement.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.dto.BudgetEvent;
import org.couponmanagement.dto.RollBackBudgetEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class BudgetEventConsumer {
    private final BudgetUsageService budgetUsageService;

    @KafkaListener(topics = "budget-usage", groupId = "budget-service-group")
    public void listen(@Payload BudgetEvent event,
                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                      @Header(KafkaHeaders.OFFSET) long offset,
                      Acknowledgment acknowledgment) {
        try {
            log.info("Received budget event: {} from topic: {}, partition: {}, offset: {}",
                    event, topic, partition, offset);

            budgetUsageService.processBudgetUsage(event);

            acknowledgment.acknowledge();

            log.info("Successfully processed budget event for transaction: {}", event.getTransactionId());

        } catch (Exception e) {
            log.error("Failed to process budget event: {} from topic: {}, partition: {}, offset: {}, error: {}",
                     event, topic, partition, offset, e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "rollback-budget-usage", groupId = "budget-service-group")
    public void listenRollBack(@Payload RollBackBudgetEvent event,
                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                       @Header(KafkaHeaders.OFFSET) long offset,
                       Acknowledgment acknowledgment) {
        try {
            log.info("Received rollback budget event: {} from topic: {}, partition: {}, offset: {}",
                    event, topic, partition, offset);
            budgetUsageService.processRollbackBudgetUsage(event);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error rolling back budget usage for event {}: {}", event, e.getMessage(), e);

            acknowledgment.acknowledge();
        }
    }


}

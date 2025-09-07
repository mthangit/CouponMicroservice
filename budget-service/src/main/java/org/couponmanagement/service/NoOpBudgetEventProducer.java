package org.couponmanagement.service;

import org.couponmanagement.dto.BudgetEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "kafka.producer.enabled", havingValue = "false")
public class NoOpBudgetEventProducer {
    public void sendBudgetEvent(BudgetEvent event) {}
}

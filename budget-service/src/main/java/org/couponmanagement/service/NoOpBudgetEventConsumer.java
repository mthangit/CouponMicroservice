package org.couponmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "kafka.consumer.enabled", havingValue = "false")
public class NoOpBudgetEventConsumer {
}
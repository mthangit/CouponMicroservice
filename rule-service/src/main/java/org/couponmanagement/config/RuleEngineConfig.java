package org.couponmanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.engine.MinOrderAmountRuleHandler;
import org.couponmanagement.engine.RuleHandler;
import org.couponmanagement.engine.TimeRangeRuleHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

@Configuration
@Slf4j
public class RuleEngineConfig {

    @Value("${rule.engine.parallel.enabled:true}")
    private boolean parallelEnabled;

    @Bean
    @Primary
    public Map<String, RuleHandler> ruleHandlerMap() {
        log.info("Initializing rule handlers with parallel processing enabled: {}", parallelEnabled);
        
        return Map.of(
                "MIN_ORDER_AMOUNT", new MinOrderAmountRuleHandler(new ObjectMapper()),
                "DAILY_ACTIVE_TIME", new TimeRangeRuleHandler(new ObjectMapper())
        );
    }

    @Bean
    public boolean parallelProcessingEnabled() {
        return parallelEnabled;
    }
}

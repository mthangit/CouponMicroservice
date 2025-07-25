package org.couponmanagement.config;

import org.couponmanagement.engine.MinOrderAmountRuleHandler;
import org.couponmanagement.engine.RuleHandler;
import org.couponmanagement.engine.TimeRangeRuleHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RuleEngineConfig {
    
    @Bean
    public Map<String, RuleHandler> ruleHandlerMap(
            MinOrderAmountRuleHandler minAmountHandler,
            TimeRangeRuleHandler timeRangeHandler
    ) {
        return Map.of(
            "MIN_ORDER_AMOUNT", minAmountHandler,
            "DAILY_ACTIVE_TIME", timeRangeHandler
        );
    }
}

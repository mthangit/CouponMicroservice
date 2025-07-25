package org.couponmanagement.engine;

public interface RuleHandler {
    boolean check(String jsonConfig, RuleEvaluationContext context);
}

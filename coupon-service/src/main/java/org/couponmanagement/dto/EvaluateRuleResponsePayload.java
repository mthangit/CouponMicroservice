package org.couponmanagement.dto;

import java.util.List;

public record EvaluateRuleResponsePayload(
        String requestId,
        int userId,
        List<RuleCollectionResult> ruleCollectionResults
) {
}

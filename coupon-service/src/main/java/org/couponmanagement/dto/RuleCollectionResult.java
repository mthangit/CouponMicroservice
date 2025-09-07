package org.couponmanagement.dto;

public record RuleCollectionResult(
        int ruleCollectionId,
        boolean success,
        String errorMessage
) {
}

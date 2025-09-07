package org.couponmanagement.dto;

public record BudgetCheckResult(
        boolean success,
        BudgetErrorCode errorCode
) {
}

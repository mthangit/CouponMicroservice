package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RollBackBudgetEvent {
    private Integer budgetId;
    private Integer couponId;
    private Integer userId;
    private BigDecimal rollbackAmount;
    private RegisterStatus status;
}


package org.couponmanagement.dto;

import lombok.Data;
import org.couponmanagement.entity.RegisterStatus;

import java.math.BigDecimal;

@Data
public class RollBackBudgetEvent {
    private Integer budgetId;
    private Integer couponId;
    private Integer userId;
    private BigDecimal rollbackAmount;
    private RegisterStatus status;
}


package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetEvent {
    private String transactionId;
    private Integer budgetId;
    private Integer couponId;
    private Integer userId;
    private BigDecimal discountAmount;
    private LocalDateTime usageTime;
}
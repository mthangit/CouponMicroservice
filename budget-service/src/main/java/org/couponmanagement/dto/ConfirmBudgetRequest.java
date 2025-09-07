package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmBudgetRequest {
    private String requestId;
    private Integer userId;
    private Integer couponId;
    private Integer orderId;
    private Integer budgetId;
    private Integer reservedBudgetId;
    private BigDecimal discountAmount;
}

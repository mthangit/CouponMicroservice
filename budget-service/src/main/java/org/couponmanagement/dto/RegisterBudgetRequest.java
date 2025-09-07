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
public class RegisterBudgetRequest {
    private String requestId;
    private Long counponUserId;
    private Integer userId;
    private Integer couponId;
    private Integer budgetId;
    private BigDecimal discountAmount;
}

package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmBudgetResponse {
    private boolean success;
    private String message;
    private BudgetErrorCode errorCode;
}

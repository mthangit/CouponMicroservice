package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.couponmanagement.entity.RegisterStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterBudgetResponse {
    private boolean success;
    private String message;
    private RegisterStatus status;
    private BudgetErrorCode errorCode;
}

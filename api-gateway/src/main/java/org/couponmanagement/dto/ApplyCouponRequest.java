package org.couponmanagement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ApplyCouponRequest {
    @NotNull(message = "User ID is required")
    private Integer userId;

    @NotBlank(message = "Coupon code is required")
    private String couponCode;

    @NotNull(message = "Order amount is required")
    @Positive(message = "Order amount must be positive")
    private Double orderAmount;

    private String orderDate;
}

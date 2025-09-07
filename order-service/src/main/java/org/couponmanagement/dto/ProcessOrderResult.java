package org.couponmanagement.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ProcessOrderResult(
        boolean success,
        String errorMessage,
        String errorCode,
        Integer orderId,
        Integer userId,
        BigDecimal orderAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String couponCode,
        Integer couponId,
        LocalDateTime orderDate,
        String status
) {
}

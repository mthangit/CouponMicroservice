package org.couponmanagement.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CouponResult(
        boolean success,
        String errorMessage,
        String errorCode,
        Integer couponId,
        String couponCode,
        BigDecimal discountAmount
) {
}

package org.couponmanagement.dto;

import io.micrometer.observation.annotation.Observed;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Observed(name = "ProcessOrderRequest", contextualName = "ProcessOrderRequest.Builder")
public record ProcessOrderRequest(
        Integer userId,
        Double orderAmount,
        String couponCode,
        String requestId,
        LocalDateTime orderDate
) {
}

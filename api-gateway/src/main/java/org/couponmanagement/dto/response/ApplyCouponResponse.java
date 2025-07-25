package org.couponmanagement.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplyCouponResponse {
    private Boolean success;
    private String couponCode;
    private Double orderAmount;
    private Double discountAmount;
    private Double finalAmount;
    private String discountType;
    private Double discountValue;
    private String message;
}

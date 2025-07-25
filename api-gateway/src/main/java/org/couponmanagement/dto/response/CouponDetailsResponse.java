package org.couponmanagement.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CouponDetailsResponse {
    private Integer couponId;
    private String couponCode;
    private String discountType;
    private Double discountValue;
    private String validFrom;
    private String validTo;
    private Integer usageLimit;
    private Integer usedCount;
    private Boolean isActive;
    private String description;
}

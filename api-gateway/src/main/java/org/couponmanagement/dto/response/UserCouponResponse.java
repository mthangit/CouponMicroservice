package org.couponmanagement.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCouponResponse {
    private Integer couponId;
    private String couponCode;
    private String description;
    private String status;
    private String type;
    private Double value;
    private String startDate;
    private String endDate;
}

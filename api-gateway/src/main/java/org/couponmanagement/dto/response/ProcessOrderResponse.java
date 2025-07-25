package org.couponmanagement.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProcessOrderResponse {
    private Integer orderId;
    private Integer userId;
    private Double orderAmount;
    private Double discountAmount;
    private Double finalAmount;
    private String couponCode;
    private Integer couponId;
    private String orderDate;
    private String status;
}

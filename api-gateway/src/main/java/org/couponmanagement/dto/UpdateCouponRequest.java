package org.couponmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class UpdateCouponRequest {
    @NotBlank(message = "Coupon code is required")
    private String code;

    private String title;
    private String description;
    private Boolean isActive;
    private Map<String, Object> config;
    private Integer collectionKeyId;
    private String startDate;
    private String endDate;
}

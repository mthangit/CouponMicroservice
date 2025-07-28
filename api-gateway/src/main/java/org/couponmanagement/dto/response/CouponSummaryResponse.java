package org.couponmanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonRawValue;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CouponSummaryResponse {
    private Integer couponId;
    private String code;
    private String title;
    private String description;
    private String status;
    private String type;
    @JsonRawValue
    private String config;
    private Boolean isActive;
    private String startDate;
    private String endDate;
    private Integer collectionKeyId;
}

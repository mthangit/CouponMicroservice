package org.couponmanagement.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateCouponResponse {
    private Integer couponId;
    private String code;
    private String title;
    private String description;
    private String type;
    @JsonRawValue
    private String config;
    private Integer collectionKeyId;
    private String startDate;
    private String endDate;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
}

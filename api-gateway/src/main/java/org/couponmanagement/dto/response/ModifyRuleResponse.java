package org.couponmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModifyRuleResponse {
    private Integer ruleId;
    private String description;
    private Boolean isActive;
    private String ruleType;
    @JsonRawValue
    private String ruleConfiguration;
    private String createdAt;
    private String updatedAt;
}

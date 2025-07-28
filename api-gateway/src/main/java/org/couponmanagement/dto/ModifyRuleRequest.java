package org.couponmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
public class ModifyRuleRequest {
    @NotNull(message = "Rule ID is required")
    private Integer ruleId;
    private String requestId;
    private String description;
    private Boolean isActive;
    private String ruleType;
    private Map<String, Object> ruleConfig;
}


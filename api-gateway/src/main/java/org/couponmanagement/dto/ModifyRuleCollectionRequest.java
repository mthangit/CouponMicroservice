package org.couponmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ModifyRuleCollectionRequest {
    @NotNull(message = "Collection ID is required")
    private Integer collectionId;

    private String requestId;
    private String name;
    private Boolean isActive;

    private List<Integer> ruleIds;
}

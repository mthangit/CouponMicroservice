package org.couponmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ModifyRuleCollectionRequest {
    @NotNull(message = "Collection ID is required")
    private Integer collectionId;

    private String requestId;
    private String name;
    private String description;
    private Boolean isActive;
}

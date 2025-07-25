package org.couponmanagement.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuleCollectionResponse {
    private Integer collectionId;
    private String name;
    private String description;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;
    private List<RuleDetail> rules;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RuleDetail {
        private Integer ruleId;
        private String ruleName;
        private String ruleType;
        private String condition;
        private Boolean isActive;
    }
}

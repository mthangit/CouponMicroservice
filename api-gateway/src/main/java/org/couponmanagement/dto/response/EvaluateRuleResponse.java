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
public class EvaluateRuleResponse {
    private String requestId;
    private Boolean overallResult;
    private List<RuleCollectionResult> results;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RuleCollectionResult {
        private Integer ruleCollectionId;
        private Boolean isSuccess;
        private String errorMessage;
    }
}

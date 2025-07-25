package org.couponmanagement.service;

import lombok.Data;

import java.util.List;

@Data
public class EvaluateRuleCollectionsResult {

    private Double orderAmount;
    private Double discountAmount;
    private List<RuleResult> ruleResults;
    private Boolean success;


    public record RuleResult(
        Boolean success,
        String errorMessage
    ){}
}

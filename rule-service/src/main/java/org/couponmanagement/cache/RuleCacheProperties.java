package org.couponmanagement.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
@Validated
public class RuleCacheProperties{
    
    @NotBlank
    private String keyPrefix = "rule-service";

    @Min(1)
    private long defaultTtlSeconds = 172800;
    
    @Min(1)
    private long ruleCollectionTtlSeconds = 172800;
    
    @Min(1)
    private long ruleConfigTtlSeconds = 172800;

    public String getRuleCollectionKey(Integer collectionId) {
        return "rule-collection:" + collectionId;
    }
    
    public String getRuleConfigKey(Integer ruleId) {
        return "rule-config:" + ruleId;
    }
    
    public String getRuleCollectionWithRulesKey(Integer collectionId) {
        return "rule-collection-with-rules:" + collectionId;
    }
}

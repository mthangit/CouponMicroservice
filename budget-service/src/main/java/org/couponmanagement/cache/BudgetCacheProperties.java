package org.couponmanagement.cache;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
@Validated
public class BudgetCacheProperties {
    @NotBlank
    private String keyPrefix = "budget-service";

    public String getKeyBudgetById(Integer budgetId) {;
        return keyPrefix + ":budget:" + budgetId;
    }

    public String getKeyRegisteredBudget(Integer budgetId, Integer couponId, Integer userId) {
        return keyPrefix + ":registered:" + budgetId + ":" + couponId + ":" + userId;
    }

    public String getKeyBudgetUsage() {
        return keyPrefix + ":usage";
    }

    public String getKeyLockBudget(Integer budgetId) {
        return keyPrefix + ":lock:budget:" + budgetId;
    }

    @Min(1)
    private long registerTtlSeconds = 180;

    @Min(1)
    private long budgetTtlSeconds = 604800;
}

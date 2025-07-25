package org.couponmanagement.cache;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
@Validated

public class OrderCacheProperties {
    @NotBlank
    private String keyPrefix = "coupon-service";

}

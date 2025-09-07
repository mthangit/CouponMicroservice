package org.couponmanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "coupon")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "collection_key_id")
    private Integer collectionKeyId;

    @Column(name = "budget_id")
    private Integer budgetId;

    @Column(name = "code", unique = true, nullable = false, length = 30)
    private String code;

    @Column(name = "type", length = 30)
    private String type;

    @Column(name = "title", length = 50)
    private String title;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "discount_config", columnDefinition = "json", nullable = false)
    private String discountConfigJson;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiscountConfig {
        private BigDecimal value;
        private String type;
        @JsonProperty("max_discount")
        private BigDecimal maxDiscount;
    }

    @Transient
    public DiscountConfig getDiscountConfig() {
        if (discountConfigJson == null || discountConfigJson.trim().isEmpty()) {
            return new DiscountConfig();
        }

        try {
            return objectMapper.readValue(discountConfigJson, DiscountConfig.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse discount config JSON for coupon {}: {}", id, e.getMessage());
            return new DiscountConfig();
        }
    }

    @Transient
    public void setDiscountConfig(DiscountConfig config) {
        try {
            this.discountConfigJson = objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize discount config for coupon {}: {}", id, e.getMessage());
            this.discountConfigJson = "{}";
        }
    }

    @Transient
    public Map<String, Object> getDiscountConfigMap() {
        if (discountConfigJson == null || discountConfigJson.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(discountConfigJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse discount config JSON to Map for coupon {}: {}", id, e.getMessage());
            return new HashMap<>();
        }
    }

    @Transient
    public static Map<String, Object> getDiscountConfigMap(String discountConfigJson) {
        if (discountConfigJson == null || discountConfigJson.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(discountConfigJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    @Transient
    @PerformanceMonitor
    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        DiscountConfig config = getDiscountConfig();

        if (config.getValue() == null || config.getType() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal discountAmount = BigDecimal.ZERO;

        switch (config.getType().toUpperCase()) {
            case "PERCENTAGE":
                discountAmount = orderAmount.multiply(config.getValue().divide(BigDecimal.valueOf(100)));
                break;
            case "FIXED_AMOUNT":
                discountAmount = config.getValue();
                break;
            default:
                return BigDecimal.ZERO;
        }

        if (config.getMaxDiscount() != null && discountAmount.compareTo(config.getMaxDiscount()) > 0) {
            discountAmount = config.getMaxDiscount();
        }

        if (discountAmount.compareTo(orderAmount) > 0) {
            discountAmount = orderAmount;
        }

        return discountAmount;
    }

    @Transient
    public String getDiscountType() {
        DiscountConfig config = getDiscountConfig();
        return config.getType() != null ? config.getType().toUpperCase() : type != null ? type.toUpperCase() : "FIXED_AMOUNT";
    }

    @Transient
    public BigDecimal getDiscountValue() {
        DiscountConfig config = getDiscountConfig();
        return config.getValue();
    }

    @Transient
    public BigDecimal getMaxDiscount() {
        DiscountConfig config = getDiscountConfig();
        return config.getMaxDiscount();
    }

    @Transient
    public void setMaxDiscount(BigDecimal maxDiscount) {
        DiscountConfig config = getDiscountConfig();
        config.setMaxDiscount(maxDiscount);
        setDiscountConfig(config);
    }

    @Transient
    public void setDiscountValue(BigDecimal value) {
        DiscountConfig config = getDiscountConfig();
        config.setValue(value);
        setDiscountConfig(config);
    }

    @Transient
    public void setDiscountType(String type) {
        DiscountConfig config = getDiscountConfig();
        config.setType(type);
        setDiscountConfig(config);
    }
}

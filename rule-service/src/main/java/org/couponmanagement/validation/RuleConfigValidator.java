package org.couponmanagement.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
@Slf4j
public class RuleConfigValidator {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public record ValidationResult(boolean valid, String errorMessage) {

    public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }

    public ValidationResult validateRuleConfig(String ruleType, String configJson) {
        if (ruleType == null || ruleType.trim().isEmpty()) {
            return ValidationResult.failure("Rule type cannot be null or empty");
        }

        if (configJson == null || configJson.trim().isEmpty()) {
            return ValidationResult.failure("Rule configuration cannot be null or empty");
        }

        try {
            JsonNode configNode = objectMapper.readTree(configJson);

            return switch (ruleType.toUpperCase()) {
                case "MIN_ORDER_AMOUNT" -> validateMinOrderAmountConfig(configNode);
                case "DAILY_ACTIVE_TIME" -> validateDailyActiveTimeConfig(configNode);
                default -> {
                    log.warn("Unknown rule type: {}, skipping validation", ruleType);
                    yield ValidationResult.success();
                }
            };
        } catch (Exception e) {
            log.error("Error parsing rule configuration JSON: {}", e.getMessage());
            return ValidationResult.failure("Invalid JSON format in rule configuration: " + e.getMessage());
        }
    }

    private ValidationResult validateMinOrderAmountConfig(JsonNode configNode) {
        if (!configNode.has("type")) {
            return ValidationResult.failure("MIN_ORDER_AMOUNT config must have 'type' field");
        }

        String type = configNode.get("type").asText();
        if (!"MIN_ORDER_AMOUNT".equals(type)) {
            return ValidationResult.failure("Config type field must be 'MIN_ORDER_AMOUNT'");
        }

        if (!configNode.has("min_amount")) {
            return ValidationResult.failure("MIN_ORDER_AMOUNT config must have 'min_amount' field");
        }

        JsonNode minAmountNode = configNode.get("min_amount");
        if (!minAmountNode.isNumber()) {
            return ValidationResult.failure("min_amount must be a number");
        }

        double minAmount = minAmountNode.asDouble();
        if (minAmount <= 0) {
            return ValidationResult.failure("min_amount must be a positive number");
        }
        if (configNode.size() > 2) {
            return ValidationResult.failure("MIN_ORDER_AMOUNT config should only contain 'type' and 'min_amount' fields");
        }

        return ValidationResult.success();
    }

    private ValidationResult validateDailyActiveTimeConfig(JsonNode configNode) {
        if (!configNode.has("type")) {
            return ValidationResult.failure("DAILY_ACTIVE_TIME config must have 'type' field");
        }

        String type = configNode.get("type").asText();
        if (!"DAILY_ACTIVE_TIME".equals(type)) {
            return ValidationResult.failure("Config type field must be 'DAILY_ACTIVE_TIME'");
        }

        if (!configNode.has("start_time")) {
            return ValidationResult.failure("DAILY_ACTIVE_TIME config must have 'start_time' field");
        }

        if (!configNode.has("end_time")) {
            return ValidationResult.failure("DAILY_ACTIVE_TIME config must have 'end_time' field");
        }

        String startTimeStr = configNode.get("start_time").asText();
        String endTimeStr = configNode.get("end_time").asText();

        try {
            LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm:ss"));
            LocalTime endTime = LocalTime.parse(endTimeStr, DateTimeFormatter.ofPattern("HH:mm:ss"));

            if (!startTime.isBefore(endTime)) {
                return ValidationResult.failure("start_time must be before end_time");
            }
        } catch (DateTimeParseException e) {
            return ValidationResult.failure("Time fields must be in HH:mm:ss format (e.g., '12:00:00')");
        }

        if (configNode.size() > 3) {
            return ValidationResult.failure("DAILY_ACTIVE_TIME config should only contain 'type', 'start_time', and 'end_time' fields");
        }

        return ValidationResult.success();
    }
}

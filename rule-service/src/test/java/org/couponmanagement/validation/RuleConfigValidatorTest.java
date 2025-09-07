package org.couponmanagement.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class RuleConfigValidatorTest {

    private RuleConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RuleConfigValidator();
    }

    @Test
    void testValidMinOrderAmountConfig() {
        String ruleType = "MIN_ORDER_AMOUNT";
        String configJson = "{\"type\": \"MIN_ORDER_AMOUNT\", \"min_amount\": 3000000}";

        RuleConfigValidator.ValidationResult result = validator.validateRuleConfig(ruleType, configJson);

        assertTrue(result.valid());
        assertNull(result.errorMessage());
    }

    @Test
    void testInvalidMinOrderAmountConfig_MissingMinAmount() {
        String ruleType = "MIN_ORDER_AMOUNT";
        String configJson = "{\"type\": \"MIN_ORDER_AMOUNT\"}";

        RuleConfigValidator.ValidationResult result = validator.validateRuleConfig(ruleType, configJson);

        assertFalse(result.valid());
        assertEquals("MIN_ORDER_AMOUNT config must have 'min_amount' field", result.errorMessage());
    }

    @Test
    void testInvalidMinOrderAmountConfig_NegativeAmount() {
        String ruleType = "MIN_ORDER_AMOUNT";
        String configJson = "{\"type\": \"MIN_ORDER_AMOUNT\", \"min_amount\": -1000}";

        RuleConfigValidator.ValidationResult result = validator.validateRuleConfig(ruleType, configJson);

        assertFalse(result.valid());
        assertEquals("min_amount must be a positive number", result.errorMessage());
    }

    @Test
    void testValidDailyActiveTimeConfig() {
        String ruleType = "DAILY_ACTIVE_TIME";
        String configJson = "{\"type\": \"DAILY_ACTIVE_TIME\", \"start_time\": \"12:00:00\", \"end_time\": \"14:00:00\"}";

        RuleConfigValidator.ValidationResult result = validator.validateRuleConfig(ruleType, configJson);

        assertTrue(result.valid());
        assertNull(result.errorMessage());
    }

    @Test
    void testInvalidDailyActiveTimeConfig_MissingStartTime() {
        String ruleType = "DAILY_ACTIVE_TIME";
        String configJson = "{\"type\": \"DAILY_ACTIVE_TIME\", \"end_time\": \"14:00:00\"}";

        RuleConfigValidator.ValidationResult result = validator.validateRuleConfig(ruleType, configJson);

        assertFalse(result.valid());
        assertEquals("DAILY_ACTIVE_TIME config must have 'start_time' field", result.errorMessage());
    }

    @Test
    void testInvalidDailyActiveTimeConfig_InvalidTimeFormat() {
        String ruleType = "DAILY_ACTIVE_TIME";
        String configJson = "{\"type\": \"DAILY_ACTIVE_TIME\", \"start_time\": \"12:00\", \"end_time\": \"14:00:00\"}";

        RuleConfigValidator.ValidationResult result = validator.validateRuleConfig(ruleType, configJson);

        assertFalse(result.valid());
        assertEquals("Time fields must be in HH:mm:ss format (e.g., '12:00:00')", result.errorMessage());
    }

    @Test
    void testInvalidDailyActiveTimeConfig_StartTimeAfterEndTime() {
        String ruleType = "DAILY_ACTIVE_TIME";
        String configJson = "{\"type\": \"DAILY_ACTIVE_TIME\", \"start_time\": \"15:00:00\", \"end_time\": \"14:00:00\"}";

        RuleConfigValidator.ValidationResult result = validator.validateRuleConfig(ruleType, configJson);

        assertFalse(result.valid());
        assertEquals("start_time must be before end_time", result.errorMessage());
    }

    @Test
    void testInvalidJson() {
        String ruleType = "MIN_ORDER_AMOUNT";
        String configJson = "invalid json";

        RuleConfigValidator.ValidationResult result = validator.validateRuleConfig(ruleType, configJson);

        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("Invalid JSON format"));
    }

    @Test
    void testUnknownRuleType() {
        String ruleType = "UNKNOWN_TYPE";
        String configJson = "{\"type\": \"UNKNOWN_TYPE\", \"some_field\": \"value\"}";

        RuleConfigValidator.ValidationResult result = validator.validateRuleConfig(ruleType, configJson);

        // Unknown types should pass validation for extensibility
        assertTrue(result.valid());
    }
}

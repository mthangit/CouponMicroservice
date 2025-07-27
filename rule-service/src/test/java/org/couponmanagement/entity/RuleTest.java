package org.couponmanagement.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RuleTest {

    @Test
    void ruleConstructors_CreateValidRule() {
        // Test NoArgs constructor
        Rule rule = new Rule();
        assertNull(rule.getId());
        assertNull(rule.getType());
        assertNull(rule.getDescription());
        assertNull(rule.getRuleConfiguration());
        assertNull(rule.getCreatedAt());
        assertNull(rule.getUpdatedAt());
        assertNull(rule.getIsActive());

        // Test AllArgs constructor
        LocalDateTime now = LocalDateTime.now();
        Rule ruleWithAllArgs = new Rule(
                1,
                "MIN_ORDER_AMOUNT",
                "Minimum order amount rule",
                "{\"min_amount\": 50.0}",
                now,
                now,
                true
        );

        assertEquals(1, ruleWithAllArgs.getId());
        assertEquals("MIN_ORDER_AMOUNT", ruleWithAllArgs.getType());
        assertEquals("Minimum order amount rule", ruleWithAllArgs.getDescription());
        assertEquals("{\"min_amount\": 50.0}", ruleWithAllArgs.getRuleConfiguration());
        assertEquals(now, ruleWithAllArgs.getCreatedAt());
        assertEquals(now, ruleWithAllArgs.getUpdatedAt());
        assertTrue(ruleWithAllArgs.getIsActive());
    }

    @Test
    void settersAndGetters() {
        // Arrange
        Rule rule = new Rule();
        LocalDateTime now = LocalDateTime.now();

        // Act
        rule.setId(1);
        rule.setType("TIME_RANGE");
        rule.setDescription("Time range rule");
        rule.setRuleConfiguration("{\"start_time\": \"09:00\", \"end_time\": \"17:00\"}");
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rule.setIsActive(true);

        // Assert
        assertEquals(1, rule.getId());
        assertEquals("TIME_RANGE", rule.getType());
        assertEquals("Time range rule", rule.getDescription());
        assertEquals("{\"start_time\": \"09:00\", \"end_time\": \"17:00\"}", rule.getRuleConfiguration());
        assertEquals(now, rule.getCreatedAt());
        assertEquals(now, rule.getUpdatedAt());
        assertTrue(rule.getIsActive());
    }

    @Test
    void prePersist_SetsTimestamps() {
        // Arrange
        Rule rule = new Rule();
        LocalDateTime beforePersist = LocalDateTime.now();

        // Act
        rule.onCreate();

        // Assert
        assertNotNull(rule.getCreatedAt());
        assertNotNull(rule.getUpdatedAt());
        assertTrue(rule.getCreatedAt().isAfter(beforePersist.minusSeconds(1)));
        assertTrue(rule.getUpdatedAt().isAfter(beforePersist.minusSeconds(1)));
        assertEquals(rule.getCreatedAt(), rule.getUpdatedAt());
    }

    @Test
    void preUpdate_UpdatesTimestamp() {
        // Arrange
        Rule rule = new Rule();
        rule.onCreate();
        LocalDateTime originalCreatedAt = rule.getCreatedAt();
        LocalDateTime originalUpdatedAt = rule.getUpdatedAt();

        // Wait a bit to ensure timestamp difference
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        rule.onUpdate();

        // Assert
        assertEquals(originalCreatedAt, rule.getCreatedAt()); // Should not change
        assertTrue(rule.getUpdatedAt().isAfter(originalUpdatedAt)); // Should be updated
    }

    @Test
    void equalsAndHashCode() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        Rule rule1 = new Rule(
                1,
                "MIN_ORDER_AMOUNT",
                "Rule 1",
                "{\"min_amount\": 50.0}",
                now,
                now,
                true
        );

        Rule rule2 = new Rule(
                1,
                "MIN_ORDER_AMOUNT",
                "Rule 1",
                "{\"min_amount\": 50.0}",
                now,
                now,
                true
        );

        Rule rule3 = new Rule(
                2,
                "TIME_RANGE",
                "Rule 3",
                "{\"start_time\": \"09:00\"}",
                now,
                now,
                false
        );

        // Assert
        assertEquals(rule1, rule2);
        assertEquals(rule1.hashCode(), rule2.hashCode());
        assertNotEquals(rule1, rule3);
        assertNotEquals(rule1.hashCode(), rule3.hashCode());
    }

    @Test
    void toString_ContainsExpectedFields() {
        // Arrange
        Rule rule = new Rule();
        rule.setId(1);
        rule.setType("MIN_ORDER_AMOUNT");
        rule.setDescription("Test rule");
        rule.setRuleConfiguration("{\"min_amount\": 50.0}");
        rule.setIsActive(true);

        // Act
        String toString = rule.toString();

        // Assert
        assertTrue(toString.contains("Rule"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("type=MIN_ORDER_AMOUNT"));
        assertTrue(toString.contains("description=Test rule"));
        assertTrue(toString.contains("isActive=true"));
    }

    @Test
    void ruleWithDifferentTypes() {
        // Test MIN_ORDER_AMOUNT rule
        Rule minOrderRule = new Rule();
        minOrderRule.setType("MIN_ORDER_AMOUNT");
        minOrderRule.setRuleConfiguration("{\"min_amount\": 100.0}");
        minOrderRule.setIsActive(true);

        assertEquals("MIN_ORDER_AMOUNT", minOrderRule.getType());
        assertTrue(minOrderRule.getRuleConfiguration().contains("min_amount"));

        // Test TIME_RANGE rule
        Rule timeRangeRule = new Rule();
        timeRangeRule.setType("TIME_RANGE");
        timeRangeRule.setRuleConfiguration("{\"start_time\": \"09:00\", \"end_time\": \"17:00\"}");
        timeRangeRule.setIsActive(true);

        assertEquals("TIME_RANGE", timeRangeRule.getType());
        assertTrue(timeRangeRule.getRuleConfiguration().contains("start_time"));
        assertTrue(timeRangeRule.getRuleConfiguration().contains("end_time"));
    }

    @Test
    void ruleWithComplexConfiguration() {
        // Arrange
        Rule rule = new Rule();
        String complexConfig = "{" +
                "\"min_amount\": 50.0," +
                "\"max_amount\": 1000.0," +
                "\"currency\": \"USD\"," +
                "\"conditions\": [\"new_user\", \"first_order\"]" +
                "}";

        // Act
        rule.setRuleConfiguration(complexConfig);

        // Assert
        assertEquals(complexConfig, rule.getRuleConfiguration());
        assertTrue(rule.getRuleConfiguration().contains("min_amount"));
        assertTrue(rule.getRuleConfiguration().contains("conditions"));
    }
}

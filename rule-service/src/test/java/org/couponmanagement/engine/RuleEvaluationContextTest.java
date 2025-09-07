package org.couponmanagement.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RuleEvaluationContextTest {

    @Test
    void allArgsConstructor_CreatesValidContext() {
        // Arrange
        Double orderAmount = 100.0;
        LocalDateTime currentTime = LocalDateTime.now();
        Integer userId = 123;
        String orderDate = "2025-07-27T12:00:00";
        Double discountAmount = 10.0;

        // Act
        RuleEvaluationContext context = new RuleEvaluationContext(
                orderAmount,
                currentTime,
                userId,
                orderDate
        );

        // Assert
        assertEquals(orderAmount, context.getOrderAmount());
        assertEquals(currentTime, context.getCurrentTime());
        assertEquals(userId, context.getUserId());
        assertEquals(orderDate, context.getOrderDate());
    }

    @Test
    void twoArgsConstructor_CreatesValidContext() {
        // Arrange
        Double orderAmount = 100.0;
        LocalDateTime currentTime = LocalDateTime.now();

        // Act
        RuleEvaluationContext context = new RuleEvaluationContext(orderAmount, currentTime);

        // Assert
        assertEquals(orderAmount, context.getOrderAmount());
        assertEquals(currentTime, context.getCurrentTime());
        assertNull(context.getUserId());
        assertNull(context.getOrderDate());
    }

    @Test
    void threeArgsConstructor_CreatesValidContext() {
        // Arrange
        Double orderAmount = 100.0;
        LocalDateTime currentTime = LocalDateTime.now();
        Integer userId = 123;

        // Act
        RuleEvaluationContext context = new RuleEvaluationContext(orderAmount, currentTime, userId);

        // Assert
        assertEquals(orderAmount, context.getOrderAmount());
        assertEquals(currentTime, context.getCurrentTime());
        assertEquals(userId, context.getUserId());
        assertNull(context.getOrderDate());
    }

    @Test
    void getters_ReturnCorrectValues() {
        // Arrange
        Double orderAmount = 150.0;
        LocalDateTime currentTime = LocalDateTime.of(2025, 7, 27, 14, 30);
        Integer userId = 456;
        String orderDate = "2025-07-27T14:30:00";
        Double discountAmount = 15.0;

        RuleEvaluationContext context = new RuleEvaluationContext(
                orderAmount,
                currentTime,
                userId,
                orderDate
        );

        // Act & Assert
        assertEquals(150.0, context.getOrderAmount());
        assertEquals(LocalDateTime.of(2025, 7, 27, 14, 30), context.getCurrentTime());
        assertEquals(456, context.getUserId());
        assertEquals("2025-07-27T14:30:00", context.getOrderDate());
    }

    @Test
    void contextWithNullValues_HandlesCorrectly() {
        // Act
        RuleEvaluationContext context = new RuleEvaluationContext(
                null, // null orderAmount
                null, // null currentTime
                null, // null userId
                null // null orderDate
        );

        // Assert
        assertNull(context.getOrderAmount());
        assertNull(context.getCurrentTime());
        assertNull(context.getUserId());
        assertNull(context.getOrderDate());
    }

    @Test
    void contextWithZeroValues_HandlesCorrectly() {
        // Arrange
        Double orderAmount = 0.0;
        LocalDateTime currentTime = LocalDateTime.now();
        Integer userId = 0;
        String orderDate = "";
        Double discountAmount = 0.0;

        // Act
        RuleEvaluationContext context = new RuleEvaluationContext(
                orderAmount,
                currentTime,
                userId,
                orderDate
        );

        // Assert
        assertEquals(0.0, context.getOrderAmount());
        assertEquals(currentTime, context.getCurrentTime());
        assertEquals(0, context.getUserId());
        assertEquals("", context.getOrderDate());
    }

    @Test
    void contextToString_ContainsExpectedValues() {
        // Arrange
        RuleEvaluationContext context = new RuleEvaluationContext(
                100.0,
                LocalDateTime.of(2025, 7, 27, 12, 0),
                123,
                "2025-07-27T12:00:00"
        );

        // Act
        String toString = context.toString();

        // Assert
        assertTrue(toString.contains("RuleEvaluationContext"));
        assertTrue(toString.contains("orderAmount=100.0"));
        assertTrue(toString.contains("userId=123"));
        assertTrue(toString.contains("discountAmount=10.0"));
    }

    @Test
    void contextEquals_WorksCorrectly() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        RuleEvaluationContext context1 = new RuleEvaluationContext(
                100.0, now, 123, "2025-07-27"
        );

        RuleEvaluationContext context2 = new RuleEvaluationContext(
                100.0, now, 123, "2025-07-27"
        );

        RuleEvaluationContext context3 = new RuleEvaluationContext(
                200.0, now, 123, "2025-07-27"
        );

        // Assert
        assertEquals(context1, context2);
        assertNotEquals(context1, context3);
        assertEquals(context1.hashCode(), context2.hashCode());
    }
}

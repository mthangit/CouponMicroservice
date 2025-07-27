 package org.couponmanagement.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void calculateFinalAmount_WithDiscount() {
        // Arrange
        Order order = Order.builder()
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.valueOf(15.0))
                .build();

        // Act
        order.calculateFinalAmount();

        // Assert
        assertEquals(BigDecimal.valueOf(85.0), order.getFinalAmount());
    }

    @Test
    void calculateFinalAmount_WithoutDiscount() {
        // Arrange
        Order order = Order.builder()
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(null)
                .build();

        // Act
        order.calculateFinalAmount();

        // Assert
        assertEquals(BigDecimal.valueOf(100.0), order.getFinalAmount());
    }

    @Test
    void calculateFinalAmount_WithZeroDiscount() {
        // Arrange
        Order order = Order.builder()
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.ZERO)
                .build();

        // Act
        order.calculateFinalAmount();

        // Assert
        assertEquals(BigDecimal.valueOf(100.0), order.getFinalAmount());
    }

    @Test
    void orderBuilder_CreatesValidOrder() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        // Act
        Order order = Order.builder()
                .id(1)
                .userId(123)
                .orderAmount(BigDecimal.valueOf(200.0))
                .discountAmount(BigDecimal.valueOf(20.0))
                .finalAmount(BigDecimal.valueOf(180.0))
                .couponId(456)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Assert
        assertEquals(1, order.getId());
        assertEquals(123, order.getUserId());
        assertEquals(BigDecimal.valueOf(200.0), order.getOrderAmount());
        assertEquals(BigDecimal.valueOf(20.0), order.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(180.0), order.getFinalAmount());
        assertEquals(456, order.getCouponId());
        assertEquals(now, order.getCreatedAt());
        assertEquals(now, order.getUpdatedAt());
    }

    @Test
    void orderBuilder_DefaultTimestamps() {
        // Act
        Order order = Order.builder()
                .userId(123)
                .orderAmount(BigDecimal.valueOf(100.0))
                .finalAmount(BigDecimal.valueOf(100.0))
                .build();

        // Assert
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertTrue(order.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(order.getUpdatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void orderNoArgsConstructor_CreatesEmptyOrder() {
        // Act
        Order order = new Order();

        // Assert
        assertNull(order.getId());
        assertNull(order.getUserId());
        assertNull(order.getOrderAmount());
        assertNull(order.getDiscountAmount());
        assertNull(order.getFinalAmount());
        assertNull(order.getCouponId());
    }

    @Test
    void orderAllArgsConstructor_CreatesOrderWithAllFields() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        // Act
        Order order = new Order(
                1,
                123,
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(90.0),
                456,
                now,
                now
        );

        // Assert
        assertEquals(1, order.getId());
        assertEquals(123, order.getUserId());
        assertEquals(BigDecimal.valueOf(100.0), order.getOrderAmount());
        assertEquals(BigDecimal.valueOf(10.0), order.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(90.0), order.getFinalAmount());
        assertEquals(456, order.getCouponId());
        assertEquals(now, order.getCreatedAt());
        assertEquals(now, order.getUpdatedAt());
    }

    @Test
    void orderEqualsAndHashCode() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        Order order1 = Order.builder()
                .id(1)
                .userId(123)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.valueOf(10.0))
                .finalAmount(BigDecimal.valueOf(90.0))
                .couponId(456)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Order order2 = Order.builder()
                .id(1)
                .userId(123)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.valueOf(10.0))
                .finalAmount(BigDecimal.valueOf(90.0))
                .couponId(456)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Order order3 = Order.builder()
                .id(2)
                .userId(123)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.valueOf(10.0))
                .finalAmount(BigDecimal.valueOf(90.0))
                .couponId(456)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Assert
        assertEquals(order1, order2);
        assertEquals(order1.hashCode(), order2.hashCode());
        assertNotEquals(order1, order3);
        assertNotEquals(order1.hashCode(), order3.hashCode());
    }

    @Test
    void orderToString() {
        // Arrange
        Order order = Order.builder()
                .id(1)
                .userId(123)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.valueOf(10.0))
                .finalAmount(BigDecimal.valueOf(90.0))
                .couponId(456)
                .build();

        // Act
        String toString = order.toString();

        // Assert
        assertTrue(toString.contains("Order"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("userId=123"));
        assertTrue(toString.contains("orderAmount=100.0"));
        assertTrue(toString.contains("discountAmount=10.0"));
        assertTrue(toString.contains("finalAmount=90.0"));
        assertTrue(toString.contains("couponId=456"));
    }
}

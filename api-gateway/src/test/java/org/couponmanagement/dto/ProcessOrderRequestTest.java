package org.couponmanagement.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProcessOrderRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void processOrderRequest_ValidData_PassesValidation() {
        // Arrange
        ProcessOrderRequest request = new ProcessOrderRequest();
        request.setUserId(1);
        request.setOrderAmount(100.0);
        request.setCouponCode("DISCOUNT10");
        request.setOrderDate("2025-07-27T12:00:00");
        request.setRequestId("test-request-id");

        // Act
        Set<ConstraintViolation<ProcessOrderRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
        assertEquals(1, request.getUserId());
        assertEquals(100.0, request.getOrderAmount());
        assertEquals("DISCOUNT10", request.getCouponCode());
        assertEquals("2025-07-27T12:00:00", request.getOrderDate());
        assertEquals("test-request-id", request.getRequestId());
    }

    @Test
    void processOrderRequest_NullUserId_FailsValidation() {
        // Arrange
        ProcessOrderRequest request = new ProcessOrderRequest();
        request.setUserId(null);
        request.setOrderAmount(100.0);

        // Act
        Set<ConstraintViolation<ProcessOrderRequest>> violations = validator.validate(request);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("userId")));
    }

    @Test
    void processOrderRequest_NullOrderAmount_FailsValidation() {
        // Arrange
        ProcessOrderRequest request = new ProcessOrderRequest();
        request.setUserId(1);
        request.setOrderAmount(null);

        // Act
        Set<ConstraintViolation<ProcessOrderRequest>> violations = validator.validate(request);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("orderAmount")));
    }

    @Test
    void processOrderRequest_NegativeOrderAmount_FailsValidation() {
        // Arrange
        ProcessOrderRequest request = new ProcessOrderRequest();
        request.setUserId(1);
        request.setOrderAmount(-10.0);

        // Act
        Set<ConstraintViolation<ProcessOrderRequest>> violations = validator.validate(request);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("orderAmount")));
    }

    @Test
    void processOrderRequest_ZeroOrderAmount_FailsValidation() {
        // Arrange
        ProcessOrderRequest request = new ProcessOrderRequest();
        request.setUserId(1);
        request.setOrderAmount(0.0);

        // Act
        Set<ConstraintViolation<ProcessOrderRequest>> violations = validator.validate(request);

        // Assert
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("orderAmount")));
    }

    @Test
    void processOrderRequest_OptionalFields_PassesValidation() {
        // Arrange - Only required fields
        ProcessOrderRequest request = new ProcessOrderRequest();
        request.setUserId(1);
        request.setOrderAmount(100.0);
        // couponCode, orderDate, requestId are optional

        // Act
        Set<ConstraintViolation<ProcessOrderRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
        assertNull(request.getCouponCode());
        assertNull(request.getOrderDate());
        assertNull(request.getRequestId());
    }

    @Test
    void processOrderRequest_SettersAndGetters() {
        // Arrange
        ProcessOrderRequest request = new ProcessOrderRequest();

        // Act
        request.setUserId(123);
        request.setOrderAmount(250.75);
        request.setCouponCode("SAVE25");
        request.setOrderDate("2025-12-31T23:59:59");
        request.setRequestId("unique-request-123");

        // Assert
        assertEquals(123, request.getUserId());
        assertEquals(250.75, request.getOrderAmount());
        assertEquals("SAVE25", request.getCouponCode());
        assertEquals("2025-12-31T23:59:59", request.getOrderDate());
        assertEquals("unique-request-123", request.getRequestId());
    }

    @Test
    void processOrderRequest_ToString() {
        // Arrange
        ProcessOrderRequest request = new ProcessOrderRequest();
        request.setUserId(1);
        request.setOrderAmount(100.0);
        request.setCouponCode("TEST");

        // Act
        String toString = request.toString();

        // Assert
        assertTrue(toString.contains("ProcessOrderRequest"));
        assertTrue(toString.contains("userId=1"));
        assertTrue(toString.contains("orderAmount=100.0"));
        assertTrue(toString.contains("couponCode=TEST"));
    }

    @Test
    void processOrderRequest_EqualsAndHashCode() {
        // Arrange
        ProcessOrderRequest request1 = new ProcessOrderRequest();
        request1.setUserId(1);
        request1.setOrderAmount(100.0);
        request1.setCouponCode("TEST");

        ProcessOrderRequest request2 = new ProcessOrderRequest();
        request2.setUserId(1);
        request2.setOrderAmount(100.0);
        request2.setCouponCode("TEST");

        ProcessOrderRequest request3 = new ProcessOrderRequest();
        request3.setUserId(2);
        request3.setOrderAmount(100.0);
        request3.setCouponCode("TEST");

        // Assert
        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1, request3);
        assertNotEquals(request1.hashCode(), request3.hashCode());
    }
}

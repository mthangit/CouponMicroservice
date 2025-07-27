package org.couponmanagement.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApplyCouponRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void applyCouponRequest_ValidData_PassesValidation() {
        ApplyCouponRequest request = new ApplyCouponRequest();
        request.setUserId(1);
        request.setCouponCode("DISCOUNT10");
        request.setOrderAmount(100.0);
        request.setOrderDate("2025-07-27T12:00:00");

        Set<ConstraintViolation<ApplyCouponRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
        assertEquals(1, request.getUserId());
        assertEquals("DISCOUNT10", request.getCouponCode());
        assertEquals(100.0, request.getOrderAmount());
        assertEquals("2025-07-27T12:00:00", request.getOrderDate());
    }

    @Test
    void applyCouponRequest_NullUserId_FailsValidation() {
        ApplyCouponRequest request = new ApplyCouponRequest();
        request.setUserId(null);
        request.setCouponCode("DISCOUNT10");
        request.setOrderAmount(100.0);

        Set<ConstraintViolation<ApplyCouponRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("userId")));
    }

    @Test
    void applyCouponRequest_BlankCouponCode_FailsValidation() {
        ApplyCouponRequest request = new ApplyCouponRequest();
        request.setUserId(1);
        request.setCouponCode("   ");
        request.setOrderAmount(100.0);

        Set<ConstraintViolation<ApplyCouponRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("couponCode")));
    }

    @Test
    void applyCouponRequest_NullOrderAmount_FailsValidation() {
        ApplyCouponRequest request = new ApplyCouponRequest();
        request.setUserId(1);
        request.setCouponCode("DISCOUNT10");
        request.setOrderAmount(null);

        Set<ConstraintViolation<ApplyCouponRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("orderAmount")));
    }

    @Test
    void applyCouponRequest_NegativeOrderAmount_FailsValidation() {
        ApplyCouponRequest request = new ApplyCouponRequest();
        request.setUserId(1);
        request.setCouponCode("DISCOUNT10");
        request.setOrderAmount(-50.0);

        Set<ConstraintViolation<ApplyCouponRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("orderAmount")));
    }

    @Test
    void applyCouponRequest_OptionalOrderDate_PassesValidation() {
        ApplyCouponRequest request = new ApplyCouponRequest();
        request.setUserId(1);
        request.setCouponCode("DISCOUNT10");
        request.setOrderAmount(100.0);
        Set<ConstraintViolation<ApplyCouponRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
        assertNull(request.getOrderDate());
    }

    @Test
    void applyCouponRequest_SettersAndGetters() {
        ApplyCouponRequest request = new ApplyCouponRequest();

        request.setUserId(123);
        request.setCouponCode("SAVE20");
        request.setOrderAmount(300.50);
        request.setOrderDate("2025-08-15T14:30:00");

        assertEquals(123, request.getUserId());
        assertEquals("SAVE20", request.getCouponCode());
        assertEquals(300.50, request.getOrderAmount());
        assertEquals("2025-08-15T14:30:00", request.getOrderDate());
    }
}

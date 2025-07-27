package org.couponmanagement.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CouponApplicationResultTest {

    @Test
    void couponApplicationResultBuilder_Success() {
        // Act
        CouponApplicationResult result = CouponApplicationResult.builder()
                .success(true)
                .couponId(123)
                .couponCode("DISCOUNT10")
                .discountAmount(BigDecimal.valueOf(10.0))
                .build();

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(123, result.getCouponId());
        assertEquals("DISCOUNT10", result.getCouponCode());
        assertEquals(BigDecimal.valueOf(10.0), result.getDiscountAmount());
        assertNull(result.getErrorMessage());
    }

    @Test
    void couponApplicationResultBuilder_Failure() {
        // Act
        CouponApplicationResult result = CouponApplicationResult.builder()
                .success(false)
                .errorMessage("Coupon not found")
                .discountAmount(BigDecimal.ZERO)
                .build();

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Coupon not found", result.getErrorMessage());
        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
        assertNull(result.getCouponId());
        assertNull(result.getCouponCode());
    }

    @Test
    void couponApplicationResultEqualsAndHashCode() {
        // Arrange
        CouponApplicationResult result1 = CouponApplicationResult.builder()
                .success(true)
                .couponId(123)
                .couponCode("DISCOUNT10")
                .discountAmount(BigDecimal.valueOf(10.0))
                .build();

        CouponApplicationResult result2 = CouponApplicationResult.builder()
                .success(true)
                .couponId(123)
                .couponCode("DISCOUNT10")
                .discountAmount(BigDecimal.valueOf(10.0))
                .build();

        CouponApplicationResult result3 = CouponApplicationResult.builder()
                .success(false)
                .errorMessage("Failed")
                .discountAmount(BigDecimal.ZERO)
                .build();

        // Assert
        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result3);
        assertNotEquals(result1.hashCode(), result3.hashCode());
    }

    @Test
    void couponApplicationResultToString() {
        // Arrange
        CouponApplicationResult result = CouponApplicationResult.builder()
                .success(true)
                .couponId(123)
                .couponCode("DISCOUNT10")
                .discountAmount(BigDecimal.valueOf(10.0))
                .build();

        // Act
        String toString = result.toString();

        // Assert
        assertTrue(toString.contains("CouponApplicationResult"));
        assertTrue(toString.contains("success=true"));
        assertTrue(toString.contains("couponId=123"));
        assertTrue(toString.contains("couponCode=DISCOUNT10"));
        assertTrue(toString.contains("discountAmount=10.0"));
    }

    @Test
    void couponApplicationResultSetters() {
        // Arrange
        CouponApplicationResult result = new CouponApplicationResult();

        // Act
        result.setSuccess(true);
        result.setCouponId(456);
        result.setCouponCode("SAVE20");
        result.setDiscountAmount(BigDecimal.valueOf(20.0));
        result.setErrorMessage(null);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(456, result.getCouponId());
        assertEquals("SAVE20", result.getCouponCode());
        assertEquals(BigDecimal.valueOf(20.0), result.getDiscountAmount());
        assertNull(result.getErrorMessage());
    }
}

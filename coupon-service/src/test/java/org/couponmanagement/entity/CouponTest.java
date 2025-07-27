package org.couponmanagement.entity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CouponTest {

    @Test
    void couponBuilder_CreatesValidCoupon() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryDate = now.plusDays(30);

        // Act
        Coupon coupon = Coupon.builder()
                .id(1)
                .collectionKeyId(100)
                .code("DISCOUNT10")
                .type("PERCENTAGE")
                .title("10% Discount")
                .description("Get 10% off your order")
                .discountConfigJson("{\"type\":\"PERCENTAGE\",\"value\":10}")
                .expiryDate(expiryDate)
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Assert
        assertEquals(1, coupon.getId());
        assertEquals(100, coupon.getCollectionKeyId());
        assertEquals("DISCOUNT10", coupon.getCode());
        assertEquals("PERCENTAGE", coupon.getType());
        assertEquals("10% Discount", coupon.getTitle());
        assertEquals("Get 10% off your order", coupon.getDescription());
        assertEquals("{\"type\":\"PERCENTAGE\",\"value\":10}", coupon.getDiscountConfigJson());
        assertEquals(expiryDate, coupon.getExpiryDate());
        assertTrue(coupon.getIsActive());
        assertEquals(now, coupon.getCreatedAt());
        assertEquals(now, coupon.getUpdatedAt());
    }

    @Test
    void couponBuilder_DefaultValues() {
        // Act
        Coupon coupon = Coupon.builder()
                .code("TEST")
                .discountConfigJson("{}")
                .expiryDate(LocalDateTime.now())
                .build();

        // Assert
        assertTrue(coupon.getIsActive()); // Default should be true
        assertNotNull(coupon.getCreatedAt());
        assertNotNull(coupon.getUpdatedAt());
        assertTrue(coupon.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(coupon.getUpdatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void getDiscountConfig_ValidJson() {
        // Arrange
        Coupon coupon = Coupon.builder()
                .discountConfigJson("{\"type\":\"PERCENTAGE\",\"value\":15,\"max_discount\":50}")
                .build();

        // Act
        Coupon.DiscountConfig config = coupon.getDiscountConfig();

        // Assert
        assertNotNull(config);
        assertEquals("PERCENTAGE", config.getType());
        assertEquals(BigDecimal.valueOf(15), config.getValue());
        assertEquals(BigDecimal.valueOf(50), config.getMaxDiscount());
    }

    @Test
    void getDiscountConfig_InvalidJson() {
        // Arrange
        Coupon coupon = Coupon.builder()
                .id(1)
                .discountConfigJson("invalid-json")
                .build();

        // Act
        Coupon.DiscountConfig config = coupon.getDiscountConfig();

        // Assert
        assertNotNull(config);
        assertNull(config.getType());
        assertNull(config.getValue());
        assertNull(config.getMaxDiscount());
    }

    @Test
    void getDiscountConfig_NullJson() {
        // Arrange
        Coupon coupon = Coupon.builder()
                .discountConfigJson(null)
                .build();

        // Act
        Coupon.DiscountConfig config = coupon.getDiscountConfig();

        // Assert
        assertNotNull(config);
        assertNull(config.getType());
        assertNull(config.getValue());
        assertNull(config.getMaxDiscount());
    }

    @Test
    void getDiscountConfig_EmptyJson() {
        // Arrange
        Coupon coupon = Coupon.builder()
                .discountConfigJson("  ")
                .build();

        // Act
        Coupon.DiscountConfig config = coupon.getDiscountConfig();

        // Assert
        assertNotNull(config);
        assertNull(config.getType());
        assertNull(config.getValue());
        assertNull(config.getMaxDiscount());
    }

    @Test
    void setDiscountConfig_ValidConfig() {
        // Arrange
        Coupon coupon = new Coupon();
        Coupon.DiscountConfig config = Coupon.DiscountConfig.builder()
                .type("FIXED")
                .value(BigDecimal.valueOf(20))
                .maxDiscount(BigDecimal.valueOf(100))
                .build();

        // Act
        coupon.setDiscountConfig(config);

        // Assert
        assertNotNull(coupon.getDiscountConfigJson());
        assertTrue(coupon.getDiscountConfigJson().contains("FIXED"));
        assertTrue(coupon.getDiscountConfigJson().contains("20"));

        // Verify round-trip
        Coupon.DiscountConfig retrievedConfig = coupon.getDiscountConfig();
        assertEquals("FIXED", retrievedConfig.getType());
        assertEquals(BigDecimal.valueOf(20), retrievedConfig.getValue());
        assertEquals(BigDecimal.valueOf(100), retrievedConfig.getMaxDiscount());
    }

    @Test
    void couponEqualsAndHashCode() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon1 = Coupon.builder()
                .id(1)
                .code("DISCOUNT10")
                .title("10% Discount")
                .discountConfigJson("{\"type\":\"PERCENTAGE\",\"value\":10}")
                .expiryDate(now)
                .build();

        Coupon coupon2 = Coupon.builder()
                .id(1)
                .code("DISCOUNT10")
                .title("10% Discount")
                .discountConfigJson("{\"type\":\"PERCENTAGE\",\"value\":10}")
                .expiryDate(now)
                .build();

        Coupon coupon3 = Coupon.builder()
                .id(2)
                .code("DISCOUNT20")
                .title("20% Discount")
                .discountConfigJson("{\"type\":\"PERCENTAGE\",\"value\":20}")
                .expiryDate(now)
                .build();

        // Assert
        assertEquals(coupon1, coupon2);
        assertEquals(coupon1.hashCode(), coupon2.hashCode());
        assertNotEquals(coupon1, coupon3);
        assertNotEquals(coupon1.hashCode(), coupon3.hashCode());
    }

    @Test
    void couponToString() {
        // Arrange
        Coupon coupon = Coupon.builder()
                .id(1)
                .code("DISCOUNT10")
                .title("10% Discount")
                .type("PERCENTAGE")
                .build();

        // Act
        String toString = coupon.toString();

        // Assert
        assertTrue(toString.contains("Coupon"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("code=DISCOUNT10"));
        assertTrue(toString.contains("title=10% Discount"));
        assertTrue(toString.contains("type=PERCENTAGE"));
    }

    @Test
    void discountConfigBuilder() {
        // Act
        Coupon.DiscountConfig config = Coupon.DiscountConfig.builder()
                .type("PERCENTAGE")
                .value(BigDecimal.valueOf(15))
                .maxDiscount(BigDecimal.valueOf(50))
                .build();

        // Assert
        assertEquals("PERCENTAGE", config.getType());
        assertEquals(BigDecimal.valueOf(15), config.getValue());
        assertEquals(BigDecimal.valueOf(50), config.getMaxDiscount());
    }

    @Test
    void discountConfigNoArgsConstructor() {
        // Act
        Coupon.DiscountConfig config = new Coupon.DiscountConfig();

        // Assert
        assertNull(config.getType());
        assertNull(config.getValue());
        assertNull(config.getMaxDiscount());
    }

    @Test
    void discountConfigAllArgsConstructor() {
        // Act
        Coupon.DiscountConfig config = new Coupon.DiscountConfig(
                BigDecimal.valueOf(25),
                "FIXED",
                BigDecimal.valueOf(100)
        );

        // Assert
        assertEquals("FIXED", config.getType());
        assertEquals(BigDecimal.valueOf(25), config.getValue());
        assertEquals(BigDecimal.valueOf(100), config.getMaxDiscount());
    }
}

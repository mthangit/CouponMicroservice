package org.couponmanagement.entity;

import org.couponmanagement.entity.CouponUser.CouponUserStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CouponUserTest {

    @Test
    void couponUserBuilder_CreatesValidCouponUser() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryDate = now.plusDays(30);
        LocalDateTime usedAt = now.plusHours(1);

        // Act
        CouponUser couponUser = CouponUser.builder()
                .id(1L)
                .userId(123)
                .couponId(456)
                .claimedAt(now)
                .expiryDate(expiryDate)
                .status(CouponUserStatus.USED)
                .usedAt(usedAt)
                .build();

        // Assert
        assertEquals(1L, couponUser.getId());
        assertEquals(123, couponUser.getUserId());
        assertEquals(456, couponUser.getCouponId());
        assertEquals(now, couponUser.getClaimedAt());
        assertEquals(expiryDate, couponUser.getExpiryDate());
        assertEquals(CouponUserStatus.USED, couponUser.getStatus());
        assertEquals(usedAt, couponUser.getUsedAt());
    }

    @Test
    void couponUserBuilder_DefaultStatus() {
        // Act
        CouponUser couponUser = CouponUser.builder()
                .userId(123)
                .couponId(456)
                .build();

        // Assert
        assertEquals(CouponUserStatus.CLAIMED, couponUser.getStatus()); // Default should be CLAIMED
        assertNull(couponUser.getClaimedAt());
        assertNull(couponUser.getExpiryDate());
        assertNull(couponUser.getUsedAt());
    }

    @Test
    void couponUserNoArgsConstructor() {
        // Act
        CouponUser couponUser = new CouponUser();

        // Assert
        assertNull(couponUser.getId());
        assertNull(couponUser.getUserId());
        assertNull(couponUser.getCouponId());
        assertNull(couponUser.getClaimedAt());
        assertNull(couponUser.getExpiryDate());
        assertNull(couponUser.getStatus());
        assertNull(couponUser.getUsedAt());
    }


    @Test
    void couponUserStatusValues() {
        // Test all enum values exist
        assertEquals("CLAIMED", CouponUserStatus.CLAIMED.name());
        assertEquals("USED", CouponUserStatus.USED.name());

        // Test enum valueOf
        assertEquals(CouponUserStatus.CLAIMED, CouponUserStatus.valueOf("CLAIMED"));
        assertEquals(CouponUserStatus.USED, CouponUserStatus.valueOf("USED"));
    }

    @Test
    void couponUserEqualsAndHashCode() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        CouponUser couponUser1 = CouponUser.builder()
                .id(1L)
                .userId(123)
                .couponId(456)
                .claimedAt(now)
                .status(CouponUserStatus.CLAIMED)
                .build();

        CouponUser couponUser2 = CouponUser.builder()
                .id(1L)
                .userId(123)
                .couponId(456)
                .claimedAt(now)
                .status(CouponUserStatus.CLAIMED)
                .build();

        CouponUser couponUser3 = CouponUser.builder()
                .id(2L)
                .userId(123)
                .couponId(456)
                .claimedAt(now)
                .status(CouponUserStatus.CLAIMED)
                .build();

        // Assert
        assertEquals(couponUser1, couponUser2);
        assertEquals(couponUser1.hashCode(), couponUser2.hashCode());
        assertNotEquals(couponUser1, couponUser3);
        assertNotEquals(couponUser1.hashCode(), couponUser3.hashCode());
    }

    @Test
    void couponUserToString() {
        // Arrange
        CouponUser couponUser = CouponUser.builder()
                .id(1L)
                .userId(123)
                .couponId(456)
                .status(CouponUserStatus.CLAIMED)
                .build();

        // Act
        String toString = couponUser.toString();

        // Assert
        assertTrue(toString.contains("CouponUser"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("userId=123"));
        assertTrue(toString.contains("couponId=456"));
        assertTrue(toString.contains("status=CLAIMED"));
    }

    @Test
    void setters() {
        // Arrange
        CouponUser couponUser = new CouponUser();
        LocalDateTime now = LocalDateTime.now();

        // Act
        couponUser.setId(1L);
        couponUser.setUserId(123);
        couponUser.setCouponId(456);
        couponUser.setClaimedAt(now);
        couponUser.setStatus(CouponUserStatus.USED);

        // Assert
        assertEquals(1L, couponUser.getId());
        assertEquals(123, couponUser.getUserId());
        assertEquals(456, couponUser.getCouponId());
        assertEquals(now, couponUser.getClaimedAt());
        assertEquals(CouponUserStatus.USED, couponUser.getStatus());
    }

    @Test
    void builderWithDifferentStatuses() {
        // Test CLAIMED status
        CouponUser claimedCoupon = CouponUser.builder()
                .userId(1)
                .couponId(1)
                .status(CouponUserStatus.CLAIMED)
                .build();
        assertEquals(CouponUserStatus.CLAIMED, claimedCoupon.getStatus());

        // Test USED status
        CouponUser usedCoupon = CouponUser.builder()
                .userId(1)
                .couponId(1)
                .status(CouponUserStatus.USED)
                .usedAt(LocalDateTime.now())
                .build();
        assertEquals(CouponUserStatus.USED, usedCoupon.getStatus());
        assertNotNull(usedCoupon.getUsedAt());
    }
}

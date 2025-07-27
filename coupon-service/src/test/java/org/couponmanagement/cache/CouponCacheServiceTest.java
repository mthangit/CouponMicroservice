package org.couponmanagement.cache;

import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.dto.UserCouponClaimInfo;
import org.couponmanagement.dto.UserCouponIds;
import org.couponmanagement.entity.DiscountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponCacheServiceTest {

    @Mock
    private RedisCacheService cacheService;

    @Mock
    private CouponCacheProperties cacheProperties;

    @InjectMocks
    private CouponCacheService couponCacheService;

    private Integer userId;
    private Integer couponId;
    private String couponCode;
    private CouponDetail testCouponDetail;
    private UserCouponClaimInfo testUserCouponClaimInfo;
    private UserCouponIds testUserCouponIds;

    @BeforeEach
    void setUp() {
        userId = 1;
        couponId = 123;
        couponCode = "DISCOUNT10";

        testCouponDetail = CouponDetail.builder()
                .couponId(couponId)
                .couponCode(couponCode)
                .title("10% Discount")
                .description("Get 10% off your order")
                .type(String.valueOf(DiscountType.PERCENTAGE))
                .expiryDate(LocalDateTime.now().plusDays(30))
                .build();

        testUserCouponClaimInfo = UserCouponClaimInfo.builder()
                .userId(userId)
                .couponId(couponId)
                .claimedDate(LocalDateTime.now())
                .expiryDate(LocalDateTime.now().plusDays(30))
                .build();

        Map<Integer, UserCouponClaimInfo> userCouponInfoMap = Map.of(couponId, testUserCouponClaimInfo);
        testUserCouponIds = UserCouponIds.of(userCouponInfoMap);
    }

    @Test
    void cacheUserCouponIds_Success() {
        // Arrange
        String expectedKey = "user-coupons:1";
        long expectedTtl = 300L;

        when(cacheProperties.getUserCouponIdsKey(userId)).thenReturn(expectedKey);
        when(cacheProperties.getUserCouponsTtlSeconds()).thenReturn(expectedTtl);

        // Act
        couponCacheService.cacheUserCouponIds(userId, testUserCouponIds);

        // Assert
        verify(cacheProperties).getUserCouponIdsKey(userId);
        verify(cacheProperties).getUserCouponsTtlSeconds();
        verify(cacheService).put(expectedKey, testUserCouponIds, expectedTtl);
    }

    @Test
    void getCachedUserCouponIds_CacheHit() {
        // Arrange
        String expectedKey = "user-coupons:1";

        when(cacheProperties.getUserCouponIdsKey(userId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, UserCouponIds.class)).thenReturn(Optional.of(testUserCouponIds));

        // Act
        Optional<UserCouponIds> result = couponCacheService.getCachedUserCouponIds(userId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testUserCouponIds, result.get());
        verify(cacheProperties).getUserCouponIdsKey(userId);
        verify(cacheService).get(expectedKey, UserCouponIds.class);
    }

    @Test
    void getCachedUserCouponIds_CacheMiss() {
        // Arrange
        String expectedKey = "user-coupons:1";

        when(cacheProperties.getUserCouponIdsKey(userId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, UserCouponIds.class)).thenReturn(Optional.empty());

        // Act
        Optional<UserCouponIds> result = couponCacheService.getCachedUserCouponIds(userId);

        // Assert
        assertFalse(result.isPresent());
        verify(cacheProperties).getUserCouponIdsKey(userId);
        verify(cacheService).get(expectedKey, UserCouponIds.class);
    }

    @Test
    void cacheCouponDetail_Success() {
        // Arrange
        String expectedKey = "coupon-detail:123";
        long expectedTtl = 600L;

        when(cacheProperties.getCouponDetailKey(couponId)).thenReturn(expectedKey);
        when(cacheProperties.getCouponDetailTtlSeconds()).thenReturn(expectedTtl);

        // Act
        couponCacheService.cacheCouponDetail(couponId, testCouponDetail);

        // Assert
        verify(cacheProperties).getCouponDetailKey(couponId);
        verify(cacheProperties).getCouponDetailTtlSeconds();
        verify(cacheService).put(expectedKey, testCouponDetail, expectedTtl);
    }

    @Test
    void getCachedCouponDetail_CacheHit() {
        // Arrange
        String expectedKey = "coupon-detail:123";

        when(cacheProperties.getCouponDetailKey(couponId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, CouponDetail.class)).thenReturn(Optional.of(testCouponDetail));

        // Act
        Optional<CouponDetail> result = couponCacheService.getCachedCouponDetail(couponId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testCouponDetail, result.get());
        verify(cacheProperties).getCouponDetailKey(couponId);
        verify(cacheService).get(expectedKey, CouponDetail.class);
    }

    @Test
    void getCachedCouponDetail_CacheMiss() {
        // Arrange
        String expectedKey = "coupon-detail:123";

        when(cacheProperties.getCouponDetailKey(couponId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, CouponDetail.class)).thenReturn(Optional.empty());

        // Act
        Optional<CouponDetail> result = couponCacheService.getCachedCouponDetail(couponId);

        // Assert
        assertFalse(result.isPresent());
        verify(cacheProperties).getCouponDetailKey(couponId);
        verify(cacheService).get(expectedKey, CouponDetail.class);
    }

    @Test
    void getCachedCouponDetailsBatch_EmptyList() {
        // Act
        Map<Integer, CouponDetail> result = couponCacheService.getCachedCouponDetailsBatch(Collections.emptyList());

        // Assert
        assertTrue(result.isEmpty());
        verify(cacheService, never()).get(any(), any());
    }

    @Test
    void getCachedCouponDetailsBatch_MixedHitsAndMisses() {
        // Arrange
        Integer couponId1 = 123;
        Integer couponId2 = 456;
        List<Integer> couponIds = List.of(couponId1, couponId2);

        CouponDetail couponDetail1 = CouponDetail.builder()
                .couponId(couponId1)
                .couponCode("COUPON1")
                .build();

        String key1 = "coupon-detail:123";
        String key2 = "coupon-detail:456";

        when(cacheProperties.getCouponDetailKey(couponId1)).thenReturn(key1);
        when(cacheProperties.getCouponDetailKey(couponId2)).thenReturn(key2);
        when(cacheService.get(key1, CouponDetail.class)).thenReturn(Optional.of(couponDetail1));
        when(cacheService.get(key2, CouponDetail.class)).thenReturn(Optional.empty());

        // Act
        Map<Integer, CouponDetail> result = couponCacheService.getCachedCouponDetailsBatch(couponIds);

        // Assert
        assertEquals(1, result.size());
        assertTrue(result.containsKey(couponId1));
        assertFalse(result.containsKey(couponId2));
        assertEquals(couponDetail1, result.get(couponId1));

        verify(cacheProperties).getCouponDetailKey(couponId1);
        verify(cacheProperties).getCouponDetailKey(couponId2);
        verify(cacheService).get(key1, CouponDetail.class);
        verify(cacheService).get(key2, CouponDetail.class);
    }

    @Test
    void getCachedCouponDetailsBatch_AllHits() {
        // Arrange
        Integer couponId1 = 123;
        Integer couponId2 = 456;
        List<Integer> couponIds = List.of(couponId1, couponId2);

        CouponDetail couponDetail1 = CouponDetail.builder().couponId(couponId1).couponCode("COUPON1").build();
        CouponDetail couponDetail2 = CouponDetail.builder().couponId(couponId2).couponCode("COUPON2").build();

        String key1 = "coupon-detail:123";
        String key2 = "coupon-detail:456";

        when(cacheProperties.getCouponDetailKey(couponId1)).thenReturn(key1);
        when(cacheProperties.getCouponDetailKey(couponId2)).thenReturn(key2);
        when(cacheService.get(key1, CouponDetail.class)).thenReturn(Optional.of(couponDetail1));
        when(cacheService.get(key2, CouponDetail.class)).thenReturn(Optional.of(couponDetail2));

        // Act
        Map<Integer, CouponDetail> result = couponCacheService.getCachedCouponDetailsBatch(couponIds);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsKey(couponId1));
        assertTrue(result.containsKey(couponId2));
        assertEquals(couponDetail1, result.get(couponId1));
        assertEquals(couponDetail2, result.get(couponId2));
    }

    @Test
    void invalidateUserCache_CacheExists() {
        // Arrange
        String expectedKey = "user-coupons:1";
        long expectedTtl = 300L;

        when(cacheProperties.getUserCouponIdsKey(userId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, UserCouponIds.class)).thenReturn(Optional.of(testUserCouponIds));
        when(cacheProperties.getUserCouponsTtlSeconds()).thenReturn(expectedTtl);

        // Act
        couponCacheService.invalidateUserCache(userId, couponId);

        // Assert
        verify(cacheService).get(expectedKey, UserCouponIds.class);
        verify(cacheService).put(eq(expectedKey), any(UserCouponIds.class), eq(expectedTtl));
    }

    @Test
    void invalidateUserCache_CacheNotExists() {
        // Arrange
        String expectedKey = "user-coupons:1";

        when(cacheProperties.getUserCouponIdsKey(userId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, UserCouponIds.class)).thenReturn(Optional.empty());

        // Act
        couponCacheService.invalidateUserCache(userId, couponId);

        // Assert
        verify(cacheService).get(expectedKey, UserCouponIds.class);
        verify(cacheService, never()).put(any(), any(), anyLong());
    }

    @Test
    void cacheCouponCodeMapping_Success() {
        // Arrange
        String expectedKey = "coupon-info:DISCOUNT10";
        long expectedTtl = 600L;

        when(cacheProperties.getCouponInfoKey(couponCode)).thenReturn(expectedKey);
        when(cacheProperties.getCouponInfoTtlSeconds()).thenReturn(expectedTtl);

        // Act
        couponCacheService.cacheCouponCodeMapping(couponCode, couponId);

        // Assert
        verify(cacheProperties).getCouponInfoKey(couponCode);
        verify(cacheProperties).getCouponInfoTtlSeconds();
        verify(cacheService).put(expectedKey, couponId, expectedTtl);
    }

    @Test
    void getCouponIdByCode_CacheHit() {
        // Arrange
        String expectedKey = "coupon-info:DISCOUNT10";

        when(cacheProperties.getCouponInfoKey(couponCode)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, Integer.class)).thenReturn(Optional.of(couponId));

        // Act
        Optional<Integer> result = couponCacheService.getCouponIdByCode(couponCode);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(couponId, result.get());
        verify(cacheProperties).getCouponInfoKey(couponCode);
        verify(cacheService).get(expectedKey, Integer.class);
    }

    @Test
    void getCouponIdByCode_CacheMiss() {
        // Arrange
        String expectedKey = "coupon-info:DISCOUNT10";

        when(cacheProperties.getCouponInfoKey(couponCode)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, Integer.class)).thenReturn(Optional.empty());

        // Act
        Optional<Integer> result = couponCacheService.getCouponIdByCode(couponCode);

        // Assert
        assertFalse(result.isPresent());
        verify(cacheProperties).getCouponInfoKey(couponCode);
        verify(cacheService).get(expectedKey, Integer.class);
    }
}

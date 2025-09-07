package org.couponmanagement.service;

import org.couponmanagement.cache.CouponCacheService;
import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.dto.UserCouponClaimInfo;
import org.couponmanagement.dto.UserCouponIds;
import org.couponmanagement.entity.Coupon;
import org.couponmanagement.entity.CouponUser;
import org.couponmanagement.entity.CouponUser.CouponUserStatus;
import org.couponmanagement.entity.DiscountType;
import org.couponmanagement.grpc.client.GrpcClientFactory;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.repository.CouponRepository;
import org.couponmanagement.repository.CouponUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponUserRepository couponUserRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private RequestValidator validator;

    @Mock
    private CouponCacheService couponCacheService;

    @Mock
    private Executor couponEvaluationExecutor;

    @Mock
    private GrpcClientFactory grpcClientFactory;

    @InjectMocks
    private CouponService couponService;

    private Integer userId;
    private Coupon testCoupon;
    private CouponUser testCouponUser;
    private CouponDetail testCouponDetail;
    private UserCouponClaimInfo testUserCouponClaimInfo;

    @BeforeEach
    void setUp() {
        userId = 1;

        testCoupon = Coupon.builder()
                .id(1)
                .code("DISCOUNT10")
                .title("10% Discount")
                .description("Get 10% off your order")
                .discountConfigJson("{\"type\":\"PERCENTAGE\",\"value\":10}")
                .expiryDate(LocalDateTime.now().plusDays(30))
                .build();

        testCouponUser = CouponUser.builder()
                .id(1L)
                .userId(userId)
                .couponId(testCoupon.getId())
                .claimedAt(LocalDateTime.now())
                .expiryDate(LocalDateTime.now().plusDays(30))
                .status(CouponUserStatus.CLAIMED)
                .build();

        testCouponDetail = CouponDetail.builder()
                .couponId(testCoupon.getId())
                .couponCode(testCoupon.getCode())
                .title(testCoupon.getTitle())
                .description(testCoupon.getDescription())
                .type(String.valueOf(DiscountType.PERCENTAGE))
                .expiryDate(testCoupon.getExpiryDate())
                .build();

        testUserCouponClaimInfo = UserCouponClaimInfo.builder()
                .userId(userId)
                .couponId(testCoupon.getId())
                .claimedDate(LocalDateTime.now())
                .expiryDate(LocalDateTime.now().plusDays(30))
                .build();
    }

    @Test
    void getUserCouponsWithPagination_CacheHit_Success() {
        // Arrange
        int page = 0;
        int size = 10;

        Map<Integer, UserCouponClaimInfo> userCouponInfoMap = Map.of(testCoupon.getId(), testUserCouponClaimInfo);
        UserCouponIds cachedUserCouponIds = UserCouponIds.of(userCouponInfoMap);
        List<Integer> couponIds = cachedUserCouponIds.getCouponIds();
        Map<Integer, CouponDetail> couponDetailMap = Map.of(testCoupon.getId(), testCouponDetail);

        doNothing().when(validator).validateUserId(userId);
        when(couponCacheService.getCachedUserCouponIds(userId))
                .thenReturn(Optional.of(cachedUserCouponIds));
        when(couponCacheService.getCachedCouponDetailsBatch(couponIds))
                .thenReturn(couponDetailMap);

        CouponService.UserCouponsResult result = couponService.getUserCouponsWithPagination(userId, page, size);

        assertNotNull(result);
        assertEquals(1, result.totalCount());
        assertEquals(page, result.page());
        assertEquals(size, result.size());
        assertEquals(1, result.userCouponClaimInfos().size());
        assertEquals(testCoupon.getId(), result.userCouponClaimInfos().getFirst().getCouponId());
        assertTrue(result.couponDetailMap().containsKey(testCoupon.getId()));

        verify(validator).validateUserId(userId);
        verify(couponCacheService).getCachedUserCouponIds(userId);
        verify(couponCacheService).getCachedCouponDetailsBatch(couponIds);
    }

    @Test
    void getUserCouponsWithPagination_CacheMiss_LoadFromDatabase() {
        // Arrange
        int page = 0;
        int size = 10;
        PageRequest pageRequest = PageRequest.of(page, size);

        List<CouponUser> couponUsers = List.of(testCouponUser);
        Page<CouponUser> couponUserPage = new PageImpl<>(couponUsers, pageRequest, 1);
        List<Coupon> coupons = List.of(testCoupon);

        doNothing().when(validator).validateUserId(userId);
        when(couponCacheService.getCachedUserCouponIds(userId))
                .thenReturn(Optional.empty());
        when(couponRepository.findAllById(List.of(testCoupon.getId())))
                .thenReturn(coupons);

        CouponService.UserCouponsResult result = couponService.getUserCouponsWithPagination(userId, page, size);

        assertNotNull(result);
        assertEquals(1, result.totalCount());
        assertEquals(1, result.userCouponClaimInfos().size());

        verify(validator).validateUserId(userId);
        verify(couponCacheService).getCachedUserCouponIds(userId);
        verify(couponRepository).findAllById(List.of(testCoupon.getId()));
    }

    @Test
    void getUserCouponsWithPagination_ValidationException() {
        doThrow(new IllegalArgumentException("Invalid user ID"))
                .when(validator).validateUserId(userId);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                couponService.getUserCouponsWithPagination(userId, 0, 10));

        assertTrue(exception.getMessage().contains("Failed to get user coupons"));
        verify(validator).validateUserId(userId);
        verify(couponCacheService, never()).getCachedUserCouponIds(any());
    }

    @Test
    void applyCouponManual_Success() {
        // Arrange
        String couponCode = "LOYALTY20";
        BigDecimal orderAmount = BigDecimal.valueOf(10000000.0);
        LocalDateTime orderDate = LocalDateTime.now();

        doNothing().when(validator).validateUserId(userId);
        doNothing().when(validator).validateCouponCode(couponCode);
        doNothing().when(validator).validateOrderAmount(orderAmount.doubleValue());

        // Act
        CouponApplicationResult result = couponService.applyCouponManual(userId, couponCode, orderAmount, orderDate);

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(testCoupon.getId(), result.getCouponId());
        assertEquals(couponCode, result.getCouponCode());

        verify(validator).validateUserId(userId);
        verify(validator).validateCouponCode(couponCode);
        verify(validator).validateOrderAmount(orderAmount.doubleValue());
        verify(couponRepository).findByCodeIgnoreCase(couponCode);
    }

    @Test
    void applyCouponManual_CouponNotFound() {
        // Arrange
        String couponCode = "INVALID_CODE";
        BigDecimal orderAmount = BigDecimal.valueOf(100.0);
        LocalDateTime orderDate = LocalDateTime.now();

        doNothing().when(validator).validateUserId(userId);
        doNothing().when(validator).validateCouponCode(couponCode);
        doNothing().when(validator).validateOrderAmount(orderAmount.doubleValue());

        when(couponRepository.findByCodeIgnoreCase(couponCode))
                .thenReturn(Optional.empty());

        // Act
        CouponApplicationResult result = couponService.applyCouponManual(userId, couponCode, orderAmount, orderDate);

        // Assert
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("Coupon not found", result.getErrorMessage());

        verify(couponRepository).findByCodeIgnoreCase(couponCode);
    }

    @Test
    void applyCouponManual_CouponNotOwnedByUser() {
        // Arrange
        String couponCode = "DISCOUNT10";
        BigDecimal orderAmount = BigDecimal.valueOf(100.0);
        LocalDateTime orderDate = LocalDateTime.now();

        doNothing().when(validator).validateUserId(userId);
        doNothing().when(validator).validateCouponCode(couponCode);
        doNothing().when(validator).validateOrderAmount(orderAmount.doubleValue());

        when(couponRepository.findByCodeIgnoreCase(couponCode))
                .thenReturn(Optional.of(testCoupon));

        // Act
        CouponApplicationResult result = couponService.applyCouponManual(userId, couponCode, orderAmount, orderDate);

        // Assert
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("Coupon not found for user", result.getErrorMessage());

        verify(couponRepository).findByCodeIgnoreCase(couponCode);
    }

//    @Test
//    void applyCouponAuto_Success() {
//        // Arrange
//        BigDecimal orderAmount = BigDecimal.valueOf(100.0);
//        LocalDateTime orderDate = LocalDateTime.now();
//
//        List<CouponUser> availableCoupons = List.of(testCouponUser);
//
//        doNothing().when(validator).validateUserId(userId);
//        doNothing().when(validator).validateOrderAmount(orderAmount.doubleValue());
//
//        // Mock the actual method used in applyCouponAuto
//        when(couponCacheService.getCachedUserCouponIds(userId))
//                .thenReturn(Optional.empty());
//        when(couponUserRepository.findAvailableCouponsByUserId(userId))
//                .thenReturn(availableCoupons);
//
//        // Mock async executor to run synchronously for testing
//        doAnswer(invocation -> {
//            Runnable task = invocation.getArgument(0);
//            task.run();
//            return null;
//        }).when(couponEvaluationExecutor).execute(any(Runnable.class));
//
//        // Act
//        CouponApplicationResult result = couponService.applyCouponAuto(userId, orderAmount, orderDate);
//
//        // Assert
//        assertNotNull(result);
//        // Note: Actual success depends on rule evaluation which would need more detailed mocking
//
//        verify(validator).validateUserId(userId);
//        verify(validator).validateOrderAmount(orderAmount.doubleValue());
//        verify(couponCacheService).getCachedUserCouponIds(userId);
//        verify(couponUserRepository).findAvailableCouponsByUserId(userId);
//    }

    @Test
    void applyCouponManual_ValidationFailure() {
        // Arrange
        String couponCode = "INVALID";
        BigDecimal orderAmount = BigDecimal.valueOf(-10.0);
        LocalDateTime orderDate = LocalDateTime.now();

        doThrow(new IllegalArgumentException("Invalid order amount"))
                .when(validator).validateOrderAmount(orderAmount.doubleValue());

        // Act
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                couponService.applyCouponManual(userId, couponCode, orderAmount, orderDate));

        // Assert
        assertTrue(exception.getMessage().contains("Failed to apply coupon manually"));
        verify(validator).validateUserId(userId);
        verify(validator).validateCouponCode(couponCode);
        verify(validator).validateOrderAmount(orderAmount.doubleValue());
        verify(couponRepository, never()).findByCodeIgnoreCase(any());
    }

    @Test
    @DisplayName("getUserCouponsWithPagination should only return active coupons")
    void getUserCouponsWithPagination_ShouldOnlyReturnActiveCoupons() {
        // Given
        Integer userId = 1;
        int page = 0;
        int size = 10;

        // Create test coupons
        Coupon activeCoupon = new Coupon();
        activeCoupon.setId(1);
        activeCoupon.setCode("ACTIVE001");
        activeCoupon.setIsActive(true);
        activeCoupon.setCreatedAt(LocalDateTime.now());
        activeCoupon.setExpiryDate(LocalDateTime.now().plusDays(30));

        Coupon inactiveCoupon = new Coupon();
        inactiveCoupon.setId(2);
        inactiveCoupon.setCode("INACTIVE001");
        inactiveCoupon.setIsActive(false);
        inactiveCoupon.setCreatedAt(LocalDateTime.now());
        inactiveCoupon.setExpiryDate(LocalDateTime.now().plusDays(30));

        // Create CouponUser entities
        CouponUser activeCouponUser = new CouponUser();
        activeCouponUser.setUserId(userId);
        activeCouponUser.setCouponId(1);
        activeCouponUser.setCoupon(activeCoupon);
        activeCouponUser.setCreatedAt(LocalDateTime.now());
        activeCouponUser.setExpiryDate(LocalDateTime.now().plusDays(30));

        CouponUser inactiveCouponUser = new CouponUser();
        inactiveCouponUser.setUserId(userId);
        inactiveCouponUser.setCouponId(2);
        inactiveCouponUser.setCoupon(inactiveCoupon);
        inactiveCouponUser.setCreatedAt(LocalDateTime.now());
        inactiveCouponUser.setExpiryDate(LocalDateTime.now().plusDays(30));

        // Mock repository to return both active and inactive coupons
        when(couponUserRepository.findActiveCouponsByUserId(userId))
                .thenReturn(List.of(activeCouponUser));

        // Mock cache to return empty
        when(couponCacheService.getCachedUserCouponIds(userId))
                .thenReturn(Optional.empty());

        // When
        CouponService.UserCouponsResult result = couponService.getUserCouponsWithPagination(userId, page, size);

        // Then
        assertThat(result.userCouponClaimInfos()).hasSize(1);
        assertThat(result.userCouponClaimInfos().get(0).getCouponId()).isEqualTo(1);
        assertThat(result.couponDetailMap()).containsKey(1);
        assertThat(result.couponDetailMap()).doesNotContainKey(2);
        assertThat(result.totalCount()).isEqualTo(1);

        // Verify that only active coupons were processed
        verify(couponUserRepository).findActiveCouponsByUserId(userId);
        verify(couponCacheService).cacheUserCouponIds(eq(userId), any(UserCouponIds.class));
        verify(couponCacheService).cacheCouponDetail(eq(1), any(CouponDetail.class));
        verify(couponCacheService, never()).cacheCouponDetail(eq(2), any(CouponDetail.class));
    }

}

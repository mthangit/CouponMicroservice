package org.couponmanagement.grpc;

import io.grpc.stub.StreamObserver;
import org.couponmanagement.cache.CouponCacheService;
import org.couponmanagement.coupon.CouponServiceProto;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.repository.CouponRepository;
import org.couponmanagement.repository.CouponUserRepository;
import org.couponmanagement.service.CouponApplicationResult;
import org.couponmanagement.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.DisplayName;
import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.dto.UserCouponClaimInfo;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CouponGrpcServiceTest {

    @Mock
    private CouponService couponService;

    @Mock
    private RequestValidator validator;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponCacheService couponCacheService;

    @Mock
    private CouponUserRepository couponUserRepository;

    @Mock
    private StreamObserver<CouponServiceProto.ApplyCouponManualResponse> manualResponseObserver;

    @Mock
    private StreamObserver<CouponServiceProto.ApplyCouponAutoResponse> autoResponseObserver;

    @InjectMocks
    private CouponGrpcService couponGrpcService;

    private CouponServiceProto.ApplyCouponManualRequest validManualRequest;

    @BeforeEach
    void setUp() {
        validManualRequest = CouponServiceProto.ApplyCouponManualRequest.newBuilder()
                .setUserId(1)
                .setCouponCode("DISCOUNT10")
                .setOrderAmount(100.0)
                .setOrderDate(LocalDateTime.now().toString())
                .build();

        var validAutoRequest = CouponServiceProto.ApplyCouponAutoRequest.newBuilder()
                .setUserId(1)
                .setOrderAmount(100.0)
                .setOrderDate(LocalDateTime.now().toString())
                .build();
    }

    @Test
    void applyCouponManual_Success() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateCouponCode(anyString());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        CouponApplicationResult successResult = CouponApplicationResult.builder()
                .success(true)
                .couponId(123)
                .couponCode("DISCOUNT10")
                .discountAmount(BigDecimal.valueOf(10.0))
                .build();

        when(couponService.applyCouponManual(
                eq(1),
                eq("DISCOUNT10"),
                eq(BigDecimal.valueOf(100.0)),
                any(LocalDateTime.class)))
                .thenReturn(successResult);

        // Act
        couponGrpcService.applyCouponManual(validManualRequest, manualResponseObserver);

        // Assert
        ArgumentCaptor<CouponServiceProto.ApplyCouponManualResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponManualResponse.class);

        verify(manualResponseObserver).onNext(responseCaptor.capture());
        verify(manualResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponManualResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.OK, response.getStatus().getCode());
        assertEquals("Coupon applied successfully", response.getStatus().getMessage());
        assertTrue(response.getPayload().getSuccess());
        assertEquals(123, response.getPayload().getCouponId());
        assertEquals("DISCOUNT10", response.getPayload().getCouponCode());
        assertEquals(100.0, response.getPayload().getOrderAmount());
        assertEquals(10.0, response.getPayload().getDiscountAmount());
        assertEquals(90.0, response.getPayload().getFinalAmount());
    }

    @Test
    void applyCouponManual_Failure() {
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateCouponCode(anyString());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        CouponApplicationResult failureResult = CouponApplicationResult.builder()
                .success(false)
                .errorMessage("Coupon not found")
                .discountAmount(BigDecimal.ZERO)
                .build();

        when(couponService.applyCouponManual(
                eq(1),
                eq("DISCOUNT10"),
                eq(BigDecimal.valueOf(100.0)),
                any(LocalDateTime.class)))
                .thenReturn(failureResult);

        couponGrpcService.applyCouponManual(validManualRequest, manualResponseObserver);

        ArgumentCaptor<CouponServiceProto.ApplyCouponManualResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponManualResponse.class);

        verify(manualResponseObserver).onNext(responseCaptor.capture());
        verify(manualResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponManualResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.INTERNAL, response.getStatus().getCode());
        assertEquals("Coupon not found for user", response.getStatus().getMessage());
        assertFalse(response.getPayload().getSuccess());
        assertEquals(100.0, response.getPayload().getOrderAmount());
        assertEquals(0.0, response.getPayload().getDiscountAmount());
        assertEquals(100.0, response.getPayload().getFinalAmount());
        assertEquals("Coupon not found", response.getPayload().getErrorMessage());
    }

    @Test
    void applyCouponManual_ValidationException() {
        doThrow(new IllegalArgumentException("Invalid user ID"))
                .when(validator).validateUserId(anyInt());

        couponGrpcService.applyCouponManual(validManualRequest, manualResponseObserver);
        ArgumentCaptor<CouponServiceProto.ApplyCouponManualResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponManualResponse.class);

        verify(manualResponseObserver).onNext(responseCaptor.capture());
        verify(manualResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponManualResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.INVALID_ARGUMENT, response.getStatus().getCode());
        assertTrue(response.getStatus().getMessage().contains("Invalid user ID"));

        verify(couponService, never()).applyCouponManual(any(), any(), any(), any());
    }

    @Test
    void applyCouponManual_RuntimeException() {
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateCouponCode(anyString());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        when(couponService.applyCouponManual(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        couponGrpcService.applyCouponManual(validManualRequest, manualResponseObserver);

        ArgumentCaptor<CouponServiceProto.ApplyCouponManualResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponManualResponse.class);

        verify(manualResponseObserver).onNext(responseCaptor.capture());
        verify(manualResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponManualResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.INTERNAL, response.getStatus().getCode());
        assertEquals("Internal server error", response.getStatus().getMessage());
    }

    @Test
    @DisplayName("getUserCoupons should only return active coupons")
    void getUserCoupons_ShouldOnlyReturnActiveCoupons() {
        // Given
        Integer userId = 1;
        int page = 0;
        int size = 10;

        // Create test coupons
        CouponDetail activeCouponDetail = new CouponDetail();
        activeCouponDetail.setCouponId(1);
        activeCouponDetail.setCouponCode("ACTIVE001");
        activeCouponDetail.setActive(true);
        activeCouponDetail.setCreatedAt(LocalDateTime.now());
        activeCouponDetail.setExpiryDate(LocalDateTime.now().plusDays(30));

        CouponDetail inactiveCouponDetail = new CouponDetail();
        inactiveCouponDetail.setCouponId(2);
        inactiveCouponDetail.setCouponCode("INACTIVE001");
        inactiveCouponDetail.setActive(false);
        inactiveCouponDetail.setCreatedAt(LocalDateTime.now());
        inactiveCouponDetail.setExpiryDate(LocalDateTime.now().plusDays(30));

        // Create UserCouponClaimInfo
        UserCouponClaimInfo activeClaimInfo = UserCouponClaimInfo.builder()
                .userId(userId)
                .couponId(1)
                .claimedDate(LocalDateTime.now())
                .expiryDate(LocalDateTime.now().plusDays(30))
                .build();

        UserCouponClaimInfo inactiveClaimInfo = UserCouponClaimInfo.builder()
                .userId(userId)
                .couponId(2)
                .claimedDate(LocalDateTime.now())
                .expiryDate(LocalDateTime.now().plusDays(30))
                .build();

        // Create coupon detail map
        Map<Integer, CouponDetail> couponDetailMap = new HashMap<>();
        couponDetailMap.put(1, activeCouponDetail);
        couponDetailMap.put(2, inactiveCouponDetail);

        // Create UserCouponsResult
        CouponService.UserCouponsResult serviceResult = new CouponService.UserCouponsResult(
                List.of(activeClaimInfo, inactiveClaimInfo),
                couponDetailMap,
                1, // Only active coupon should be counted
                page,
                size
        );

        // Mock service
        when(couponService.getUserCouponsWithPagination(userId, page, size))
                .thenReturn(serviceResult);

        // Create request
        CouponServiceProto.GetUserCouponsRequest request = CouponServiceProto.GetUserCouponsRequest.newBuilder()
                .setUserId(userId)
                .setPage(page)
                .setSize(size)
                .build();

        // Create response observer
        StreamObserver<CouponServiceProto.GetUserCouponsResponse> responseObserver = mock(StreamObserver.class);

        // When
        couponGrpcService.getUserCoupons(request, responseObserver);

        // Then
        ArgumentCaptor<CouponServiceProto.GetUserCouponsResponse> responseCaptor = 
                ArgumentCaptor.forClass(CouponServiceProto.GetUserCouponsResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        CouponServiceProto.GetUserCouponsResponse response = responseCaptor.getValue();
        assertThat(response.getStatus().getCode()).isEqualTo(CouponServiceProto.StatusCode.OK);
        assertThat(response.getPayload().getUserCouponsCount()).isEqualTo(1);
        assertThat(response.getPayload().getTotalCount()).isEqualTo(1);

        // Verify only active coupon is returned
        CouponServiceProto.UserCouponSummary returnedCoupon = response.getPayload().getUserCoupons(0);
        assertThat(returnedCoupon.getCouponId()).isEqualTo(1);
        assertThat(returnedCoupon.getCouponCode()).isEqualTo("ACTIVE001");
    }

}

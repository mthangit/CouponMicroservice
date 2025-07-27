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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    private CouponServiceProto.ApplyCouponAutoRequest validAutoRequest;

    @BeforeEach
    void setUp() {
        validManualRequest = CouponServiceProto.ApplyCouponManualRequest.newBuilder()
                .setUserId(1)
                .setCouponCode("DISCOUNT10")
                .setOrderAmount(100.0)
                .setOrderDate(LocalDateTime.now().toString())
                .build();

        validAutoRequest = CouponServiceProto.ApplyCouponAutoRequest.newBuilder()
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
        // Arrange
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

        // Act
        couponGrpcService.applyCouponManual(validManualRequest, manualResponseObserver);

        // Assert
        ArgumentCaptor<CouponServiceProto.ApplyCouponManualResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponManualResponse.class);

        verify(manualResponseObserver).onNext(responseCaptor.capture());
        verify(manualResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponManualResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.INTERNAL, response.getStatus().getCode());
        assertEquals("Coupon not found", response.getStatus().getMessage());
        assertFalse(response.getPayload().getSuccess());
        assertEquals(100.0, response.getPayload().getOrderAmount());
        assertEquals(0.0, response.getPayload().getDiscountAmount());
        assertEquals(100.0, response.getPayload().getFinalAmount());
        assertEquals("Coupon not found", response.getPayload().getErrorMessage());
    }

    @Test
    void applyCouponManual_ValidationException() {
        // Arrange
        doThrow(new IllegalArgumentException("Invalid user ID"))
                .when(validator).validateUserId(anyInt());

        // Act
        couponGrpcService.applyCouponManual(validManualRequest, manualResponseObserver);

        // Assert
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
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateCouponCode(anyString());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        when(couponService.applyCouponManual(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act
        couponGrpcService.applyCouponManual(validManualRequest, manualResponseObserver);

        // Assert
        ArgumentCaptor<CouponServiceProto.ApplyCouponManualResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponManualResponse.class);

        verify(manualResponseObserver).onNext(responseCaptor.capture());
        verify(manualResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponManualResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.INTERNAL, response.getStatus().getCode());
        assertEquals("Internal server error", response.getStatus().getMessage());
    }

    @Test
    void applyCouponAuto_Success() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        CouponApplicationResult successResult = CouponApplicationResult.builder()
                .success(true)
                .couponId(456)
                .couponCode("AUTO20")
                .discountAmount(BigDecimal.valueOf(20.0))
                .build();

        when(couponService.applyCouponAuto(
                eq(1),
                eq(BigDecimal.valueOf(100.0)),
                any(LocalDateTime.class)))
                .thenReturn(successResult);

        // Act
        couponGrpcService.applyCouponAuto(validAutoRequest, autoResponseObserver);

        // Assert
        ArgumentCaptor<CouponServiceProto.ApplyCouponAutoResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponAutoResponse.class);

        verify(autoResponseObserver).onNext(responseCaptor.capture());
        verify(autoResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponAutoResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.OK, response.getStatus().getCode());
        assertEquals("Coupon applied successfully", response.getStatus().getMessage());
        assertEquals(456, response.getPayload().getCouponId());
        assertEquals("AUTO20", response.getPayload().getCouponCode());
        assertEquals(100.0, response.getPayload().getOrderAmount());
        assertEquals(20.0, response.getPayload().getDiscountAmount());
        assertEquals(80.0, response.getPayload().getFinalAmount());
    }

    @Test
    void applyCouponAuto_NoSuitableCoupon() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        CouponApplicationResult failureResult = CouponApplicationResult.builder()
                .success(false)
                .errorMessage("No suitable coupon found")
                .discountAmount(BigDecimal.ZERO)
                .build();

        when(couponService.applyCouponAuto(
                eq(1),
                eq(BigDecimal.valueOf(100.0)),
                any(LocalDateTime.class)))
                .thenReturn(failureResult);

        // Act
        couponGrpcService.applyCouponAuto(validAutoRequest, autoResponseObserver);

        // Assert
        ArgumentCaptor<CouponServiceProto.ApplyCouponAutoResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponAutoResponse.class);

        verify(autoResponseObserver).onNext(responseCaptor.capture());
        verify(autoResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponAutoResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.NOT_FOUND, response.getStatus().getCode());
        assertEquals("No suitable coupon found", response.getStatus().getMessage());
    }

    @Test
    void applyCouponAuto_ValidationException() {
        // Arrange
        doThrow(new IllegalArgumentException("Invalid order amount"))
                .when(validator).validateOrderAmount(anyDouble());

        // Act
        couponGrpcService.applyCouponAuto(validAutoRequest, autoResponseObserver);

        // Assert
        ArgumentCaptor<CouponServiceProto.ApplyCouponAutoResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponAutoResponse.class);

        verify(autoResponseObserver).onNext(responseCaptor.capture());
        verify(autoResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponAutoResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.INVALID_ARGUMENT, response.getStatus().getCode());
        assertTrue(response.getStatus().getMessage().contains("Invalid order amount"));

        verify(couponService, never()).applyCouponAuto(any(), any(), any());
    }

    @Test
    void applyCouponAuto_RuntimeException() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        when(couponService.applyCouponAuto(any(), any(), any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Act
        couponGrpcService.applyCouponAuto(validAutoRequest, autoResponseObserver);

        // Assert
        ArgumentCaptor<CouponServiceProto.ApplyCouponAutoResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponAutoResponse.class);

        verify(autoResponseObserver).onNext(responseCaptor.capture());
        verify(autoResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponAutoResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.INTERNAL, response.getStatus().getCode());
        assertEquals("Internal server error", response.getStatus().getMessage());
    }

    @Test
    void applyCouponManual_InvalidDateFormat() {
        // Arrange
        CouponServiceProto.ApplyCouponManualRequest invalidDateRequest =
                CouponServiceProto.ApplyCouponManualRequest.newBuilder()
                .setUserId(1)
                .setCouponCode("DISCOUNT10")
                .setOrderAmount(100.0)
                .setOrderDate("invalid-date-format")
                .build();

        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateCouponCode(anyString());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        // Act
        couponGrpcService.applyCouponManual(invalidDateRequest, manualResponseObserver);

        // Assert
        ArgumentCaptor<CouponServiceProto.ApplyCouponManualResponse> responseCaptor =
                ArgumentCaptor.forClass(CouponServiceProto.ApplyCouponManualResponse.class);

        verify(manualResponseObserver).onNext(responseCaptor.capture());
        verify(manualResponseObserver).onCompleted();

        CouponServiceProto.ApplyCouponManualResponse response = responseCaptor.getValue();
        assertEquals(CouponServiceProto.StatusCode.INVALID_ARGUMENT, response.getStatus().getCode());
        assertTrue(response.getStatus().getMessage().contains("Invalid date format"));

        verify(couponService, never()).applyCouponManual(any(), any(), any(), any());
    }
}

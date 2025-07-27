package org.couponmanagement.controller;

import org.couponmanagement.coupon.CouponServiceProto;
import org.couponmanagement.dto.ApplyCouponRequest;
import org.couponmanagement.dto.AutoApplyCouponRequest;
import org.couponmanagement.dto.response.ApplyCouponResponse;
import org.couponmanagement.grpc.CouponServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    @Mock
    private CouponServiceClient couponServiceClient;

    @InjectMocks
    private CouponController couponController;

    private ApplyCouponRequest validApplyRequest;
    private AutoApplyCouponRequest validAutoRequest;

    @BeforeEach
    void setUp() {
        validApplyRequest = new ApplyCouponRequest();
        validApplyRequest.setUserId(1);
        validApplyRequest.setCouponCode("DISCOUNT10");
        validApplyRequest.setOrderAmount(100.0);
        validApplyRequest.setOrderDate("2025-07-27T12:00:00");

        validAutoRequest = new AutoApplyCouponRequest();
        validAutoRequest.setUserId(1);
        validAutoRequest.setOrderAmount(100.0);
        validAutoRequest.setOrderDate("2025-07-27T12:00:00");
    }

    @Test
    void applyCouponManual_Success_ReturnsOkResponse() {
        CouponServiceProto.ApplyCouponManualResponse mockResponse =
            CouponServiceProto.ApplyCouponManualResponse.newBuilder()
                .setStatus(CouponServiceProto.Status.newBuilder()
                    .setCode(CouponServiceProto.StatusCode.OK)
                    .setMessage("Success"))
                .setPayload(CouponServiceProto.ApplyCouponManualResponsePayload.newBuilder()
                    .setSuccess(true)
                    .setCouponCode("DISCOUNT10")
                    .setOrderAmount(100.0)
                    .setDiscountAmount(10.0)
                    .setFinalAmount(90.0)
                    .setCouponId(123))
                .build();

        when(couponServiceClient.applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<?> response = couponController.applyCouponManual(validApplyRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(ApplyCouponResponse.class, response.getBody());

        ApplyCouponResponse couponResponse = (ApplyCouponResponse) response.getBody();
        assertTrue(couponResponse.getSuccess());
        assertEquals("DISCOUNT10", couponResponse.getCouponCode());
        assertEquals(100.0, couponResponse.getOrderAmount());
        assertEquals(10.0, couponResponse.getDiscountAmount());
        assertEquals(90.0, couponResponse.getFinalAmount());

        verify(couponServiceClient).applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class));
    }

    @Test
    void applyCouponManual_ServiceError_ReturnsBadRequest() {
        CouponServiceProto.ApplyCouponManualResponse mockResponse =
            CouponServiceProto.ApplyCouponManualResponse.newBuilder()
                .setStatus(CouponServiceProto.Status.newBuilder()
                    .setCode(CouponServiceProto.StatusCode.INTERNAL)
                    .setMessage("Coupon not found"))
                .setPayload(CouponServiceProto.ApplyCouponManualResponsePayload.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Coupon not found"))
                .build();

        when(couponServiceClient.applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<?> response = couponController.applyCouponManual(validApplyRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void applyCouponManual_GrpcException_ReturnsInternalServerError() {
        when(couponServiceClient.applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class)))
                .thenThrow(new RuntimeException("gRPC communication failed"));

        ResponseEntity<?> response = couponController.applyCouponManual(validApplyRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void applyCouponAuto_Success_ReturnsOkResponse() {
        CouponServiceProto.ApplyCouponAutoResponse mockResponse =
            CouponServiceProto.ApplyCouponAutoResponse.newBuilder()
                .setStatus(CouponServiceProto.Status.newBuilder()
                    .setCode(CouponServiceProto.StatusCode.OK)
                    .setMessage("Success"))
                .setPayload(CouponServiceProto.ApplyCouponAutoResponsePayload.newBuilder()
                    .setCouponCode("AUTO20")
                    .setOrderAmount(100.0)
                    .setDiscountAmount(20.0)
                    .setFinalAmount(80.0)
                    .setCouponId(456))
                .build();

        when(couponServiceClient.applyCouponAuto(any(CouponServiceProto.ApplyCouponAutoRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<?> response = couponController.applyCouponAuto(validAutoRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(ApplyCouponResponse.class, response.getBody());

        ApplyCouponResponse couponResponse = (ApplyCouponResponse) response.getBody();
        assertTrue(couponResponse.getSuccess());
        assertEquals("AUTO20", couponResponse.getCouponCode());
        assertEquals(100.0, couponResponse.getOrderAmount());
        assertEquals(20.0, couponResponse.getDiscountAmount());
        assertEquals(80.0, couponResponse.getFinalAmount());

        verify(couponServiceClient).applyCouponAuto(any(CouponServiceProto.ApplyCouponAutoRequest.class));
    }

    @Test
    void applyCouponAuto_NoSuitableCoupon_ReturnsNotFound() {
        CouponServiceProto.ApplyCouponAutoResponse mockResponse =
            CouponServiceProto.ApplyCouponAutoResponse.newBuilder()
                .setStatus(CouponServiceProto.Status.newBuilder()
                    .setCode(CouponServiceProto.StatusCode.NOT_FOUND)
                    .setMessage("No suitable coupon found"))
                .build();

        when(couponServiceClient.applyCouponAuto(any(CouponServiceProto.ApplyCouponAutoRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<?> response = couponController.applyCouponAuto(validAutoRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void applyCouponManual_WithNullOrderDate_HandlesCorrectly() {
        validApplyRequest.setOrderDate(null);

        CouponServiceProto.ApplyCouponManualResponse mockResponse =
            CouponServiceProto.ApplyCouponManualResponse.newBuilder()
                .setStatus(CouponServiceProto.Status.newBuilder()
                    .setCode(CouponServiceProto.StatusCode.OK))
                .setPayload(CouponServiceProto.ApplyCouponManualResponsePayload.newBuilder()
                    .setSuccess(true)
                    .setCouponCode("DISCOUNT10")
                    .setOrderAmount(100.0)
                    .setDiscountAmount(10.0)
                    .setFinalAmount(90.0))
                .build();

        when(couponServiceClient.applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<?> response = couponController.applyCouponManual(validApplyRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(couponServiceClient).applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class));
    }

    @Test
    void applyCouponManual_WithEmptyOrderDate_HandlesCorrectly() {
        validApplyRequest.setOrderDate("   ");

        CouponServiceProto.ApplyCouponManualResponse mockResponse =
            CouponServiceProto.ApplyCouponManualResponse.newBuilder()
                .setStatus(CouponServiceProto.Status.newBuilder()
                    .setCode(CouponServiceProto.StatusCode.OK))
                .setPayload(CouponServiceProto.ApplyCouponManualResponsePayload.newBuilder()
                    .setSuccess(true)
                    .setCouponCode("DISCOUNT10")
                    .setOrderAmount(100.0)
                    .setDiscountAmount(10.0)
                    .setFinalAmount(90.0))
                .build();

        when(couponServiceClient.applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = couponController.applyCouponManual(validApplyRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(couponServiceClient).applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class));
    }

    @Test
    void getUserCoupons_Success_ReturnsOkResponse() {
        Integer userId = 1;
        Integer page = 0;
        Integer size = 10;

        assertDoesNotThrow(() -> {
            ResponseEntity<?> response = couponController.getUserCoupons(userId, page, size);
        });
    }

    @Test
    void applyCouponAuto_GrpcException_ReturnsInternalServerError() {
        when(couponServiceClient.applyCouponAuto(any(CouponServiceProto.ApplyCouponAutoRequest.class)))
                .thenThrow(new RuntimeException("gRPC service unavailable"));

        ResponseEntity<?> response = couponController.applyCouponAuto(validAutoRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}

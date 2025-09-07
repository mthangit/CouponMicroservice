package org.couponmanagement.service;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.couponmanagement.coupon.CouponServiceGrpc;
import org.couponmanagement.coupon.CouponServiceProto;
import org.couponmanagement.dto.ProcessOrderRequest;
import org.couponmanagement.dto.ProcessOrderResult;
import org.couponmanagement.entity.Order;
import org.couponmanagement.grpc.client.GrpcClientFactory;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private GrpcClientFactory grpcClientFactory;

    @Mock
    private RequestValidator validator;

    @Mock
    private Channel channel;

    @Mock
    private CouponServiceGrpc.CouponServiceBlockingStub stub;

    @InjectMocks
    private OrderService orderService;

    private ProcessOrderRequest validRequest;
    private Order savedOrder;

    @BeforeEach
    void setUp() {
        validRequest = ProcessOrderRequest.builder()
                .userId(1)
                .orderAmount(100.0)
                .couponCode("DISCOUNT10")
                .requestId("req-123")
                .orderDate(LocalDateTime.now())
                .build();

        savedOrder = Order.builder()
                .id(1)
                .userId(1)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.valueOf(10.0))
                .finalAmount(BigDecimal.valueOf(90.0))
                .couponId(123)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void processOrderManual_Success() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());
        doNothing().when(validator).validateCouponCode(anyString());

        when(grpcClientFactory.getCouponServiceChannel()).thenReturn(channel);
        when(CouponServiceGrpc.newBlockingStub(channel)).thenReturn(stub);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);

        CouponServiceProto.ApplyCouponManualResponse grpcResponse =
            CouponServiceProto.ApplyCouponManualResponse.newBuilder()
                .setStatus(CouponServiceProto.Status.newBuilder()
                    .setCode(CouponServiceProto.StatusCode.OK)
                    .setMessage("Success")
                    .build())
                .setPayload(CouponServiceProto.ApplyCouponManualResponsePayload.newBuilder()
                    .setCouponId(123)
                    .setCouponCode("DISCOUNT10")
                    .setDiscountAmount(10.0)
                    .build())
                .build();

        when(stub.applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class)))
            .thenReturn(grpcResponse);

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        ProcessOrderResult result = orderService.processOrderManual(validRequest);

        // Assert
        assertTrue(result.success());
        assertEquals(1, result.orderId());
        assertEquals(1, result.userId());
        assertEquals(BigDecimal.valueOf(100.0), result.orderAmount());
        assertEquals(BigDecimal.valueOf(10.0), result.discountAmount());
        assertEquals(BigDecimal.valueOf(90.0), result.finalAmount());
        assertEquals("DISCOUNT10", result.couponCode());
        assertEquals(123, result.couponId());
        assertEquals("COMPLETED", result.status());

        verify(validator).validateUserId(1);
        verify(validator).validateOrderAmount(100.0);
        verify(validator).validateCouponCode("DISCOUNT10");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void processOrderManual_CouponServiceError() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());
        doNothing().when(validator).validateCouponCode(anyString());

        when(grpcClientFactory.getCouponServiceChannel()).thenReturn(channel);
        when(CouponServiceGrpc.newBlockingStub(channel)).thenReturn(stub);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);

        CouponServiceProto.ApplyCouponManualResponse grpcResponse =
            CouponServiceProto.ApplyCouponManualResponse.newBuilder()
                .setStatus(CouponServiceProto.Status.newBuilder()
                    .setCode(CouponServiceProto.StatusCode.NOT_FOUND)
                    .setMessage("Coupon not found")
                    .build())
                .build();

        when(stub.applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class)))
            .thenReturn(grpcResponse);

        // Act
        ProcessOrderResult result = orderService.processOrderManual(validRequest);

        // Assert
        assertFalse(result.success());
        assertEquals("Coupon not found", result.errorMessage());

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void processOrderManual_GrpcException() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());
        doNothing().when(validator).validateCouponCode(anyString());

        when(grpcClientFactory.getCouponServiceChannel()).thenReturn(channel);
        when(CouponServiceGrpc.newBlockingStub(channel)).thenReturn(stub);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);

        when(stub.applyCouponManual(any(CouponServiceProto.ApplyCouponManualRequest.class)))
            .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        // Act
        ProcessOrderResult result = orderService.processOrderManual(validRequest);

        // Assert
        assertFalse(result.success());
        assertEquals("Coupon service unavailable", result.errorMessage());

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void processOrderAuto_Success() {
        // Arrange
        ProcessOrderRequest autoRequest = ProcessOrderRequest.builder()
                .userId(1)
                .orderAmount(100.0)
                .orderDate(LocalDateTime.now())
                .build();

        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        when(grpcClientFactory.getCouponServiceChannel()).thenReturn(channel);
        when(CouponServiceGrpc.newBlockingStub(channel)).thenReturn(stub);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);

        CouponServiceProto.ApplyCouponAutoResponse grpcResponse =
            CouponServiceProto.ApplyCouponAutoResponse.newBuilder()
                .setStatus(CouponServiceProto.Status.newBuilder()
                    .setCode(CouponServiceProto.StatusCode.OK)
                    .setMessage("Success")
                    .build())
                .setPayload(CouponServiceProto.ApplyCouponAutoResponsePayload.newBuilder()
                    .setCouponId(456)
                    .setCouponCode("AUTO20")
                    .setDiscountAmount(20.0)
                    .build())
                .build();

        when(stub.applyCouponAuto(any(CouponServiceProto.ApplyCouponAutoRequest.class)))
            .thenReturn(grpcResponse);

        Order autoSavedOrder = Order.builder()
                .id(2)
                .userId(1)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.valueOf(20.0))
                .finalAmount(BigDecimal.valueOf(80.0))
                .couponId(456)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(orderRepository.save(any(Order.class))).thenReturn(autoSavedOrder);

        // Act
        ProcessOrderResult result = orderService.processOrderAuto(autoRequest);

        // Assert
        assertTrue(result.success());
        assertEquals(2, result.orderId());
        assertEquals(BigDecimal.valueOf(20.0), result.discountAmount());
        assertEquals(BigDecimal.valueOf(80.0), result.finalAmount());

        verify(validator).validateUserId(1);
        verify(validator).validateOrderAmount(100.0);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void processOrderAuto_NoCouponFound_StillSuccess() {
        // Arrange
        ProcessOrderRequest autoRequest = ProcessOrderRequest.builder()
                .userId(1)
                .orderAmount(100.0)
                .orderDate(LocalDateTime.now())
                .build();

        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());

        when(grpcClientFactory.getCouponServiceChannel()).thenReturn(channel);
        when(CouponServiceGrpc.newBlockingStub(channel)).thenReturn(stub);
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);

        when(stub.applyCouponAuto(any(CouponServiceProto.ApplyCouponAutoRequest.class)))
            .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        Order orderWithoutDiscount = Order.builder()
                .id(3)
                .userId(1)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.valueOf(100.0))
                .couponId(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(orderRepository.save(any(Order.class))).thenReturn(orderWithoutDiscount);

        // Act
        ProcessOrderResult result = orderService.processOrderAuto(autoRequest);

        // Assert
        assertTrue(result.success());
        assertEquals(3, result.orderId());
        assertEquals(BigDecimal.ZERO, result.discountAmount());
        assertEquals(BigDecimal.valueOf(100.0), result.finalAmount());

        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void processOrderManual_ValidationException() {
        // Arrange
        doThrow(new IllegalArgumentException("Invalid user ID"))
            .when(validator).validateUserId(anyInt());

        // Act
        ProcessOrderResult result = orderService.processOrderManual(validRequest);

        // Assert
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Failed to process order"));

        verify(orderRepository, never()).save(any(Order.class));
    }
}

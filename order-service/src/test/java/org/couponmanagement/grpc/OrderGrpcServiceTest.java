package org.couponmanagement.grpc;

import io.grpc.stub.StreamObserver;
import org.couponmanagement.dto.ProcessOrderRequest;
import org.couponmanagement.dto.ProcessOrderResult;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.order.OrderServiceProto;
import org.couponmanagement.service.OrderService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderGrpcServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private RequestValidator validator;

    @Mock
    private StreamObserver<OrderServiceProto.ProcessOrderResponse> responseObserver;

    @InjectMocks
    private OrderGrpcService orderGrpcService;

    private OrderServiceProto.ProcessOrderRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = OrderServiceProto.ProcessOrderRequest.newBuilder()
                .setUserId(1)
                .setOrderAmount(100.0)
                .setCouponCode("DISCOUNT10")
                .setRequestId("req-123")
                .setOrderDate(LocalDateTime.now().toString())
                .build();
    }

    @Test
    void processOrder_ManualCoupon_Success() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());
        doNothing().when(validator).validateRequestId(anyString());

        ProcessOrderResult successResult = ProcessOrderResult.builder()
                .success(true)
                .orderId(1)
                .userId(1)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.valueOf(10.0))
                .finalAmount(BigDecimal.valueOf(90.0))
                .couponCode("DISCOUNT10")
                .couponId(123)
                .orderDate(LocalDateTime.now())
                .status("COMPLETED")
                .build();

        when(orderService.processOrderManual(any(ProcessOrderRequest.class)))
                .thenReturn(successResult);

        // Act
        orderGrpcService.processOrder(validRequest, responseObserver);

        // Assert
        ArgumentCaptor<OrderServiceProto.ProcessOrderResponse> responseCaptor =
                ArgumentCaptor.forClass(OrderServiceProto.ProcessOrderResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        OrderServiceProto.ProcessOrderResponse response = responseCaptor.getValue();
        assertEquals(OrderServiceProto.StatusCode.OK, response.getStatus().getCode());
        assertEquals("Order processed successfully", response.getStatus().getMessage());
        assertEquals(1, response.getPayload().getOrderId());
        assertEquals(100.0, response.getPayload().getOrderAmount());
        assertEquals(10.0, response.getPayload().getDiscountAmount());
        assertEquals(90.0, response.getPayload().getFinalAmount());
        assertEquals("DISCOUNT10", response.getPayload().getCouponCode());
        assertEquals(123, response.getPayload().getCouponId());
        assertEquals("COMPLETED", response.getPayload().getStatus());
    }

    @Test
    void processOrder_AutoCoupon_Success() {
        // Arrange
        OrderServiceProto.ProcessOrderRequest autoRequest = OrderServiceProto.ProcessOrderRequest.newBuilder()
                .setUserId(1)
                .setOrderAmount(100.0)
                .setCouponCode("") // Empty coupon code for auto mode
                .setRequestId("req-123")
                .setOrderDate(LocalDateTime.now().toString())
                .build();

        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());
        doNothing().when(validator).validateRequestId(anyString());

        ProcessOrderResult successResult = ProcessOrderResult.builder()
                .success(true)
                .orderId(2)
                .userId(1)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.valueOf(20.0))
                .finalAmount(BigDecimal.valueOf(80.0))
                .couponCode("AUTO20")
                .couponId(456)
                .orderDate(LocalDateTime.now())
                .status("COMPLETED")
                .build();

        when(orderService.processOrderAuto(any(ProcessOrderRequest.class)))
                .thenReturn(successResult);

        // Act
        orderGrpcService.processOrder(autoRequest, responseObserver);

        // Assert
        ArgumentCaptor<OrderServiceProto.ProcessOrderResponse> responseCaptor =
                ArgumentCaptor.forClass(OrderServiceProto.ProcessOrderResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        OrderServiceProto.ProcessOrderResponse response = responseCaptor.getValue();
        assertEquals(OrderServiceProto.StatusCode.OK, response.getStatus().getCode());
        assertEquals(2, response.getPayload().getOrderId());
        assertEquals(20.0, response.getPayload().getDiscountAmount());
        assertEquals(80.0, response.getPayload().getFinalAmount());
        assertEquals("AUTO20", response.getPayload().getCouponCode());
    }

    @Test
    void processOrder_ServiceFailure() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());
        doNothing().when(validator).validateRequestId(anyString());

        ProcessOrderResult failureResult = ProcessOrderResult.builder()
                .success(false)
                .errorMessage("Coupon not found")
                .build();

        when(orderService.processOrderManual(any(ProcessOrderRequest.class)))
                .thenReturn(failureResult);

        // Act
        orderGrpcService.processOrder(validRequest, responseObserver);

        // Assert
        ArgumentCaptor<OrderServiceProto.ProcessOrderResponse> responseCaptor =
                ArgumentCaptor.forClass(OrderServiceProto.ProcessOrderResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        OrderServiceProto.ProcessOrderResponse response = responseCaptor.getValue();
        assertEquals(OrderServiceProto.StatusCode.INVALID_ARGUMENT, response.getStatus().getCode());
        assertEquals("Order processing failed", response.getStatus().getMessage());
        assertEquals("ORDER_PROCESSING_FAILED", response.getError().getCode());
        assertEquals("Coupon not found", response.getError().getMessage());
    }

    @Test
    void processOrder_ValidationException() {
        // Arrange
        doThrow(new IllegalArgumentException("Invalid user ID"))
                .when(validator).validateUserId(anyInt());

        // Act
        orderGrpcService.processOrder(validRequest, responseObserver);

        // Assert
        ArgumentCaptor<OrderServiceProto.ProcessOrderResponse> responseCaptor =
                ArgumentCaptor.forClass(OrderServiceProto.ProcessOrderResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        OrderServiceProto.ProcessOrderResponse response = responseCaptor.getValue();
        assertEquals(OrderServiceProto.StatusCode.INTERNAL, response.getStatus().getCode());
        assertEquals("Internal server error", response.getStatus().getMessage());
        assertEquals("INTERNAL_ERROR", response.getError().getCode());
        assertTrue(response.getError().getMessage().contains("Invalid user ID"));

        verify(orderService, never()).processOrderManual(any());
        verify(orderService, never()).processOrderAuto(any());
    }

    @Test
    void processOrder_RuntimeException() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());
        doNothing().when(validator).validateRequestId(anyString());

        when(orderService.processOrderManual(any(ProcessOrderRequest.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act
        orderGrpcService.processOrder(validRequest, responseObserver);

        // Assert
        ArgumentCaptor<OrderServiceProto.ProcessOrderResponse> responseCaptor =
                ArgumentCaptor.forClass(OrderServiceProto.ProcessOrderResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        OrderServiceProto.ProcessOrderResponse response = responseCaptor.getValue();
        assertEquals(OrderServiceProto.StatusCode.INTERNAL, response.getStatus().getCode());
        assertEquals("Internal server error", response.getStatus().getMessage());
        assertEquals("INTERNAL_ERROR", response.getError().getCode());
        assertTrue(response.getError().getMessage().contains("Database connection failed"));
    }

    @Test
    void processOrder_NullCouponValues() {
        // Arrange
        doNothing().when(validator).validateUserId(anyInt());
        doNothing().when(validator).validateOrderAmount(anyDouble());
        doNothing().when(validator).validateRequestId(anyString());

        ProcessOrderResult resultWithNulls = ProcessOrderResult.builder()
                .success(true)
                .orderId(3)
                .userId(1)
                .orderAmount(BigDecimal.valueOf(100.0))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.valueOf(100.0))
                .couponCode(null) // Null coupon code
                .couponId(null) // Null coupon ID
                .orderDate(LocalDateTime.now())
                .status("COMPLETED")
                .build();

        when(orderService.processOrderManual(any(ProcessOrderRequest.class)))
                .thenReturn(resultWithNulls);

        // Act
        orderGrpcService.processOrder(validRequest, responseObserver);

        // Assert
        ArgumentCaptor<OrderServiceProto.ProcessOrderResponse> responseCaptor =
                ArgumentCaptor.forClass(OrderServiceProto.ProcessOrderResponse.class);

        verify(responseObserver).onNext(responseCaptor.capture());

        OrderServiceProto.ProcessOrderResponse response = responseCaptor.getValue();
        assertEquals(OrderServiceProto.StatusCode.OK, response.getStatus().getCode());
        assertEquals("", response.getPayload().getCouponCode()); // Should be empty string
        assertEquals(0, response.getPayload().getCouponId()); // Should be 0
    }
}

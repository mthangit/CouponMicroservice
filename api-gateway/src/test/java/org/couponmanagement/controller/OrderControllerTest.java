package org.couponmanagement.controller;

import org.couponmanagement.dto.ProcessOrderRequest;
import org.couponmanagement.dto.response.ProcessOrderResponse;
import org.couponmanagement.grpc.OrderServiceClient;
import org.couponmanagement.order.OrderServiceProto;
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
class OrderControllerTest {

    @Mock
    private OrderServiceClient orderServiceClient;

    @InjectMocks
    private OrderController orderController;

    private ProcessOrderRequest validOrderRequest;

    @BeforeEach
    void setUp() {
        validOrderRequest = new ProcessOrderRequest();
        validOrderRequest.setUserId(1);
        validOrderRequest.setOrderAmount(100.0);
        validOrderRequest.setCouponCode("DISCOUNT10");
        validOrderRequest.setOrderDate("2025-07-27T12:00:00");
        validOrderRequest.setRequestId("test-request-id");
    }

    @Test
    void processOrder_WithCoupon_Success_ReturnsOkResponse() {
        // Arrange
        OrderServiceProto.ProcessOrderResponse mockResponse =
            OrderServiceProto.ProcessOrderResponse.newBuilder()
                .setStatus(OrderServiceProto.Status.newBuilder()
                    .setCode(OrderServiceProto.StatusCode.OK)
                    .setMessage("Order processed successfully"))
                .setPayload(OrderServiceProto.ProcessOrderPayload.newBuilder()
                    .setOrderId(12345)
                    .setUserId(1)
                    .setOrderAmount(100.0)
                    .setDiscountAmount(10.0)
                    .setFinalAmount(90.0)
                    .setCouponCode("DISCOUNT10")
                    .setCouponId(123)
                    .setOrderDate("2025-07-27T12:00:00")
                    .setStatus("COMPLETED"))
                .build();

        when(orderServiceClient.processOrder(any(OrderServiceProto.ProcessOrderRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = orderController.processOrder(validOrderRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(ProcessOrderResponse.class, response.getBody());

        ProcessOrderResponse orderResponse = (ProcessOrderResponse) response.getBody();
        assertEquals(12345, orderResponse.getOrderId());
        assertEquals(1, orderResponse.getUserId());
        assertEquals(100.0, orderResponse.getOrderAmount());
        assertEquals(10.0, orderResponse.getDiscountAmount());
        assertEquals(90.0, orderResponse.getFinalAmount());
        assertEquals("DISCOUNT10", orderResponse.getCouponCode());
        assertEquals(123, orderResponse.getCouponId());
        assertEquals("COMPLETED", orderResponse.getStatus());

        verify(orderServiceClient).processOrder(any(OrderServiceProto.ProcessOrderRequest.class));
    }

    @Test
    void processOrder_WithoutCoupon_Success_ReturnsOkResponse() {
        // Arrange
        validOrderRequest.setCouponCode(null); // Auto coupon mode

        OrderServiceProto.ProcessOrderResponse mockResponse =
            OrderServiceProto.ProcessOrderResponse.newBuilder()
                .setStatus(OrderServiceProto.Status.newBuilder()
                    .setCode(OrderServiceProto.StatusCode.OK)
                    .setMessage("Order processed successfully"))
                .setPayload(OrderServiceProto.ProcessOrderPayload.newBuilder()
                    .setOrderId(12346)
                    .setUserId(1)
                    .setOrderAmount(100.0)
                    .setDiscountAmount(20.0)
                    .setFinalAmount(80.0)
                    .setCouponCode("AUTO20")
                    .setCouponId(456)
                    .setOrderDate("2025-07-27T12:00:00")
                    .setStatus("COMPLETED"))
                .build();

        when(orderServiceClient.processOrder(any(OrderServiceProto.ProcessOrderRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = orderController.processOrder(validOrderRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(ProcessOrderResponse.class, response.getBody());

        ProcessOrderResponse orderResponse = (ProcessOrderResponse) response.getBody();
        assertEquals("AUTO20", orderResponse.getCouponCode());
        assertEquals(20.0, orderResponse.getDiscountAmount());
        assertEquals(80.0, orderResponse.getFinalAmount());

        verify(orderServiceClient).processOrder(any(OrderServiceProto.ProcessOrderRequest.class));
    }

    @Test
    void processOrder_ServiceError_ReturnsBadRequest() {
        // Arrange
        OrderServiceProto.ProcessOrderResponse mockResponse =
            OrderServiceProto.ProcessOrderResponse.newBuilder()
                .setStatus(OrderServiceProto.Status.newBuilder()
                    .setCode(OrderServiceProto.StatusCode.INVALID_ARGUMENT)
                    .setMessage("Order processing failed"))
                .setError(OrderServiceProto.Error.newBuilder()
                    .setCode("ORDER_PROCESSING_FAILED")
                    .setMessage("Invalid order data"))
                .build();

        when(orderServiceClient.processOrder(any(OrderServiceProto.ProcessOrderRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = orderController.processOrder(validOrderRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void processOrder_GrpcException_ReturnsInternalServerError() {
        // Arrange
        when(orderServiceClient.processOrder(any(OrderServiceProto.ProcessOrderRequest.class)))
                .thenThrow(new RuntimeException("gRPC communication failed"));

        // Act
        ResponseEntity<?> response = orderController.processOrder(validOrderRequest);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void processOrder_GeneratesRequestIdWhenNull() {
        // Arrange
        validOrderRequest.setRequestId(null);

        OrderServiceProto.ProcessOrderResponse mockResponse =
            OrderServiceProto.ProcessOrderResponse.newBuilder()
                .setStatus(OrderServiceProto.Status.newBuilder()
                    .setCode(OrderServiceProto.StatusCode.OK))
                .setPayload(OrderServiceProto.ProcessOrderPayload.newBuilder()
                    .setOrderId(12347)
                    .setUserId(1)
                    .setOrderAmount(100.0)
                    .setDiscountAmount(0.0)
                    .setFinalAmount(100.0)
                    .setStatus("COMPLETED"))
                .build();

        when(orderServiceClient.processOrder(any(OrderServiceProto.ProcessOrderRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = orderController.processOrder(validOrderRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify that processOrder was called with a request that has a requestId
        verify(orderServiceClient).processOrder(argThat(request ->
        {
            request.getRequestId();
            return !request.getRequestId().trim().isEmpty();
        }));
    }

    @Test
    void processOrder_GeneratesRequestIdWhenEmpty() {
        // Arrange
        validOrderRequest.setRequestId("   ");

        OrderServiceProto.ProcessOrderResponse mockResponse =
            OrderServiceProto.ProcessOrderResponse.newBuilder()
                .setStatus(OrderServiceProto.Status.newBuilder()
                    .setCode(OrderServiceProto.StatusCode.OK))
                .setPayload(OrderServiceProto.ProcessOrderPayload.newBuilder()
                    .setOrderId(12348)
                    .setUserId(1)
                    .setOrderAmount(100.0)
                    .setDiscountAmount(0.0)
                    .setFinalAmount(100.0)
                    .setStatus("COMPLETED"))
                .build();

        when(orderServiceClient.processOrder(any(OrderServiceProto.ProcessOrderRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = orderController.processOrder(validOrderRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify that processOrder was called with a generated requestId
        verify(orderServiceClient).processOrder(argThat(request ->
        {
            request.getRequestId();
            return !request.getRequestId().trim().isEmpty();
        }));
    }

    @Test
    void processOrder_WithNullOrderDate_HandlesCorrectly() {
        // Arrange
        validOrderRequest.setOrderDate(null);

        OrderServiceProto.ProcessOrderResponse mockResponse =
            OrderServiceProto.ProcessOrderResponse.newBuilder()
                .setStatus(OrderServiceProto.Status.newBuilder()
                    .setCode(OrderServiceProto.StatusCode.OK))
                .setPayload(OrderServiceProto.ProcessOrderPayload.newBuilder()
                    .setOrderId(12349)
                    .setUserId(1)
                    .setOrderAmount(100.0)
                    .setDiscountAmount(0.0)
                    .setFinalAmount(100.0)
                    .setStatus("COMPLETED"))
                .build();

        when(orderServiceClient.processOrder(any(OrderServiceProto.ProcessOrderRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = orderController.processOrder(validOrderRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(orderServiceClient).processOrder(any(OrderServiceProto.ProcessOrderRequest.class));
    }

    @Test
    void processOrder_WithEmptyStrings_HandlesCorrectly() {
        // Arrange
        validOrderRequest.setCouponCode("   ");
        validOrderRequest.setOrderDate("   ");

        OrderServiceProto.ProcessOrderResponse mockResponse =
            OrderServiceProto.ProcessOrderResponse.newBuilder()
                .setStatus(OrderServiceProto.Status.newBuilder()
                    .setCode(OrderServiceProto.StatusCode.OK))
                .setPayload(OrderServiceProto.ProcessOrderPayload.newBuilder()
                    .setOrderId(12350)
                    .setUserId(1)
                    .setOrderAmount(100.0)
                    .setDiscountAmount(0.0)
                    .setFinalAmount(100.0)
                    .setStatus("COMPLETED"))
                .build();

        when(orderServiceClient.processOrder(any(OrderServiceProto.ProcessOrderRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = orderController.processOrder(validOrderRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(orderServiceClient).processOrder(any(OrderServiceProto.ProcessOrderRequest.class));
    }
}

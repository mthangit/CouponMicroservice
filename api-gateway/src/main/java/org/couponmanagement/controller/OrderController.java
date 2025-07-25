package org.couponmanagement.controller;

import jakarta.validation.Valid;
import org.couponmanagement.dto.ProcessOrderRequest;
import org.couponmanagement.dto.response.ProcessOrderResponse;
import org.couponmanagement.dto.response.ErrorResponse;
import org.couponmanagement.grpc.OrderServiceClient;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.order.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Slf4j
public class OrderController {

    @Autowired
    private OrderServiceClient orderServiceClient;

    @PostMapping("/process")
    @PerformanceMonitor
    public ResponseEntity<?> processOrder(@Valid @RequestBody ProcessOrderRequest request) {
        try {
            log.info("Process order: user={}, amount={}", request.getUserId(), request.getOrderAmount());


            // Convert DTO to protobuf
            OrderServiceProto.ProcessOrderRequest.Builder protoRequestBuilder = OrderServiceProto.ProcessOrderRequest.newBuilder()
                    .setUserId(request.getUserId())
                    .setOrderAmount(request.getOrderAmount());

            if (request.getCouponCode() != null && !request.getCouponCode().trim().isEmpty()) {
                protoRequestBuilder.setCouponCode(request.getCouponCode());
            }

            if (request.getOrderDate() != null && !request.getOrderDate().trim().isEmpty()) {
                protoRequestBuilder.setOrderDate(request.getOrderDate());
            }

            String requestId = request.getRequestId();
            if (requestId == null || requestId.trim().isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }
            protoRequestBuilder.setRequestId(requestId);

            OrderServiceProto.ProcessOrderResponse response = orderServiceClient.processOrder(protoRequestBuilder.build());

            if (response.getStatus().getCode() == OrderServiceProto.StatusCode.OK) {
                // Convert protobuf response to DTO
                ProcessOrderResponse orderResponse = ProcessOrderResponse.builder()
                        .orderId(response.getPayload().getOrderId())
                        .userId(response.getPayload().getUserId())
                        .orderAmount(response.getPayload().getOrderAmount())
                        .discountAmount(response.getPayload().getDiscountAmount())
                        .finalAmount(response.getPayload().getFinalAmount())
                        .couponCode(response.getPayload().getCouponCode())
                        .couponId(response.getPayload().getCouponId())
                        .orderDate(response.getPayload().getOrderDate())
                        .status(response.getPayload().getStatus())
                        .build();

                return ResponseEntity.ok(orderResponse);
            } else {
                // Convert protobuf error to DTO
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .code(response.getError().getCode())
                        .message(response.getError().getMessage())
                        .details(response.getError().getDetailsMap())
                        .build();

                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error in process order: {}", e.getMessage(), e);

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}

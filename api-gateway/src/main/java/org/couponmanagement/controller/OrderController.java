package org.couponmanagement.controller;

import jakarta.validation.Valid;
import org.couponmanagement.grpc.OrderServiceClient;
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
    public ResponseEntity<?> processOrder(@Valid @RequestBody OrderServiceProto.ProcessOrderRequest request) {
        try {
            log.info("Process order: user={}, amount={}", request.getUserId(), request.getOrderAmount());

            OrderServiceProto.ProcessOrderRequest.Builder requestBuilder = request.toBuilder();
            if (request.getRequestId().isEmpty()) {
                requestBuilder.setRequestId(UUID.randomUUID().toString());
            }

            OrderServiceProto.ProcessOrderResponse response = orderServiceClient.processOrder(requestBuilder.build());

            if (response.getStatus().getCode() == OrderServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
            } else {
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in process order: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
        }
    }
}

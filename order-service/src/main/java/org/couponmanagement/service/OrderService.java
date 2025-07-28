package org.couponmanagement.service;

import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.coupon.CouponServiceGrpc;
import org.couponmanagement.coupon.CouponServiceProto;
import org.couponmanagement.entity.Order;
import org.couponmanagement.grpc.client.GrpcClientFactory;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final GrpcClientFactory grpcClientFactory;
    private final RequestValidator validator;

    public ProcessOrderResult processOrderManual(ProcessOrderRequest request) {
        Integer couponId = null;
        try {
            log.info("Processing order with manual coupon: userId={}, couponCode={}, amount={}", 
                    request.getUserId(), request.getCouponCode(), request.getOrderAmount());

            validator.validateUserId(request.getUserId());
            validator.validateOrderAmount(request.getOrderAmount());
            validator.validateCouponCode(request.getCouponCode());

            CouponResult couponResult = callCouponServiceManual(request.getUserId(), 
                    request.getCouponCode(), request.getOrderAmount(),
                    request.getOrderDate());
            couponId = couponResult.getCouponId();
            if (!couponResult.isSuccess()) {
                if (couponId != null) {
                    rollbackCouponUsage(request.getUserId(), couponId);
                }
                return ProcessOrderResult.builder()
                        .success(false)
                        .errorMessage(couponResult.getErrorMessage())
                        .build();
            }

            Order order = createOrder(request, couponResult);

            return ProcessOrderResult.builder()
                    .success(true)
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .orderAmount(order.getOrderAmount())
                    .discountAmount(order.getDiscountAmount())
                    .finalAmount(order.getFinalAmount())
                    .couponCode(couponResult.getCouponCode())
                    .couponId(couponResult.getCouponId())
                    .orderDate(LocalDateTime.now())
                    .status("COMPLETED")
                    .build();

        } catch (Exception e) {
            log.error("Error processing manual order: {}", e.getMessage(), e);
            if (couponId != null) {
                rollbackCouponUsage(request.getUserId(), couponId);
            }
            return ProcessOrderResult.builder()
                    .success(false)
                    .errorMessage("Failed to process order: " + e.getMessage())
                    .build();
        }
    }

    public ProcessOrderResult processOrderAuto(ProcessOrderRequest request) {
        Integer couponId = null;
        try {
            log.info("Processing order with auto coupon: userId={}, amount={}", 
                    request.getUserId(), request.getOrderAmount());

            validator.validateUserId(request.getUserId());
            validator.validateOrderAmount(request.getOrderAmount());

            CouponResult couponResult = callCouponServiceAuto(request.getUserId(), request.getOrderAmount(), request.getOrderDate());
            couponId = couponResult.getCouponId();
            if (!couponResult.isSuccess()) {
                if (couponId != null) {
                    rollbackCouponUsage(request.getUserId(), couponId);
                }
                couponResult = CouponResult.builder()
                        .success(true)
                        .discountAmount(BigDecimal.ZERO)
                        .build();
            }

            Order order = createOrder(request, couponResult);

            return ProcessOrderResult.builder()
                    .success(true)
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .orderAmount(order.getOrderAmount())
                    .discountAmount(order.getDiscountAmount())
                    .finalAmount(order.getFinalAmount())
                    .couponCode(couponResult.getCouponCode())
                    .couponId(couponResult.getCouponId())
                    .orderDate(LocalDateTime.now())
                    .status("COMPLETED")
                    .build();

        } catch (Exception e) {
            log.error("Error processing auto order: {}", e.getMessage(), e);
            if (couponId != null) {
                rollbackCouponUsage(request.getUserId(), couponId);
            }
            return ProcessOrderResult.builder()
                    .success(false)
                    .errorMessage("Failed to process order: " + e.getMessage())
                    .build();
        }
    }

    private CouponResult callCouponServiceManual(Integer userId, String couponCode, Double orderAmount, LocalDateTime orderDate) {
        try {
            Channel channel = grpcClientFactory.getCouponServiceChannel();
            CouponServiceGrpc.CouponServiceBlockingStub stub = CouponServiceGrpc.newBlockingStub(channel);

            var grpcRequest = CouponServiceProto.ApplyCouponManualRequest.newBuilder()
                    .setUserId(userId)
                    .setCouponCode(couponCode)
                    .setOrderAmount(orderAmount)
                    .setOrderDate(orderDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .build();

            var grpcResponse = stub.applyCouponManual(grpcRequest);

            if (grpcResponse.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                var payload = grpcResponse.getPayload();
                return CouponResult.builder()
                        .success(true)
                        .couponId(payload.getCouponId())
                        .couponCode(payload.getCouponCode())
                        .discountAmount(BigDecimal.valueOf(payload.getDiscountAmount()))
                        .build();
            } else {
                return CouponResult.builder()
                        .success(false)
                        .errorMessage(grpcResponse.getStatus().getMessage())
                        .build();
            }

        } catch (StatusRuntimeException e) {
            log.error("gRPC error calling coupon service: {}", e.getStatus());
            return CouponResult.builder()
                    .success(false)
                    .errorMessage("Coupon service unavailable")
                    .build();
        }
    }


    private CouponResult callCouponServiceAuto(Integer userId, Double orderAmount, LocalDateTime orderDate) {
        try {
            Channel channel = grpcClientFactory.getCouponServiceChannel();
            CouponServiceGrpc.CouponServiceBlockingStub stub = CouponServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(5, TimeUnit.SECONDS);

            var grpcRequest = CouponServiceProto.ApplyCouponAutoRequest.newBuilder()
                    .setUserId(userId)
                    .setOrderAmount(orderAmount)
                    .setOrderDate(orderDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .build();

            var grpcResponse = stub.applyCouponAuto(grpcRequest);

            if (grpcResponse.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                var payload = grpcResponse.getPayload();
                return CouponResult.builder()
                        .success(true)
                        .couponId(payload.getCouponId())
                        .couponCode(payload.getCouponCode())
                        .discountAmount(BigDecimal.valueOf(payload.getDiscountAmount()))
                        .build();
            } else {
                return CouponResult.builder()
                        .success(false)
                        .errorMessage(grpcResponse.getStatus().getMessage())
                        .build();
            }

        } catch (StatusRuntimeException e) {
            log.error("gRPC error calling coupon service: {}", e.getStatus());
            return CouponResult.builder()
                    .success(false)
                    .errorMessage("No suitable coupon found")
                    .build();
        }
    }

    private Order createOrder(ProcessOrderRequest request, CouponResult couponResult) {
        BigDecimal orderAmount = BigDecimal.valueOf(request.getOrderAmount());
        BigDecimal discountAmount = couponResult.getDiscountAmount() != null ? 
                couponResult.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal finalAmount = orderAmount.subtract(discountAmount);

        Order order = Order.builder()
                .userId(request.getUserId())
                .orderAmount(orderAmount)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .couponId(couponResult.getCouponId())
                .build();

        return orderRepository.save(order);
    }

    private void rollbackCouponUsage(Integer userId, Integer couponId) {
        try {
            Channel channel = grpcClientFactory.getCouponServiceChannel();
            CouponServiceGrpc.CouponServiceBlockingStub stub = CouponServiceGrpc.newBlockingStub(channel);
            var rollbackRequest = CouponServiceProto.RollbackCouponUsageRequest.newBuilder()
                    .setUserId(userId)
                    .setCouponId(couponId)
                    .build();
            var rollbackResponse = stub.rollbackCouponUsage(rollbackRequest);
            if (rollbackResponse.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                log.info("Rolled back coupon usage for userId={}, couponId={}", userId, couponId);
            } else {
                log.warn("Failed to rollback coupon usage for userId={}, couponId={}, message={}", userId, couponId, rollbackResponse.getStatus().getMessage());
            }
        } catch (Exception ex) {
            log.error("Exception when calling rollbackCouponUsage: userId={}, couponId={}, error={}", userId, couponId, ex.getMessage(), ex);
        }
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessOrderRequest {
        private Integer userId;
        private Double orderAmount;
        private String couponCode;
        private String requestId;
        private LocalDateTime orderDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessOrderResult {
        private boolean success;
        private String errorMessage;
        private Integer orderId;
        private Integer userId;
        private BigDecimal orderAmount;
        private BigDecimal discountAmount;
        private BigDecimal finalAmount;
        private String couponCode;
        private Integer couponId;
        private LocalDateTime orderDate;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CouponResult {
        private boolean success;
        private String errorMessage;
        private Integer couponId;
        private String couponCode;
        private BigDecimal discountAmount;
    }
}

package org.couponmanagement.service;

import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import io.micrometer.observation.annotation.Observed;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.coupon.CouponServiceGrpc;
import org.couponmanagement.coupon.CouponServiceProto;
import org.couponmanagement.dto.CouponResult;
import org.couponmanagement.dto.OrderError;
import org.couponmanagement.dto.ProcessOrderRequest;
import org.couponmanagement.dto.ProcessOrderResult;
import org.couponmanagement.entity.Order;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.grpc.client.GrpcClientFactory;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.grpc.validation.ValidationException;
import org.couponmanagement.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final GrpcClientFactory grpcClientFactory;
    private final RequestValidator validator;

    @Observed(name = "process-order-manual", contextualName = "manual-order-processing")
    @PerformanceMonitor
    public ProcessOrderResult processOrderManual(Integer userId, String couponCode, Double orderAmount, LocalDateTime orderDate, String requestId) {
        Integer couponId = null;
        ProcessOrderRequest request = ProcessOrderRequest.builder()
                .userId(userId)
                .requestId(requestId)
                .couponCode(couponCode)
                .orderAmount(orderAmount)
                .orderDate(orderDate)
                .build();
        try {
            CouponResult couponResult = callCouponServiceManual(request.userId(),
                    request.couponCode(), request.orderAmount(),
                    request.orderDate());
            couponId = couponResult.couponId();
            if (!couponResult.success()) {
                if (couponId != null) {
                    rollbackCouponUsage(request.userId(), couponId);
                }
                return ProcessOrderResult.builder()
                        .success(false)
                        .errorMessage(couponResult.errorMessage())
                        .errorCode(couponResult.errorCode())
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
                    .couponCode(couponResult.couponCode())
                    .couponId(couponResult.couponId())
                    .orderDate(LocalDateTime.now())
                    .status("COMPLETED")
                    .build();

        } catch (Exception e) {
            log.error("Error processing manual order: {}", e.getMessage(), e);
            if (couponId != null) {
                rollbackCouponUsage(request.userId(), couponId);
            }
            return ProcessOrderResult.builder()
                    .success(false)
                    .errorMessage("Failed to process order: " + e.getMessage())
                    .build();
        }
    }

    @Observed(name = "process-order-auto", contextualName = "auto-order-processing")
    @PerformanceMonitor
    @Transactional(rollbackFor = Exception.class)
    public ProcessOrderResult processOrderAuto(ProcessOrderRequest request) {
        Integer couponId = null;
        try {
            log.info("Processing order with auto coupon: userId={}, amount={}", 
                    request.userId(), request.orderAmount());

            validator.validateUserId(request.userId());
            validator.validateOrderAmount(request.orderAmount());

            CouponResult couponResult = callCouponServiceAuto(request.userId(), request.orderAmount(), request.orderDate());
            couponId = couponResult.couponId();
            if (!couponResult.success()) {
                if (couponId != null) {
                    rollbackCouponUsage(request.userId(), couponId);
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
                    .couponCode(couponResult.couponCode())
                    .couponId(couponResult.couponId())
                    .orderDate(LocalDateTime.now())
                    .status("COMPLETED")
                    .build();

        } catch (ValidationException e){
            log.error("Validation error processing auto order: {}", e.getMessage());
            return ProcessOrderResult.builder()
                    .success(false)
                    .errorMessage("Validation error: " + e.getMessage())
                    .errorCode(OrderError.INVALID_ARGUMENT.name())
                    .build();
        }
        catch (Exception e) {
            log.error("Error processing auto order: {}", e.getMessage(), e);
            if (couponId != null) {
                rollbackCouponUsage(request.userId(), couponId);
            }
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
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
                if (payload.getSuccess()){
                    return CouponResult.builder()
                            .success(true)
                            .couponId(payload.getCouponId())
                            .couponCode(payload.getCouponCode())
                            .discountAmount(BigDecimal.valueOf(payload.getDiscountAmount()))
                            .build();
                } else {
                    return CouponResult.builder()
                            .success(false)
                            .errorMessage(payload.getErrorMessage())
                            .errorCode(grpcResponse.getError().getCode())
                            .build();
                }
            } else {
                return CouponResult.builder()
                        .success(false)
                        .errorMessage(grpcResponse.getStatus().getMessage())
                        .errorCode(grpcResponse.getStatus().getCode().name())
                        .build();
            }

        } catch (StatusRuntimeException e) {
            log.error("gRPC error calling coupon service: {}", e.getStatus());
            return CouponResult.builder()
                    .success(false)
                    .errorMessage("Coupon service unavailable")
                    .errorCode(OrderError.INTERNAL_ERROR.name())
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

    @Transactional
    private Order createOrder(ProcessOrderRequest request, CouponResult couponResult) {
        BigDecimal orderAmount = BigDecimal.valueOf(request.orderAmount());
        BigDecimal discountAmount = couponResult.discountAmount() != null ?
                couponResult.discountAmount() : BigDecimal.ZERO;
        BigDecimal finalAmount = orderAmount.subtract(discountAmount);

        Order order = Order.builder()
                .userId(request.userId())
                .orderAmount(orderAmount)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .couponId(couponResult.couponId())
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
}

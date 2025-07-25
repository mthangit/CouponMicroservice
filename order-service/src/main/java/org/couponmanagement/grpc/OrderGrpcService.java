package org.couponmanagement.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.couponmanagement.grpc.annotation.RequireAuth;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.order.OrderServiceGrpc;
import org.couponmanagement.order.OrderServiceProto;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.service.OrderService;
import org.springframework.util.StringUtils;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private final OrderService orderService;
    private final RequestValidator validator;

    @Override
    @RequireAuth("PROCESS_ORDER")
    @PerformanceMonitor()
    public void processOrder(OrderServiceProto.ProcessOrderRequest request,
                           StreamObserver<OrderServiceProto.ProcessOrderResponse> responseObserver) {
        
        log.info("Received processOrder gRPC request: userId={}, amount={}, couponCode={}, orderDate={}",
                request.getUserId(), request.getOrderAmount(), request.getCouponCode(), request.getOrderDate());

        try {
            validator.validateUserId(request.getUserId());
            validator.validateOrderAmount(request.getOrderAmount());
            validator.validateRequestId(request.getRequestId());

            OrderService.ProcessOrderRequest serviceRequest = OrderService.ProcessOrderRequest.builder()
                    .userId(request.getUserId())
                    .orderAmount(request.getOrderAmount())
                    .couponCode(request.getCouponCode().isEmpty() ? null : request.getCouponCode())
                    .requestId(request.getRequestId())
                    .orderDate(java.time.LocalDateTime.parse(request.getOrderDate()))
                    .build();

            OrderService.ProcessOrderResult result;
            if (StringUtils.hasText(request.getCouponCode())) {
                result = orderService.processOrderManual(serviceRequest);
            } else {
                result = orderService.processOrderAuto(serviceRequest);
            }

            OrderServiceProto.ProcessOrderResponse response;
            
            if (result.isSuccess()) {
                response = OrderServiceProto.ProcessOrderResponse.newBuilder()
                        .setStatus(OrderServiceProto.Status.newBuilder()
                                .setCode(OrderServiceProto.StatusCode.OK)
                                .setMessage("Order processed successfully")
                                .build())
                        .setPayload(OrderServiceProto.ProcessOrderPayload.newBuilder()
                                .setOrderId(result.getOrderId())
                                .setUserId(result.getUserId())
                                .setOrderAmount(result.getOrderAmount().doubleValue())
                                .setDiscountAmount(result.getDiscountAmount().doubleValue())
                                .setFinalAmount(result.getFinalAmount().doubleValue())
                                .setCouponCode(result.getCouponCode() != null ? result.getCouponCode() : "")
                                .setCouponId(result.getCouponId() != null ? result.getCouponId() : 0)
                                .setOrderDate(result.getOrderDate().toString())
                                .setStatus(result.getStatus())
                                .build())
                        .build();

                log.info("Order processed successfully: orderId={}, finalAmount={}", 
                        result.getOrderId(), result.getFinalAmount());
            } else {
                // Error response
                response = OrderServiceProto.ProcessOrderResponse.newBuilder()
                        .setStatus(OrderServiceProto.Status.newBuilder()
                                .setCode(OrderServiceProto.StatusCode.INVALID_ARGUMENT)
                                .setMessage("Order processing failed")
                                .build())
                        .setError(OrderServiceProto.Error.newBuilder()
                                .setCode("ORDER_PROCESSING_FAILED")
                                .setMessage(result.getErrorMessage())
                                .build())
                        .build();

                log.warn("Order processing failed: {}", result.getErrorMessage());
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in processOrder gRPC call: userId={}, error={}", 
                     request.getUserId(), e.getMessage(), e);

            OrderServiceProto.ProcessOrderResponse errorResponse = OrderServiceProto.ProcessOrderResponse.newBuilder()
                    .setStatus(OrderServiceProto.Status.newBuilder()
                            .setCode(OrderServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(OrderServiceProto.Error.newBuilder()
                            .setCode("INTERNAL_ERROR")
                            .setMessage("Internal server error: " + e.getMessage())
                            .build())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }
}

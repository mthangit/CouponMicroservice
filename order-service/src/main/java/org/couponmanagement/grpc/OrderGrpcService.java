package org.couponmanagement.grpc;

import io.grpc.stub.StreamObserver;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.couponmanagement.dto.OrderError;
import org.couponmanagement.dto.ProcessOrderRequest;
import org.couponmanagement.dto.ProcessOrderResult;
import org.couponmanagement.grpc.annotation.RequireAuth;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.order.OrderServiceGrpc;
import org.couponmanagement.order.OrderServiceProto;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.service.OrderService;
import org.couponmanagement.utils.DateTimeUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private final OrderService orderService;
    private final RequestValidator validator;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final DateTimeUtils dateTimeUtils;

    @Override
    @RequireAuth("PROCESS_ORDER")
    @PerformanceMonitor
    @Observed(name = "processOrderGrpcCall", contextualName = "OrderGrpcService.processOrder")
    public void processOrder(OrderServiceProto.ProcessOrderRequest request,
                           StreamObserver<OrderServiceProto.ProcessOrderResponse> responseObserver) {
        
        log.info("Received processOrder gRPC request: userId={}, amount={}, couponCode={}, orderDate={}",
                request.getUserId(), request.getOrderAmount(), request.getCouponCode(), request.getOrderDate());

        try {
            validator.validateUserId(request.getUserId());
            validator.validateOrderAmount(request.getOrderAmount());
            validator.validateRequestId(request.getRequestId());

            ProcessOrderResult result = processOrderInternal(
                    request.getUserId(),
                    request.getCouponCode(),
                    request.getOrderAmount(),
                    dateTimeUtils.parseOrderDate(request.getOrderDate()),
                    request.getRequestId()
            );

            OrderServiceProto.ProcessOrderResponse response;
            
            if (result.success()) {
                response = OrderServiceProto.ProcessOrderResponse.newBuilder()
                        .setStatus(OrderServiceProto.Status.newBuilder()
                                .setCode(OrderServiceProto.StatusCode.OK)
                                .setMessage("Order processed successfully")
                                .build())
                        .setPayload(OrderServiceProto.ProcessOrderPayload.newBuilder()
                                .setOrderId(result.orderId())
                                .setUserId(result.userId())
                                .setOrderAmount(result.orderAmount().doubleValue())
                                .setDiscountAmount(result.discountAmount().doubleValue())
                                .setFinalAmount(result.finalAmount().doubleValue())
                                .setCouponCode(result.couponCode() != null ? result.couponCode() : "")
                                .setCouponId(result.couponId() != null ? result.couponId() : 0)
                                .setOrderDate(result.orderDate().toString())
                                .setStatus(result.status())
                                .build())
                        .build();

                log.info("Order processed successfully: orderId={}, finalAmount={}", 
                        result.orderAmount(), result.finalAmount());
            } else {
                response = OrderServiceProto.ProcessOrderResponse.newBuilder()
                        .setStatus(OrderServiceProto.Status.newBuilder()
                                .setCode(OrderServiceProto.StatusCode.INVALID_ARGUMENT)
                                .setMessage("Order processing failed")
                                .build())
                        .setError(OrderServiceProto.Error.newBuilder()
                                .setCode(result.errorCode())
                                .setMessage(result.errorMessage())
                                .build())
                        .build();

                log.warn("Order processing failed: {}", result.errorMessage());
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
                            .setCode(OrderError.INTERNAL_ERROR.name())
                            .setMessage("Internal server error: " + e.getMessage())
                            .build())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Observed(name = "OrderGrpcService.processOrderInternal")
    @PerformanceMonitor
    private ProcessOrderResult processOrderInternal(Integer userId, String couponCode, Double orderAmount, LocalDateTime orderDate, String requestId){
        if (couponCode != null) {
            return orderService.processOrderManual(userId, couponCode, orderAmount, orderDate, requestId);
        } else {
            ProcessOrderRequest serviceRequest = new ProcessOrderRequest(
                    userId,
                    orderAmount,
                    null,
                    requestId,
                    orderDate
            );
            return orderService.processOrderAuto(serviceRequest);
        }
    }
}

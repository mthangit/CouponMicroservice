package org.couponmanagement.grpc;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.couponmanagement.order.*;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrderServiceClient {

    @GrpcClient("order-service")
    private OrderServiceGrpc.OrderServiceBlockingStub orderServiceStub;

    public OrderServiceProto.ProcessOrderResponse processOrder(OrderServiceProto.ProcessOrderRequest request) {
        try {
            log.info("Calling order service - process: user={}, amount={}",
                    request.getUserId(), request.getOrderAmount());
            return orderServiceStub.processOrder(request);
        } catch (Exception e) {
            log.error("Error in processOrder: {}", e.getMessage(), e);
            throw e;
        }
    }
}

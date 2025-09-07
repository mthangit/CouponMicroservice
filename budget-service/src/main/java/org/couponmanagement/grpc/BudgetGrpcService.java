package org.couponmanagement.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.couponmanagement.budget.BudgetServiceGrpc;
import org.couponmanagement.budget.BudgetServiceProto;
import org.couponmanagement.dto.*;
import org.couponmanagement.service.BudgetService;

import java.math.BigDecimal;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class BudgetGrpcService extends BudgetServiceGrpc.BudgetServiceImplBase {

    private final BudgetService budgetService;

    @Override
    public void register(BudgetServiceProto.RegisterBudgetCouponRequest request,
                         StreamObserver<BudgetServiceProto.RegisterBudgetCouponResponse> responseObserver) {
        RegisterBudgetRequest req = RegisterBudgetRequest.builder()
                .requestId(request.getRequestId())
                .counponUserId(request.getCouponUserId())
                .userId(request.getUserId())
                .couponId(request.getCouponId())
                .budgetId(request.getBudgetId())
                .discountAmount(BigDecimal.valueOf(request.getDiscountAmount()))
                .build();

        RegisterBudgetResponse res = budgetService.registerBudgetCoupon(req);

        BudgetServiceProto.RegisterBudgetCouponResponse.Builder builder = BudgetServiceProto.RegisterBudgetCouponResponse.newBuilder();
        BudgetServiceProto.StatusCode statusCode = mapToProtoStatusCode(res);
        BudgetServiceProto.Status status = BudgetServiceProto.Status.newBuilder()
                .setCode(statusCode)
                .setMessage(res.isSuccess() ? "OK" : res.getMessage())
                .build();

        builder.setStatus(status);
        if (res.isSuccess()) {
            BudgetServiceProto.RegisterBudgetCouponResponsePayload payload = BudgetServiceProto.RegisterBudgetCouponResponsePayload.newBuilder()
                    .setSuccess(true)
                    .setMessage(res.getMessage() == null ? "" : res.getMessage())
                    .build();
            builder.setPayload(payload);
        } else {
            BudgetServiceProto.Error error = BudgetServiceProto.Error.newBuilder()
                    .setCode(mapToErrorCode(res))
                    .setMessage(res.getMessage() == null ? "" : res.getMessage())
                    .build();
            builder.setError(error);
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }



    private BudgetServiceProto.StatusCode mapToProtoStatusCode(RegisterBudgetResponse res) {
        if (res.isSuccess()) return BudgetServiceProto.StatusCode.OK;
        BudgetErrorCode code = res.getErrorCode();
        if (code == null) return BudgetServiceProto.StatusCode.INTERNAL;
        return switch (code) {
            case INVALID_ARGUMENT, INSUFFICIENT_BUDGET, ALREADY_RESERVED -> BudgetServiceProto.StatusCode.INVALID_ARGUMENT;
            case NOT_FOUND -> BudgetServiceProto.StatusCode.NOT_FOUND;
            case SERVICE_UNAVAILABLE, INTERNAL, NONE, ROLLBACK_FAILED -> BudgetServiceProto.StatusCode.INTERNAL;
        };
    }

    private String mapToErrorCode(RegisterBudgetResponse res) {
        BudgetErrorCode code = res.getErrorCode();
        if (code == null) return "INTERNAL";
        return code.name();
    }
}

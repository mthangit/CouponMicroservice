package org.couponmanagement.grpc.interceptor;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.grpc.auth.TrustedCallersProperties;
import org.couponmanagement.grpc.client.AuthContext;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class GrpcServerInterceptor implements ServerInterceptor {

    private final TrustedCallersProperties trustedCallersProperties;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String serviceId = headers.get(Metadata.Key.of("x-service-id", Metadata.ASCII_STRING_MARSHALLER));
        String clientKey = headers.get(Metadata.Key.of("x-client-key", Metadata.ASCII_STRING_MARSHALLER));

        if (serviceId == null || clientKey == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing x-service-id or x-client-key in headers"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        if (!trustedCallersProperties.getCallers().containsKey(serviceId)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Unknown service: " + serviceId), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        if (!trustedCallersProperties.getCallers().get(serviceId).getClientKey().equals(clientKey)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid client-key for service: " + serviceId), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        log.info("Authenticated service: {} for method: {}", serviceId, call.getMethodDescriptor().getFullMethodName());

        List<String> permissions = trustedCallersProperties.getCallers().get(serviceId).getPermissions();

        Context currentContext = Context.current();

        Context context = currentContext
                .withValue(AuthContext.SERVICE_ID_KEY, serviceId)
                .withValue(AuthContext.CLIENT_KEY_KEY, clientKey)
                .withValue(AuthContext.LIST_PERMISSIONS_KEY, permissions);

        return Contexts.interceptCall(context, call, headers, next);
    }
}
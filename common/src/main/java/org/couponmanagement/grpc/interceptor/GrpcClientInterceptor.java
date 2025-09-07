package org.couponmanagement.grpc.interceptor;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.grpc.client.GrpcClientMetadataProperties;

@Slf4j
public class GrpcClientInterceptor implements ClientInterceptor {

    private final GrpcClientMetadataProperties grpcClientMetadataProperties;

    public GrpcClientInterceptor(GrpcClientMetadataProperties grpcClientMetadataProperties) {
        this.grpcClientMetadataProperties = grpcClientMetadataProperties;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String serviceId = grpcClientMetadataProperties.getXServiceId();
                String clientKey = grpcClientMetadataProperties.getXClientKey();

                if (serviceId != null) {
                    headers.put(Metadata.Key.of("x-service-id", Metadata.ASCII_STRING_MARSHALLER), serviceId);
                } else {
                    log.warn(">>> x-service-id is NULL!");
                }

                if (clientKey != null) {
                    headers.put(Metadata.Key.of("x-client-key", Metadata.ASCII_STRING_MARSHALLER), clientKey);
                } else {
                    log.warn(">>> x-client-key is NULL!");
                }
                super.start(responseListener, headers);
            }
        };
    }
}
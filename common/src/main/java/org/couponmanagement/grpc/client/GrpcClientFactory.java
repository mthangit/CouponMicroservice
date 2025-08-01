package org.couponmanagement.grpc.client;

import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcClientInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.couponmanagement.grpc.interceptor.GrpcClientInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class GrpcClientFactory {
    
    private final GrpcClientMetadataProperties grpcClientMetadataProperties;

    @Value("${grpc.client.coupon-service.address:localhost:9091}")
    private String couponServiceAddress;
    
    @Value("${grpc.client.rule-service.address:localhost:9090}")
    private String ruleServiceAddress;
    
    @Value("${grpc.client.order-service.address:localhost:9093}")
    private String orderServiceAddress;
    
    private final ConcurrentHashMap<String, ManagedChannel> channels = new ConcurrentHashMap<>();


    private final ObservationGrpcClientInterceptor observationGrpcClientInterceptor;

    public GrpcClientFactory(GrpcClientMetadataProperties grpcClientMetadataProperties,
                             @Qualifier("clientInterceptor") ObservationGrpcClientInterceptor observationGrpcClientInterceptor) {
        this.grpcClientMetadataProperties = grpcClientMetadataProperties;
        this.observationGrpcClientInterceptor = observationGrpcClientInterceptor;
    }

    public Channel getCouponServiceChannel() {
        return getOrCreateChannel("coupon-service", couponServiceAddress);
    }
    
    public Channel getRuleServiceChannel() {
        return getOrCreateChannel("rule-service", ruleServiceAddress);
    }
    
    public Channel getOrderServiceChannel() {
        return getOrCreateChannel("order-service", orderServiceAddress);
    }
    
    private Channel getOrCreateChannel(String serviceName, String address) {
        ManagedChannel baseChannel = channels.computeIfAbsent(serviceName, k -> {
            log.info("Creating gRPC channel for service: {} at address: {}", serviceName, address);

            String cleanAddress = address;
            if (address.startsWith("static://")) {
                cleanAddress = address.substring("static://".length());
            }

            String[] parts = cleanAddress.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();

            log.info("Base gRPC channel created for service: {}", serviceName);
            return channel;
        });

        GrpcClientInterceptor interceptor = new GrpcClientInterceptor(grpcClientMetadataProperties);

        Channel interceptedChannel = ClientInterceptors.intercept(baseChannel, interceptor, observationGrpcClientInterceptor);
        log.info("Channel intercepted for service: {}", serviceName);
        return interceptedChannel;
    }

    public void shutdown() {
        channels.values().forEach(channel -> {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while shutting down gRPC channel", e);
            }
        });
        channels.clear();
    }
}

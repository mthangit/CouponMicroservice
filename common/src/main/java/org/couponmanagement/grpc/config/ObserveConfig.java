package org.couponmanagement.grpc.config;

import io.micrometer.core.instrument.binder.grpc.ObservationGrpcClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ObserveConfig {
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    @Bean
    public ObservationGrpcServerInterceptor serverInterceptor(ObservationRegistry observationRegistry) {
        return new ObservationGrpcServerInterceptor(observationRegistry);
    }

    @Bean
    @Primary
    public ObservationGrpcClientInterceptor clientInterceptor(ObservationRegistry observationRegistry) {
        return new ObservationGrpcClientInterceptor(observationRegistry);
    }
}

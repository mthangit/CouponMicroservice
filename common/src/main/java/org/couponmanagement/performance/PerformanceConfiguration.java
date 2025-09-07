package org.couponmanagement.performance;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Cấu hình performance monitoring cho các service
 * Đã loại bỏ custom MeterRegistry bean để sử dụng Spring Boot auto-configuration
 * với micrometer-registry-prometheus dependency
 */
@Configuration
@EnableAspectJAutoProxy
public class PerformanceConfiguration {
    // Đã xóa custom MeterRegistry bean
    // Spring Boot sẽ tự động tạo PrometheusMeterRegistry
    // khi có micrometer-registry-prometheus dependency
}

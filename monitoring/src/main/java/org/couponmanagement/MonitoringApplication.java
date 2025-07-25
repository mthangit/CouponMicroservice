package org.couponmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Monitoring Service Application
 * Provides comprehensive monitoring, metrics, and observability
 */
@SpringBootApplication
@EnableScheduling
public class MonitoringApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MonitoringApplication.class, args);
    }
}

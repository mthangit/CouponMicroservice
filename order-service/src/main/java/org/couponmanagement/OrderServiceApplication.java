package org.couponmanagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {
    "org.couponmanagement",
    "org.couponmanagement.grpc.config",
    "org.couponmanagement.grpc.interceptor",
    "org.couponmanagement.grpc.headers",
    "org.couponmanagement.grpc.auth",
    "org.couponmanagement.performance"
})
@EnableJpaRepositories
@EnableTransactionManagement
@EnableAspectJAutoProxy
@EnableAsync
@Slf4j
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

package org.couponmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {
    "org.couponmanagement",
    "org.couponmanagement.grpc.config",
    "org.couponmanagement.grpc.interceptor",
    "org.couponmanagement.grpc.headers",
    "org.couponmanagement.grpc.auth"
})
@EnableJpaRepositories(basePackages = "org.couponmanagement.repository")
@EnableRedisRepositories
@EnableAspectJAutoProxy
@EnableTransactionManagement
public class RuleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleServiceApplication.class, args);
    }
}
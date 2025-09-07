package org.couponmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {
    "org.couponmanagement",
    "org.couponmanagement.grpc.config",
    "org.couponmanagement.grpc.interceptor",
    "org.couponmanagement.grpc.headers",
    "org.couponmanagement.grpc.auth",
    "org.couponmanagement.performance"
},
exclude = {KafkaAutoConfiguration.class})
@EnableJpaRepositories(basePackages = "org.couponmanagement.repository")
@EnableRedisRepositories
@EnableAspectJAutoProxy
@EnableTransactionManagement
@EnableAsync
public class CouponServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponServiceApplication.class, args);
    }
}

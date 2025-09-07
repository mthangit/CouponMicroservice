package org.couponmanagement.grpc.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationPredicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

@Slf4j
@Configuration
public class TracingConfig {
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Bean
    public ObservationPredicate skipTracingForSpecificEndpoints() {
        return (name, context) -> {
            if (!"http.server.requests".equals(name)) {
                return true;
            }

            // Safe null checking
            String targetPath = null;

            // Try route first
            KeyValue routeKeyValue = context.getLowCardinalityKeyValue("http.route");
            if (routeKeyValue != null) {
                targetPath = routeKeyValue.getValue();
            } else {
                KeyValue urlKeyValue = context.getLowCardinalityKeyValue("http.url");
                if (urlKeyValue != null && urlKeyValue.getValue() != null) {
                    log.info("Extracting target path from URL: {}", urlKeyValue.getValue());
                    targetPath = urlKeyValue.getValue().split("\\?")[0];
                    log.info("Determined target path: {}", targetPath);
                }
            }

            if (targetPath == null || targetPath.isEmpty()) {
                log.info("Unable to determine target path for tracing, continuing tracing");
                return true; // Không xác định được path -> vẫn tracing
            }

            // Check exclude patterns
            String[] excludePatterns = {
                    "/actuator/**", "/swagger-ui/**", "/api-docs/**",
                    "/v3/api-docs/**", "/favicon.ico", "/error"
            };

            for (String pattern : excludePatterns) {
                if (pathMatcher.match(pattern, targetPath)) {
                    return false;
                }
            }

            return true;
        };
    }}

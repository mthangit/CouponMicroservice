package org.couponmanagement.performance;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CustomMetricsRegistry {

    private final MeterRegistry meterRegistry;

    public CustomMetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordExecutionTime(String methodName, String className, long durationMs, boolean success) {
        Timer.builder("method_execution_time_seconds")
                .description("Method execution time in seconds")
                .tag("class", className)
                .tag("method", methodName)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void incrementMethodCalls(String methodName, String className, boolean success) {
        Counter.builder("method_calls_total")
                .description("Total number of method calls")
                .tag("class", className)
                .tag("method", methodName)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();
    }

    public void recordMemoryUsage(String methodName, String className, long memoryBytes) {
        DistributionSummary.builder("method_memory_bytes")
                .description("Memory used by method in bytes")
                .tag("class", className)
                .tag("method", methodName)
                .register(meterRegistry)
                .record(memoryBytes);
    }

    public void recordCpuUsage(String methodName, String className, double cpuPercent) {
        Gauge.builder("method_cpu_usage_percent", () -> cpuPercent)
                .description("CPU usage percentage for method")
                .tag("class", className)
                .tag("method", methodName)
                .register(meterRegistry);    }
}

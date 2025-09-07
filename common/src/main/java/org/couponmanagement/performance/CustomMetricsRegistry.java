package org.couponmanagement.performance;

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CustomMetricsRegistry {

    private final MeterRegistry meterRegistry;

    public CustomMetricsRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordExecutionTime(String methodName, String className, long durationMs, boolean success) {
        meterRegistry.timer(
                        "method_duration_seconds",
                        "class", className,
                        "method", methodName,
                        "success", String.valueOf(success)
                )
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void incrementMethodCalls(String methodName, String className, boolean success) {
        meterRegistry.counter(
                        "method_calls_total",
                        "class", className,
                        "method", methodName,
                        "success", String.valueOf(success)
                )
                .increment();
    }

    private final Map<String, AtomicDouble> gaugeHolders = new ConcurrentHashMap<>();

    public void recordCpuUsage(String methodName, String className, double cpuPercent) {
        String key = className + "." + methodName;
        gaugeHolders.computeIfAbsent(key, k -> {
            AtomicDouble holder = new AtomicDouble();
            Gauge.builder("method_cpu_usage_percent", holder, AtomicDouble::get)
                    .description("CPU usage percentage for method")
                    .tags("class", className, "method", methodName)
                    .register(meterRegistry);
            return holder;
        });
        gaugeHolders.get(key).set(cpuPercent);
    }

    public void recordBudgetUsageFailure(Integer budgetId) {
        meterRegistry.counter(
                "budget_usage_failures",
                "budgetId", String.valueOf(budgetId))
                .increment();
    }
}
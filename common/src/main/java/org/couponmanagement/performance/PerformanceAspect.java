package org.couponmanagement.performance;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

@Aspect
@Component
@Slf4j
public class PerformanceAspect {
    
    @Autowired
    private CustomMetricsRegistry metricsRegistry;

    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    static {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
            threadMXBean.setThreadCpuTimeEnabled(true);
        }
    }
    
    @Around("@annotation(performanceMonitor)")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint, PerformanceMonitor performanceMonitor) throws Throwable {
        
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        long startTime = System.currentTimeMillis();
        long startMemory = getUsedMemory();
        long startCpuTime = getCpuTime();

        boolean success = true;

        try {
            return joinPoint.proceed();

        } catch (Throwable throwable) {
            success = false;
            throw throwable;
            
        } finally {
            long endTime = System.currentTimeMillis();
            long endMemory = getUsedMemory();
            long endCpuTime = getCpuTime();

            long executionTime = endTime - startTime;
            long memoryUsed = Math.max(0, endMemory - startMemory);
            double cpuUsagePercent = calculateCpuUsage(startCpuTime, endCpuTime, executionTime);

            // Record metrics to Prometheus only
            metricsRegistry.recordExecutionTime(methodName, className, executionTime, success);
            metricsRegistry.incrementMethodCalls(methodName, className, success);

            if (performanceMonitor.monitorMemory()) {
                metricsRegistry.recordMemoryUsage(methodName, className, memoryUsed);
            }

            if (performanceMonitor.monitorCpu() && cpuUsagePercent > 0) {
                metricsRegistry.recordCpuUsage(methodName, className, cpuUsagePercent);
            }
        }
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    private long getCpuTime() {
        if (threadBean.isCurrentThreadCpuTimeSupported()) {
            return threadBean.getCurrentThreadCpuTime();
        }
        return 0;
    }
    
    private double calculateCpuUsage(long startCpuTime, long endCpuTime, long executionTimeMs) {
        if (startCpuTime == 0 || endCpuTime == 0 || executionTimeMs == 0) {
            return 0;
        }
        long cpuTimeNs = endCpuTime - startCpuTime;
        long executionTimeNs = executionTimeMs * 1_000_000;
        return Math.min(100.0, (double) cpuTimeNs / executionTimeNs * 100);
    }
}

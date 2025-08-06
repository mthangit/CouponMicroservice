package org.couponmanagement.grpc.config;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import io.micrometer.tracing.Tracer;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@Slf4j
public class ThreadPoolConfig {

    @Value("${thread-pool.core-size:5}")
    private int corePoolSize;

    @Value("${thread-pool.max-size:10}")
    private int maxPoolSize;

    @Value("${thread-pool.queue-capacity:100}")
    private int queueCapacity;

    @Value("${thread-pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    private final ContextSnapshotFactory contextSnapshotFactory;
    private final Tracer tracer;

    public ThreadPoolConfig(ContextSnapshotFactory contextSnapshotFactory, Tracer tracer) {
        this.contextSnapshotFactory = contextSnapshotFactory;
        this.tracer = tracer;
        log.info("ThreadPoolConfig initialized with tracer: {}", tracer != null ? tracer.getClass().getSimpleName() : "NULL");
    }

    @Bean(name = "couponEvaluationExecutor")
    public Executor couponEvaluationExecutor() {
        return createSharedEvaluationExecutor("CouponEval-");
    }

    @Bean(name = "ruleEvaluationExecutor")
    public Executor ruleEvaluationExecutor() {
        return createSharedEvaluationExecutor("RuleEval-");
    }

    private Executor createSharedEvaluationExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);

        executor.initialize();

        log.info("Shared evaluation thread pool initialized: prefix={}, core={}, max={}, queue={}",
                threadNamePrefix, corePoolSize, maxPoolSize, queueCapacity);

        return new TraceAwareExecutor(executor, contextSnapshotFactory, tracer, threadNamePrefix);
    }

    private static class TraceAwareExecutor implements Executor {
        private final Executor delegate;
        private final ContextSnapshotFactory contextSnapshotFactory;
        private final Tracer tracer;
        private final String threadNamePrefix;

        public TraceAwareExecutor(Executor delegate, ContextSnapshotFactory contextSnapshotFactory, Tracer tracer, String threadNamePrefix) {
            this.delegate = delegate;
            this.contextSnapshotFactory = contextSnapshotFactory;
            this.tracer = tracer;
            this.threadNamePrefix = threadNamePrefix;
        }

        @Override
        public void execute(Runnable command) {
            ContextSnapshot snapshot = contextSnapshotFactory.captureAll();

            delegate.execute(() -> {
                try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                    var span = tracer.nextSpan().name(threadNamePrefix + "async-task").start();
                    try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                        log.debug("Starting async task with span: {} in thread: {}",
                                span.context().spanId(), Thread.currentThread().getName());
                        command.run();
                        log.debug("Completed async task with span: {}", span.context().spanId());
                    } catch (Exception e) {
                        span.error(e);
                        log.error("Error in async task with span: {}", span.context().spanId(), e);
                        throw e;
                    } finally {
                        span.end();
                    }
                }
            });
        }
    }
}


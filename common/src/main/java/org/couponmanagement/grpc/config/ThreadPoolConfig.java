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
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@Slf4j
public class ThreadPoolConfig {

    @Value("${thread-pool.use-virtual-threads:false}")
    private boolean useVirtualThreads;

    @Value("${thread-pool.core-size:50}")
    private int corePoolSize;

    @Value("${thread-pool.max-size:100}")
    private int maxPoolSize;

    @Value("${thread-pool.queue-capacity:300}")
    private int queueCapacity;

    @Value("${thread-pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    private final ContextSnapshotFactory contextSnapshotFactory;
    private final Tracer tracer;

    public ThreadPoolConfig(ContextSnapshotFactory contextSnapshotFactory, Tracer tracer) {
        this.contextSnapshotFactory = contextSnapshotFactory;
        this.tracer = tracer;
        log.info("ThreadPoolConfig initialized with tracer: {}, virtualThreads: {}",
                tracer != null ? tracer.getClass().getSimpleName() : "NULL", useVirtualThreads);
    }

    @Bean(name = "couponEvaluationExecutor")
    public Executor couponEvaluationExecutor() {
        return createSharedEvaluationExecutor("CouponEval-");
    }

    @Bean(name = "ruleEvaluationExecutor")
    public Executor ruleEvaluationExecutor() {
        return createSharedEvaluationExecutor("RuleEval-");
    }

    @Bean(name = "collectionRuleEvaluationExecutor")
    public Executor collectionRuleEvaluationExecutor() {
        return createSharedEvaluationExecutor("CollectionRuleEval-");
    }

    private Executor createSharedEvaluationExecutor(String threadNamePrefix) {
        Executor executor;

        if (useVirtualThreads) {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            log.info("Virtual thread executor initialized: prefix={}", threadNamePrefix);
        } else {
            ThreadPoolTaskExecutor threadPoolExecutor = new ThreadPoolTaskExecutor();
            threadPoolExecutor.setCorePoolSize(corePoolSize);
            threadPoolExecutor.setMaxPoolSize(maxPoolSize);
            threadPoolExecutor.setQueueCapacity(queueCapacity);
            threadPoolExecutor.setKeepAliveSeconds(keepAliveSeconds);
            threadPoolExecutor.setThreadNamePrefix(threadNamePrefix);
            threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
            threadPoolExecutor.setWaitForTasksToCompleteOnShutdown(true);
            threadPoolExecutor.setAwaitTerminationSeconds(20);
            threadPoolExecutor.initialize();

            executor = threadPoolExecutor;
            log.info("Platform thread pool initialized: prefix={}, core={}, max={}, queue={}",
                    threadNamePrefix, corePoolSize, maxPoolSize, queueCapacity);
        }

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
                        command.run();
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
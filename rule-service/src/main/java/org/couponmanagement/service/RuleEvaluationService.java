package org.couponmanagement.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.cache.RuleCacheService;
import org.couponmanagement.dto.RuleErrorCode;
import org.couponmanagement.engine.MinOrderAmountRuleHandler;
import org.couponmanagement.engine.RuleEvaluationContext;
import org.couponmanagement.engine.RuleHandler;
import org.couponmanagement.entity.Rule;
import org.couponmanagement.entity.RuleCollection;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.performance.ErrorMetricsRegistry;
import org.couponmanagement.repository.RuleCollectionRepository;
import org.couponmanagement.repository.RuleRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RuleEvaluationService {

    private final RuleCollectionRepository ruleCollectionRepository;
    private final RuleRepository ruleRepository;
    private final RuleCacheService ruleCacheService;
    private final Map<String, RuleHandler> ruleHandlerMap;
    private final Executor ruleEvaluationExecutor;
    private final Executor collectionRuleEvaluationExecutor;
    private final ErrorMetricsRegistry errorMetricsRegistry;


    private final ConcurrentHashMap<String, RuleHandler> handlerCache = new ConcurrentHashMap<>();

    public RuleEvaluationService(
            RuleCollectionRepository ruleCollectionRepository,
            RuleRepository ruleRepository,
            RuleCacheService ruleCacheService,
            @Qualifier("ruleHandlerMap") Map<String, RuleHandler> ruleHandlerMap,
            @Qualifier("ruleEvaluationExecutor") Executor ruleEvaluationExecutor,
            @Qualifier("collectionRuleEvaluationExecutor") Executor collectionRuleEvaluationExecutor,
            ErrorMetricsRegistry errorMetricsRegistry){
        this.ruleCollectionRepository = ruleCollectionRepository;
        this.ruleRepository = ruleRepository;
        this.ruleCacheService = ruleCacheService;
        this.ruleHandlerMap = ruleHandlerMap;
        this.ruleEvaluationExecutor = ruleEvaluationExecutor;
        this.collectionRuleEvaluationExecutor = collectionRuleEvaluationExecutor;
        this.errorMetricsRegistry = errorMetricsRegistry;
    }

    public record RuleCollectionEvaluationResult(
            Integer collectionId,
            boolean success,
            String errorMessage
    ) {}

    public record RuleCollectionWithRules(
            RuleCollection ruleCollection,
            List<Rule> rules
    ) {}

    public record RuleEvaluationResult(
            Integer ruleId,
            String ruleType,
            boolean success,
            String errorMessage,
            long evaluationTimeMs
    ) {}

    @Observed(name = "evaluate-rule-collection")
    public RuleCollectionEvaluationResult evaluateRuleCollection(Integer collectionId, RuleEvaluationContext context) {
        try {

            if (collectionId == 0) {
                return new RuleCollectionEvaluationResult(collectionId, true, null);
            }

            if (context == null || context.getOrderAmount() == null) {
                return new RuleCollectionEvaluationResult(collectionId, false, "Invalid evaluation context or order amount");
            }

            RuleCollectionWithRules collectionWithRules = loadRuleCollectionWithRules(collectionId);

            if (collectionWithRules == null) {
                return new RuleCollectionEvaluationResult(collectionId, false, "Rule collection not found: " + collectionId);
            }

            if (collectionWithRules.rules() == null || collectionWithRules.rules().isEmpty()) {
                return new RuleCollectionEvaluationResult(collectionId, false, "No rules found in collection: " + collectionId);
            }

            String errorMessage = evaluateRulesParallelAndCheckFailure(collectionWithRules.rules(), context);
            boolean success = (errorMessage == null);

            log.info("Rule collection {} evaluation completed: success={}, error={}", collectionId, success, errorMessage);
            return new RuleCollectionEvaluationResult(collectionId, success, errorMessage);

        } catch (Exception e) {
            log.error("Error evaluating rule collection {} with details", collectionId, e);
            return new RuleCollectionEvaluationResult(collectionId, false, "Internal error: " + e.getMessage());
        }
    }

    @Observed(name = "evaluate-single-rule", contextualName = "single-rule-evaluation")
    public boolean evaluateRule(Rule rule, RuleEvaluationContext context) {
        log.debug("Evaluating rule: id={}, type={}", rule.getId(), rule.getType());

        try {
            RuleHandler handler = getCachedHandler(rule.getType());
            if (handler == null) {
                log.error("No handler found for rule type: {}", rule.getType());
                return false;
            }

            String config = rule.getRuleConfiguration();
            if (config == null || config.trim().isEmpty()) {
                log.error("Rule configuration is null or empty for rule: {}", rule.getId());
                return false;
            }

            long startTime = System.currentTimeMillis();
            boolean result = handler.check(config, context);
            long evaluationTime = System.currentTimeMillis() - startTime;

            log.debug("Rule {} evaluation: type={}, result={}, time={}ms", 
                     rule.getId(), rule.getType(), result, evaluationTime);



            return result;

        } catch (Exception e) {
            log.error("Error evaluating rule {}: {}", rule.getId(), rule.getType(), e);
            return false;
        }
    }

    public List<RuleEvaluationResult> evaluateRules(List<Rule> rules, RuleEvaluationContext context) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }

        return rules.stream()
                .map(rule -> evaluateRuleWithDetails(rule, context))
                .collect(Collectors.toList());
    }

    @PerformanceMonitor
    @Observed(name = "evaluate-rules-parallel", contextualName = "RuleEvaluationService.evaluateRulesParallel")
    public List<RuleEvaluationResult> evaluateRulesParallel(List<Rule> rules, RuleEvaluationContext context) {
        if (rules == null || rules.isEmpty()) {
            return new ArrayList<>();
        }

        log.debug("Evaluating {} rules in parallel", rules.size());

        List<CompletableFuture<RuleEvaluationResult>> futures = rules.stream()
                .map(rule -> CompletableFuture.supplyAsync(() -> evaluateRuleWithDetails(rule, context), ruleEvaluationExecutor))
                .toList();

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Timeout or error during parallel rule evaluation", e);
            futures.forEach(future -> future.cancel(true));
        }

        return futures.parallelStream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        log.error("Error getting rule evaluation result", e);
                        return new RuleEvaluationResult(null, null, false, "Evaluation failed: " + e.getMessage(), 0);
                    }
                })
                .toList();
    }


    @Observed(name = "evaluate-rule-with-details")
    private RuleEvaluationResult evaluateRuleWithDetails(Rule rule, RuleEvaluationContext context) {
        long startTime = System.currentTimeMillis();
        
        try {
            RuleHandler handler = getCachedHandler(rule.getType());
            if (handler == null) {
                return new RuleEvaluationResult(
                    rule.getId(), rule.getType(), false, 
                    "No handler found for rule type: " + rule.getType(),
                    System.currentTimeMillis() - startTime
                );
            }

            String config = rule.getRuleConfiguration();
            if (config == null || config.trim().isEmpty()) {
                return new RuleEvaluationResult(
                    rule.getId(), rule.getType(), false,
                    "Rule configuration is null or empty",
                    System.currentTimeMillis() - startTime
                );
            }

            boolean result = handler.check(config, context);

            if (!result){
                if (handler instanceof MinOrderAmountRuleHandler){
                    errorMetricsRegistry.incrementBusinessError(String.valueOf(RuleErrorCode.MIN_ORDER_AMOUNT_NOT_MET), "RuleService");
                } else {
                    errorMetricsRegistry.incrementBusinessError(String.valueOf(RuleErrorCode.NOT_IN_TIME_RANGE), "RuleService");
                }
            }

            long evaluationTime = System.currentTimeMillis() - startTime;

            return new RuleEvaluationResult(
                rule.getId(), rule.getType(), result,
                result ? null : rule.getDescription(),
                evaluationTime
            );

        } catch (Exception e) {
            long evaluationTime = System.currentTimeMillis() - startTime;
            return new RuleEvaluationResult(
                rule.getId(), rule.getType(), false,
                "Exception during evaluation: " + e.getMessage(),
                evaluationTime
            );
        }
    }

    private RuleHandler getCachedHandler(String ruleType) {
        return handlerCache.computeIfAbsent(ruleType, type -> {
            RuleHandler handler = ruleHandlerMap.get(type);
            if (handler != null) {
                log.debug("Cached handler for rule type: {}", type);
            }
            return handler;
        });
    }

    @Observed(name = "evaluate-multiple-collections")
    @PerformanceMonitor
    public List<RuleCollectionEvaluationResult> evaluateMultipleCollections(List<Integer> collectionIds, RuleEvaluationContext context) {
        if (context == null || context.getOrderAmount() == null) {
            return List.of(new RuleCollectionEvaluationResult(null, false, "Invalid evaluation context or order amount"));
        }

        if (collectionIds == null) {
            return List.of(new RuleCollectionEvaluationResult(null, false, "No collection IDs provided"));
        }

        List<CompletableFuture<RuleCollectionEvaluationResult>> futures = collectionIds.stream().
                map(collectionId -> CompletableFuture.supplyAsync(() -> {
                    try{
                        return evaluateRuleCollection(collectionId, context);
                    } catch (Exception e) {
                        log.error("Error evaluating collection {}: ", collectionId, e);
                        return new RuleCollectionEvaluationResult(collectionId, false, "Internal error: " + e.getMessage());
                    }
                }, collectionRuleEvaluationExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }



    @Observed(name = "load-rule-collection-with-rules")
    private RuleCollectionWithRules loadRuleCollectionWithRules(Integer collectionId) {
        try {
            Optional<RuleCacheService.RuleCollectionCacheInfo> cachedCollectionInfo =
                    ruleCacheService.getCachedRuleCollection(collectionId);

            RuleCollection collection;
            List<Rule> rules = new ArrayList<>();

            if (cachedCollectionInfo.isPresent()) {
                RuleCacheService.RuleCollectionCacheInfo collectionInfo = cachedCollectionInfo.get();
                
                collection = new RuleCollection();
                collection.setId(collectionInfo.getCollectionId());
                collection.setName(collectionInfo.getName());
                collection.setRuleIds(collectionInfo.getRuleIds());

                List<Integer> ruleIdsList = collection.getRuleIdsList();
                if (!ruleIdsList.isEmpty()) {
                    rules = loadRulesFromCacheOrDatabase(ruleIdsList);
                }
            } else {
                Optional<RuleCollection> collectionOpt = ruleCollectionRepository.findById(collectionId);
                if (collectionOpt.isEmpty()) {
                    log.warn("Rule collection not found: {}", collectionId);
                    return null;
                }

                collection = collectionOpt.get();
                
                RuleCacheService.RuleCollectionCacheInfo collectionInfo = 
                    RuleCacheService.RuleCollectionCacheInfo.fromRuleCollection(collection);
                ruleCacheService.cacheRuleCollection(collectionId, collectionInfo);

                List<Integer> ruleIdsList = collection.getRuleIdsList();
                if (!ruleIdsList.isEmpty()) {
                    rules = loadRulesFromCacheOrDatabase(ruleIdsList);
                } else {
                    log.warn("No rule IDs found in collection: {}", collectionId);
                }
            }

            return new RuleCollectionWithRules(collection, rules);

        } catch (Exception e) {
            log.error("Error loading rule collection: {}", collectionId, e);
            return null;
        }
    }

    private List<Rule> loadRulesFromCacheOrDatabase(List<Integer> ruleIds) {
        List<Rule> rules = new ArrayList<>();
        List<Integer> uncachedRuleIds = new ArrayList<>();

        for (Integer ruleId : ruleIds) {
            Optional<RuleCacheService.RuleConfigCacheInfo> cachedRuleInfo = 
                ruleCacheService.getCachedRuleConfig(ruleId);
            
            if (cachedRuleInfo.isPresent()) {
                rules.add(convertCacheInfoToRule(cachedRuleInfo.get()));
            } else {
                uncachedRuleIds.add(ruleId);
            }
        }

        if (!uncachedRuleIds.isEmpty()) {
            List<Rule> dbRules = ruleRepository.findByIdIn(uncachedRuleIds);
            
            for (Rule rule : dbRules) {
                // Cache individual rule
                RuleCacheService.RuleConfigCacheInfo ruleInfo = 
                    RuleCacheService.RuleConfigCacheInfo.fromRule(rule);
                ruleCacheService.cacheRuleConfig(rule.getId(), ruleInfo);
                rules.add(rule);
            }
        }

        return rules;
    }

    private Rule convertCacheInfoToRule(RuleCacheService.RuleConfigCacheInfo cacheInfo) {
        Rule rule = new Rule();
        rule.setId(cacheInfo.getRuleId());
        rule.setType(cacheInfo.getRuleType());
        rule.setDescription(cacheInfo.getDescription());
        rule.setRuleConfiguration(cacheInfo.getRuleConfiguration());
        return rule;
    }

    @Observed(name = "evaluate-rules")
    private String evaluateRulesParallelAndCheckFailure(List<Rule> rules, RuleEvaluationContext context) {
        if (rules == null || rules.isEmpty()) {
            return "No rules to evaluate";
        }

        long startTime = System.currentTimeMillis();

        List<RuleEvaluationResult> results = evaluateRules(rules, context);

        String combinedErrorMessages = results.parallelStream()
                .filter(result -> !result.success())
                .map(RuleEvaluationResult::errorMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        long evaluationTime = System.currentTimeMillis() - startTime;

        return combinedErrorMessages.isEmpty() ? null : combinedErrorMessages;
    }

    public void shutdown() {
        if (ruleEvaluationExecutor != null && ruleEvaluationExecutor instanceof ThreadPoolTaskExecutor) {
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) ruleEvaluationExecutor;
            log.info("Shutting down rule evaluation thread pool");
            executor.shutdown();
            try {
                if (!executor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.getThreadPoolExecutor().shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}


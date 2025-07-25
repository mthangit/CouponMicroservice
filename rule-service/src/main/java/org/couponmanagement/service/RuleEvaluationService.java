package org.couponmanagement.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.cache.RuleCacheService;
import org.couponmanagement.engine.RuleEvaluationContext;
import org.couponmanagement.engine.RuleHandler;
import org.couponmanagement.entity.Rule;
import org.couponmanagement.entity.RuleCollection;
import org.couponmanagement.repository.RuleCollectionRepository;
import org.couponmanagement.repository.RuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEvaluationService {

    private final RuleCollectionRepository ruleCollectionRepository;
    private final RuleRepository ruleRepository;
    @Autowired
    @Qualifier("ruleHandlerMap")
    private final Map<String, RuleHandler> ruleHandlerMap;
    private final RuleCacheService ruleCacheService;

    public record RuleCollectionEvaluationResult(
            Integer collectionId,
            boolean success,
            String errorMessage
    ) {}

    public record RuleCollectionWithRules(
            RuleCollection ruleCollection,
            List<Rule> rules
    ) {}

    @Observed(name = "evaluate-rule-collection", contextualName = "rule-collection-evaluation")
    public RuleCollectionEvaluationResult evaluateRuleCollection(Integer collectionId, RuleEvaluationContext context) {
        log.info("Evaluating rule collection with context: id={}, context={}", collectionId, context);

        try {
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

            String errorMessage = evaluateAllRulesWithDetails(collectionWithRules.rules(), context);
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
            RuleHandler handler = ruleHandlerMap.get(rule.getType());
            if (handler == null) {
                log.error("No handler found for rule type: {}", rule.getType());
                return false;
            }

            String config = rule.getRuleConfiguration();
            if (config == null || config.trim().isEmpty()) {
                log.error("Rule configuration is null or empty for rule: {}", rule.getId());
                return false;
            }

            boolean result = handler.check(config, context);

            log.debug("Rule {} evaluation: type={}, result={}", rule.getId(), rule.getType(), result);
            return result;

        } catch (Exception e) {
            log.error("Error evaluating rule {}: {}", rule.getId(), rule.getType(), e);
            return false;
        }
    }

    @Observed(name = "evaluate-multiple-collections")
    public List<RuleCollectionEvaluationResult> evaluateMultipleCollections(List<Integer> collectionIds, RuleEvaluationContext context) {
        log.info("Batch evaluating {} collections with context: {}", collectionIds.size(), context);

        if (context == null || context.getOrderAmount() == null) {
            return collectionIds.stream()
                    .map(id -> new RuleCollectionEvaluationResult(id, false, "Invalid evaluation context or order amount"))
                    .toList();
        }

        List<RuleCollectionEvaluationResult> results = new ArrayList<>();

        for (Integer collectionId : collectionIds) {
            try {
                RuleCollectionEvaluationResult result = evaluateRuleCollection(collectionId, context);
                results.add(result);
                log.debug("Collection {} evaluation result: success={}, error={}",
                        collectionId, result.success(), result.errorMessage());
            } catch (Exception e) {
                log.error("Error evaluating collection {}: {}", collectionId, e);
                results.add(new RuleCollectionEvaluationResult(collectionId, false, "Internal error: " + e.getMessage()));
            }
        }

        long successCount = results.stream().mapToLong(r -> r.success() ? 1 : 0).sum();
        log.info("Batch evaluation completed: {} collections processed, {} successful", results.size(), successCount);
        return results;
    }

    private RuleCollectionWithRules loadRuleCollectionWithRules(Integer collectionId) {
        try {
            Optional<RuleCacheService.RuleCollectionWithRulesCacheInfo> cachedInfo =
                    ruleCacheService.getCachedRuleCollectionWithRules(collectionId);

            if (cachedInfo.isPresent()) {
                log.debug("Using cached rule collection with rules: {}", collectionId);
                RuleCacheService.RuleCollectionWithRulesCacheInfo cacheInfo = cachedInfo.get();

                RuleCollection collection = new RuleCollection();
                collection.setId(cacheInfo.getCollectionId());
                collection.setName(cacheInfo.getName());
                collection.setRuleIds(cacheInfo.getRuleIds());

                List<Rule> rules = cacheInfo.getRules().parallelStream()
                        .map(this::convertCacheInfoToRule)
                        .toList();

                return new RuleCollectionWithRules(collection, rules);
            }

            Optional<RuleCollection> collectionOpt = ruleCollectionRepository.findById(collectionId);
            if (collectionOpt.isEmpty()) {
                log.warn("Rule collection not found: {}", collectionId);
                return null;
            }

            RuleCollection collection = collectionOpt.get();
            List<Rule> rules = new ArrayList<>();
            List<Integer> ruleIdsList = collection.getRuleIdsList();

            if (!ruleIdsList.isEmpty()) {
                rules = ruleRepository.findByIdIn(ruleIdsList);
                log.debug("Loaded {} rules for collection {} using rule IDs: {}",
                        rules.size(), collectionId, ruleIdsList);
            } else {
                log.warn("No rule IDs found in collection: {}", collectionId);
            }

            RuleCacheService.RuleCollectionWithRulesCacheInfo cacheInfo =
                    RuleCacheService.RuleCollectionWithRulesCacheInfo.fromRuleCollectionWithRules(collection, rules);
            ruleCacheService.cacheRuleCollectionWithRules(collectionId, cacheInfo);

            log.debug("Loaded and cached rule collection: {} with {} rules", collectionId, rules.size());
            return new RuleCollectionWithRules(collection, rules);

        } catch (Exception e) {
            log.error("Error loading rule collection: {}", collectionId, e);
            return null;
        }
    }

    private Rule convertCacheInfoToRule(RuleCacheService.RuleConfigCacheInfo cacheInfo) {
        Rule rule = new Rule();
        rule.setId(cacheInfo.getRuleId());
        rule.setType(cacheInfo.getRuleType());
        rule.setDescription(cacheInfo.getDescription());
        rule.setRuleConfiguration(cacheInfo.getRuleConfiguration());
        return rule;
    }

    private boolean evaluateAllRules(List<Rule> rules, RuleEvaluationContext context) {
        if (rules == null || rules.isEmpty()) {
            log.warn("No rules to evaluate");
            return false;
        }

        for (Rule rule : rules) {
            boolean ruleResult = evaluateRule(rule, context);
            if (!ruleResult) {
                log.debug("Rule collection failed at rule {}: {}", rule.getId(), rule.getDescription());
                return false;
            }
        }

        log.debug("All {} rules passed evaluation", rules.size());
        return true;
    }

    @Observed(name = "evaluate-all-rules-details", contextualName = "all-rules-detailed-evaluation")
    private String evaluateAllRulesWithDetails(List<Rule> rules, RuleEvaluationContext context) {
        if (rules == null || rules.isEmpty()) {
            return "No rules to evaluate";
        }

        for (Rule rule : rules) {
            try {
                boolean ruleResult = evaluateRule(rule, context);
                if (!ruleResult) {
                    String errorMsg = String.format("Rule failed: %s (ID: %d, Type: %s)",
                            rule.getDescription() != null ? rule.getDescription() : "No description",
                            rule.getId(),
                            rule.getType());
                    log.debug("Rule evaluation failed: {}", errorMsg);
                    return errorMsg;
                }
            } catch (Exception e) {
                String errorMsg = String.format("Error evaluating rule %d (%s): %s",
                        rule.getId(), rule.getType(), e.getMessage());
                log.error("Rule evaluation error: {}", errorMsg, e);
                return errorMsg;
            }
        }

        log.debug("All {} rules passed detailed evaluation", rules.size());
        return null;
    }
}


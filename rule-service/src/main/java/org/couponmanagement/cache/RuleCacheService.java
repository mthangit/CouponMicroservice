package org.couponmanagement.cache;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.entity.Rule;
import org.couponmanagement.entity.RuleCollection;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleCacheService {

    private final RedisCacheService cacheService;
    private final RuleCacheProperties cacheProperties;
    
    public void cacheRuleCollection(Integer collectionId, RuleCollectionCacheInfo ruleCollectionInfo) {
        String key = cacheProperties.getRuleCollectionKey(collectionId);
        cacheService.put(key, ruleCollectionInfo, cacheProperties.getRuleCollectionTtlSeconds());
        log.debug("Cached rule collection: collectionId={}, ttl={}s", collectionId, cacheProperties.getRuleCollectionTtlSeconds());
    }

    public Optional<RuleCollectionCacheInfo> getCachedRuleCollection(Integer collectionId) {
        String key = cacheProperties.getRuleCollectionKey(collectionId);
        Optional<RuleCollectionCacheInfo> result = cacheService.get(key, RuleCollectionCacheInfo.class);

        if (result.isPresent()) {
            log.debug("Cache hit for rule collection: {}", collectionId);
        } else {
            log.debug("Cache miss for rule collection: {}", collectionId);
        }

        return result;
    }
    
    public void cacheRuleConfig(Integer ruleId, RuleConfigCacheInfo ruleConfigInfo) {
        String key = cacheProperties.getRuleConfigKey(ruleId);
        cacheService.put(key, ruleConfigInfo, cacheProperties.getRuleConfigTtlSeconds());
        log.debug("Cached rule config: ruleId={}, ttl={}s", ruleId, cacheProperties.getRuleConfigTtlSeconds());
    }

    public Optional<RuleConfigCacheInfo> getCachedRuleConfig(Integer ruleId) {
        String key = cacheProperties.getRuleConfigKey(ruleId);
        Optional<RuleConfigCacheInfo> result = cacheService.get(key, RuleConfigCacheInfo.class);

        if (result.isPresent()) {
            log.debug("Cache hit for rule config: {}", ruleId);
        } else {
            log.debug("Cache miss for rule config: {}", ruleId);
        }

        return result;
    }
    
    public void cacheRuleCollectionWithRules(Integer collectionId, RuleCollectionWithRulesCacheInfo collectionWithRules) {
        String key = cacheProperties.getRuleCollectionWithRulesKey(collectionId);
        cacheService.put(key, collectionWithRules, cacheProperties.getRuleCollectionTtlSeconds());
        log.debug("Cached rule collection with rules: collectionId={}, rulesCount={}, ttl={}s", 
                collectionId, collectionWithRules.getRules().size(), cacheProperties.getRuleCollectionTtlSeconds());
    }

    @Observed(name = "get-cached-rule-collection-with-rules")
    public Optional<RuleCollectionWithRulesCacheInfo> getCachedRuleCollectionWithRules(Integer collectionId) {
        String key = cacheProperties.getRuleCollectionWithRulesKey(collectionId);
        Optional<RuleCollectionWithRulesCacheInfo> result = cacheService.get(key, RuleCollectionWithRulesCacheInfo.class);

        if (result.isPresent()) {
            log.debug("Cache hit for rule collection with rules: {}", collectionId);
        } else {
            log.debug("Cache miss for rule collection with rules: {}", collectionId);
        }

        return result;
    }

    public void invalidateRuleCollectionCache(Integer collectionId) {
        log.info("Cache invalidation not supported in simplified cache service for rule collection: {}", collectionId);
    }
    
    public void invalidateRuleConfigCache(Integer ruleId) {
        log.info("Cache invalidation not supported in simplified cache service for rule config: {}", ruleId);
    }

    // ============ DTOs ============
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RuleCollectionCacheInfo {
        private Integer collectionId;
        private String name;
        private String ruleIds;
        private LocalDateTime cachedAt;

        public static RuleCollectionCacheInfo fromRuleCollection(RuleCollection ruleCollection) {
            return RuleCollectionCacheInfo.builder()
                    .collectionId(ruleCollection.getId())
                    .name(ruleCollection.getName())
                    .ruleIds(ruleCollection.getRuleIds())
                    .cachedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RuleConfigCacheInfo {
        private Integer ruleId;
        private String ruleType;
        private String description;
        private String ruleConfiguration;
        private LocalDateTime cachedAt;

        public static RuleConfigCacheInfo fromRule(Rule rule) {
            return RuleConfigCacheInfo.builder()
                    .ruleId(rule.getId())
                    .ruleType(rule.getType())
                    .description(rule.getDescription())
                    .ruleConfiguration(rule.getRuleConfiguration())
                    .cachedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RuleCollectionWithRulesCacheInfo {
        private Integer collectionId;
        private String name;
        private String ruleIds;
        private List<RuleConfigCacheInfo> rules;
        private LocalDateTime cachedAt;

        public static RuleCollectionWithRulesCacheInfo fromRuleCollectionWithRules(RuleCollection ruleCollection, List<Rule> rules) {
            List<RuleConfigCacheInfo> ruleInfos = rules.stream()
                    .map(RuleConfigCacheInfo::fromRule)
                    .toList();

            return RuleCollectionWithRulesCacheInfo.builder()
                    .collectionId(ruleCollection.getId())
                    .name(ruleCollection.getName())
                    .ruleIds(ruleCollection.getRuleIds())
                    .rules(ruleInfos)
                    .cachedAt(LocalDateTime.now())
                    .build();
        }
    }
}

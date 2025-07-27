package org.couponmanagement.cache;

import org.couponmanagement.entity.Rule;
import org.couponmanagement.entity.RuleCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleCacheServiceTest {

    @Mock
    private RedisCacheService cacheService;

    @Mock
    private RuleCacheProperties cacheProperties;

    @InjectMocks
    private RuleCacheService ruleCacheService;

    private Integer collectionId;
    private Integer ruleId;
    private RuleCollection testRuleCollection;
    private Rule testRule;

    @BeforeEach
    void setUp() {
        collectionId = 1;
        ruleId = 1;

        testRuleCollection = new RuleCollection();
        testRuleCollection.setId(collectionId);
        testRuleCollection.setName("Test Collection");
        testRuleCollection.setRuleIds("[1, 2, 3]");
        testRuleCollection.setCreatedAt(LocalDateTime.now());
        testRuleCollection.setUpdatedAt(LocalDateTime.now());

        testRule = new Rule();
        testRule.setId(ruleId);
        testRule.setType("MIN_ORDER_AMOUNT");
        testRule.setDescription("Minimum order amount rule");
        testRule.setRuleConfiguration("{\"min_amount\": 50.0}");
        testRule.setIsActive(true);
        testRule.setCreatedAt(LocalDateTime.now());
        testRule.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void cacheRuleCollection_Success() {
        // Arrange
        String expectedKey = "rule-collection:1";
        long expectedTtl = 300L;
        RuleCacheService.RuleCollectionCacheInfo cacheInfo =
                RuleCacheService.RuleCollectionCacheInfo.fromRuleCollection(testRuleCollection);

        when(cacheProperties.getRuleCollectionKey(collectionId)).thenReturn(expectedKey);
        when(cacheProperties.getRuleCollectionTtlSeconds()).thenReturn(expectedTtl);

        // Act
        ruleCacheService.cacheRuleCollection(collectionId, cacheInfo);

        // Assert
        verify(cacheProperties).getRuleCollectionKey(collectionId);
        verify(cacheProperties).getRuleCollectionTtlSeconds();
        verify(cacheService).put(expectedKey, cacheInfo, expectedTtl);
    }

    @Test
    void getCachedRuleCollection_CacheHit() {
        // Arrange
        String expectedKey = "rule-collection:1";
        RuleCacheService.RuleCollectionCacheInfo cacheInfo =
                RuleCacheService.RuleCollectionCacheInfo.fromRuleCollection(testRuleCollection);

        when(cacheProperties.getRuleCollectionKey(collectionId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, RuleCacheService.RuleCollectionCacheInfo.class))
                .thenReturn(Optional.of(cacheInfo));

        // Act
        Optional<RuleCacheService.RuleCollectionCacheInfo> result =
                ruleCacheService.getCachedRuleCollection(collectionId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(cacheInfo, result.get());
        verify(cacheProperties).getRuleCollectionKey(collectionId);
        verify(cacheService).get(expectedKey, RuleCacheService.RuleCollectionCacheInfo.class);
    }

    @Test
    void getCachedRuleCollection_CacheMiss() {
        // Arrange
        String expectedKey = "rule-collection:1";

        when(cacheProperties.getRuleCollectionKey(collectionId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, RuleCacheService.RuleCollectionCacheInfo.class))
                .thenReturn(Optional.empty());

        // Act
        Optional<RuleCacheService.RuleCollectionCacheInfo> result =
                ruleCacheService.getCachedRuleCollection(collectionId);

        // Assert
        assertFalse(result.isPresent());
        verify(cacheProperties).getRuleCollectionKey(collectionId);
        verify(cacheService).get(expectedKey, RuleCacheService.RuleCollectionCacheInfo.class);
    }

    @Test
    void cacheRuleConfig_Success() {
        // Arrange
        String expectedKey = "rule-config:1";
        long expectedTtl = 600L;
        RuleCacheService.RuleConfigCacheInfo cacheInfo =
                RuleCacheService.RuleConfigCacheInfo.fromRule(testRule);

        when(cacheProperties.getRuleConfigKey(ruleId)).thenReturn(expectedKey);
        when(cacheProperties.getRuleConfigTtlSeconds()).thenReturn(expectedTtl);

        // Act
        ruleCacheService.cacheRuleConfig(ruleId, cacheInfo);

        // Assert
        verify(cacheProperties).getRuleConfigKey(ruleId);
        verify(cacheProperties).getRuleConfigTtlSeconds();
        verify(cacheService).put(expectedKey, cacheInfo, expectedTtl);
    }

    @Test
    void getCachedRuleConfig_CacheHit() {
        // Arrange
        String expectedKey = "rule-config:1";
        RuleCacheService.RuleConfigCacheInfo cacheInfo =
                RuleCacheService.RuleConfigCacheInfo.fromRule(testRule);

        when(cacheProperties.getRuleConfigKey(ruleId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, RuleCacheService.RuleConfigCacheInfo.class))
                .thenReturn(Optional.of(cacheInfo));

        // Act
        Optional<RuleCacheService.RuleConfigCacheInfo> result =
                ruleCacheService.getCachedRuleConfig(ruleId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(cacheInfo, result.get());
        verify(cacheProperties).getRuleConfigKey(ruleId);
        verify(cacheService).get(expectedKey, RuleCacheService.RuleConfigCacheInfo.class);
    }

    @Test
    void getCachedRuleConfig_CacheMiss() {
        // Arrange
        String expectedKey = "rule-config:1";

        when(cacheProperties.getRuleConfigKey(ruleId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, RuleCacheService.RuleConfigCacheInfo.class))
                .thenReturn(Optional.empty());

        // Act
        Optional<RuleCacheService.RuleConfigCacheInfo> result =
                ruleCacheService.getCachedRuleConfig(ruleId);

        // Assert
        assertFalse(result.isPresent());
        verify(cacheProperties).getRuleConfigKey(ruleId);
        verify(cacheService).get(expectedKey, RuleCacheService.RuleConfigCacheInfo.class);
    }

    @Test
    void cacheRuleCollectionWithRules_Success() {
        // Arrange
        String expectedKey = "rule-collection-with-rules:1";
        long expectedTtl = 300L;

        List<RuleCacheService.RuleConfigCacheInfo> rules = List.of(
                RuleCacheService.RuleConfigCacheInfo.fromRule(testRule)
        );

        RuleCacheService.RuleCollectionWithRulesCacheInfo cacheInfo =
                RuleCacheService.RuleCollectionWithRulesCacheInfo.builder()
                        .collectionId(collectionId)
                        .name("Test Collection")
                        .ruleIds(List.of(1, 2, 3).toString())
                        .rules(rules)
                        .cachedAt(LocalDateTime.now())
                        .build();

        when(cacheProperties.getRuleCollectionWithRulesKey(collectionId)).thenReturn(expectedKey);
        when(cacheProperties.getRuleCollectionTtlSeconds()).thenReturn(expectedTtl);

        // Act
        ruleCacheService.cacheRuleCollectionWithRules(collectionId, cacheInfo);

        // Assert
        verify(cacheProperties).getRuleCollectionWithRulesKey(collectionId);
        verify(cacheProperties).getRuleCollectionTtlSeconds();
        verify(cacheService).put(expectedKey, cacheInfo, expectedTtl);
    }

    @Test
    void getCachedRuleCollectionWithRules_CacheHit() {
        // Arrange
        String expectedKey = "rule-collection-with-rules:1";

        List<RuleCacheService.RuleConfigCacheInfo> rules = List.of(
                RuleCacheService.RuleConfigCacheInfo.fromRule(testRule)
        );

        RuleCacheService.RuleCollectionWithRulesCacheInfo cacheInfo =
                RuleCacheService.RuleCollectionWithRulesCacheInfo.builder()
                        .collectionId(collectionId)
                        .name("Test Collection")
                        .ruleIds(List.of(1, 2, 3).toString())
                        .rules(rules)
                        .cachedAt(LocalDateTime.now())
                        .build();

        when(cacheProperties.getRuleCollectionWithRulesKey(collectionId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, RuleCacheService.RuleCollectionWithRulesCacheInfo.class))
                .thenReturn(Optional.of(cacheInfo));

        // Act
        Optional<RuleCacheService.RuleCollectionWithRulesCacheInfo> result =
                ruleCacheService.getCachedRuleCollectionWithRules(collectionId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(cacheInfo, result.get());
        assertEquals(1, result.get().getRules().size());
        verify(cacheProperties).getRuleCollectionWithRulesKey(collectionId);
        verify(cacheService).get(expectedKey, RuleCacheService.RuleCollectionWithRulesCacheInfo.class);
    }

    @Test
    void getCachedRuleCollectionWithRules_CacheMiss() {
        // Arrange
        String expectedKey = "rule-collection-with-rules:1";

        when(cacheProperties.getRuleCollectionWithRulesKey(collectionId)).thenReturn(expectedKey);
        when(cacheService.get(expectedKey, RuleCacheService.RuleCollectionWithRulesCacheInfo.class))
                .thenReturn(Optional.empty());

        // Act
        Optional<RuleCacheService.RuleCollectionWithRulesCacheInfo> result =
                ruleCacheService.getCachedRuleCollectionWithRules(collectionId);

        // Assert
        assertFalse(result.isPresent());
        verify(cacheProperties).getRuleCollectionWithRulesKey(collectionId);
        verify(cacheService).get(expectedKey, RuleCacheService.RuleCollectionWithRulesCacheInfo.class);
    }

    @Test
    void invalidateRuleCollectionCache_LogsMessage() {
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> ruleCacheService.invalidateRuleCollectionCache(collectionId));

        // This method only logs, so we can't verify much beyond that it doesn't fail
    }

    @Test
    void invalidateRuleConfigCache_LogsMessage() {
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> ruleCacheService.invalidateRuleConfigCache(ruleId));

        // This method only logs, so we can't verify much beyond that it doesn't fail
    }
}

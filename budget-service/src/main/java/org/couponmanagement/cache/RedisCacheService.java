package org.couponmanagement.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public RedisCacheService(@Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public <T> Optional<T> get(String key, Class<T> valueType) {
        try {
            String fullKey = buildKey(key);
            Object cached = redisTemplate.opsForValue().get(fullKey);

            if (cached == null) {
                missCount.incrementAndGet();
                log.debug("Cache miss for key: {}", fullKey);
                return Optional.empty();
            }

            hitCount.incrementAndGet();

            // With GenericJackson2JsonRedisSerializer, objects are automatically deserialized
            if (valueType.isInstance(cached)) {
                return Optional.of(valueType.cast(cached));
            }

            // Handle case where cached value is a different type but can be converted
            if (cached instanceof String && valueType != String.class) {
                String stringValue = (String) cached;
                
                // Try to convert string to target type
                if (valueType == BigDecimal.class) {
                    return Optional.of(valueType.cast(new BigDecimal(stringValue)));
                } else if (valueType == Integer.class) {
                    return Optional.of(valueType.cast(Integer.valueOf(stringValue)));
                } else if (valueType == Long.class) {
                    return Optional.of(valueType.cast(Long.valueOf(stringValue)));
                } else if (valueType == Double.class) {
                    return Optional.of(valueType.cast(Double.valueOf(stringValue)));
                } else if (valueType == Boolean.class) {
                    return Optional.of(valueType.cast(Boolean.valueOf(stringValue)));
                }
            }

            log.warn("Cannot convert cached value of type {} to {}", 
                cached.getClass().getSimpleName(), valueType.getSimpleName());
            return Optional.empty();

        } catch (Exception e) {
            log.error("Error getting value from cache for key: {}", key, e);
            missCount.incrementAndGet();
            return Optional.empty();
        }
    }

    public void put(String key, Object value, long ttlSeconds) {
        try {
            String fullKey = buildKey(key);

            if (value == null) {
                log.warn("Attempting to cache null value for key: {}", fullKey);
                return;
            }

            // With GenericJackson2JsonRedisSerializer, objects are automatically serialized
            // No need for manual serialization
            redisTemplate.opsForValue().set(fullKey, value, Duration.ofSeconds(ttlSeconds));

        } catch (Exception e) {
            log.error("Error putting value to cache for key: {}", key, e);
        }
    }

    public void increment(String key, BigDecimal delta) {
        try {
            String fullKey = buildKey(key);
            Double newValue = redisTemplate.opsForValue().increment(fullKey, delta.doubleValue());
            log.debug("Incremented cache key: {} by {}. New value: {}", fullKey, delta, newValue);
        } catch (Exception e) {
            log.error("Error incrementing cache key: {}", key, e);
        }
    }

    public Object getRaw(String key) {
        try {
            String fullKey = buildKey(key);
            return redisTemplate.opsForValue().get(fullKey);
        } catch (Exception e) {
            log.error("Error getting raw value from cache for key: {}", key, e);
            return null;
        }
    }

    private String buildKey(String key) {
        return key;
    }
}

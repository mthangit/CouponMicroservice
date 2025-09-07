package org.couponmanagement.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final OrderCacheProperties cacheProperties;

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public <T> Optional<T> get(String key, Class<T> valueType) {
        try {
            String fullKey = buildKey(key);
            Object cached = redisTemplate.opsForValue().get(fullKey);

            if (cached == null) {
                missCount.incrementAndGet();
                return Optional.empty();
            }

            hitCount.incrementAndGet();

            if (valueType.isInstance(cached)) {
                return Optional.of(valueType.cast(cached));
            }

            if (cached instanceof String) {
                T value = objectMapper.readValue((String) cached, valueType);
                return Optional.of(value);
            }

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

            Object cacheValue = value;
            if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
                cacheValue = objectMapper.writeValueAsString(value);
            }

            redisTemplate.opsForValue().set(fullKey, cacheValue, Duration.ofSeconds(ttlSeconds));
            log.debug("Cached value for key: {} with TTL: {}s", fullKey, ttlSeconds);

        } catch (JsonProcessingException e) {
            log.error("Error serializing value for cache key: {}", key, e);
        } catch (Exception e) {
            log.error("Error putting value to cache for key: {}", key, e);
        }
    }

    private String buildKey(String key) {
        return cacheProperties.getKeyPrefix() + ":" + key;
    }
}

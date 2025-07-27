package org.couponmanagement.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OrderCacheProperties cacheProperties;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RedisCacheService redisCacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(cacheProperties.getKeyPrefix()).thenReturn("order-service");
    }

    @Test
    void get_CacheHit_String() {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";
        String cachedValue = "test-value";

        when(valueOperations.get(fullKey)).thenReturn(cachedValue);

        // Act
        Optional<String> result = redisCacheService.get(key, String.class);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("test-value", result.get());
        verify(valueOperations).get(fullKey);
    }

    @Test
    void get_CacheHit_JsonObject() throws JsonProcessingException {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";
        String jsonString = "{\"name\":\"test\",\"value\":123}";
        TestObject expectedObject = new TestObject("test", 123);

        when(valueOperations.get(fullKey)).thenReturn(jsonString);
        when(objectMapper.readValue(jsonString, TestObject.class)).thenReturn(expectedObject);

        // Act
        Optional<TestObject> result = redisCacheService.get(key, TestObject.class);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("test", result.get().getName());
        assertEquals(123, result.get().getValue());
        verify(valueOperations).get(fullKey);
        verify(objectMapper).readValue(jsonString, TestObject.class);
    }

    @Test
    void get_CacheMiss() {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";

        when(valueOperations.get(fullKey)).thenReturn(null);

        // Act
        Optional<String> result = redisCacheService.get(key, String.class);

        // Assert
        assertFalse(result.isPresent());
        verify(valueOperations).get(fullKey);
    }

    @Test
    void get_JsonProcessingException() throws JsonProcessingException {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";
        String invalidJson = "invalid-json";

        when(valueOperations.get(fullKey)).thenReturn(invalidJson);
        when(objectMapper.readValue(invalidJson, TestObject.class))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        // Act
        Optional<TestObject> result = redisCacheService.get(key, TestObject.class);

        // Assert
        assertFalse(result.isPresent());
        verify(valueOperations).get(fullKey);
        verify(objectMapper).readValue(invalidJson, TestObject.class);
    }

    @Test
    void get_RuntimeException() {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";

        when(valueOperations.get(fullKey)).thenThrow(new RuntimeException("Redis connection failed"));

        // Act
        Optional<String> result = redisCacheService.get(key, String.class);

        // Assert
        assertFalse(result.isPresent());
        verify(valueOperations).get(fullKey);
    }

    @Test
    void put_StringValue() {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";
        String value = "test-value";
        long ttl = 300;

        // Act
        redisCacheService.put(key, value, ttl);

        // Assert
        verify(valueOperations).set(fullKey, value, Duration.ofSeconds(ttl));
    }

    @Test
    void put_NumberValue() {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";
        Integer value = 123;
        long ttl = 300;

        // Act
        redisCacheService.put(key, value, ttl);

        // Assert
        verify(valueOperations).set(fullKey, value, Duration.ofSeconds(ttl));
    }

    @Test
    void put_BooleanValue() {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";
        Boolean value = true;
        long ttl = 300;

        // Act
        redisCacheService.put(key, value, ttl);

        // Assert
        verify(valueOperations).set(fullKey, value, Duration.ofSeconds(ttl));
    }

    @Test
    void put_ComplexObject() throws JsonProcessingException {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";
        TestObject value = new TestObject("test", 123);
        String jsonString = "{\"name\":\"test\",\"value\":123}";
        long ttl = 300;

        when(objectMapper.writeValueAsString(value)).thenReturn(jsonString);

        // Act
        redisCacheService.put(key, value, ttl);

        // Assert
        verify(objectMapper).writeValueAsString(value);
        verify(valueOperations).set(fullKey, jsonString, Duration.ofSeconds(ttl));
    }

    @Test
    void put_RuntimeException() {
        // Arrange
        String key = "test-key";
        String fullKey = "order-service:test-key";
        String value = "test-value";
        long ttl = 300;

        doThrow(new RuntimeException("Redis connection failed"))
                .when(valueOperations).set(fullKey, value, Duration.ofSeconds(ttl));

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> redisCacheService.put(key, value, ttl));

        verify(valueOperations).set(fullKey, value, Duration.ofSeconds(ttl));
    }

    @Test
    void buildKey_WithPrefix() {
        // This test verifies the key building logic indirectly through other methods
        String key = "test-key";
        String fullKey = "order-service:test-key";

        when(valueOperations.get(fullKey)).thenReturn("test-value");

        Optional<String> result = redisCacheService.get(key, String.class);

        assertTrue(result.isPresent());
        verify(valueOperations).get(fullKey);
    }

    // Test helper class
    private static class TestObject {
        private String name;
        private int value;

        public TestObject() {}

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
}

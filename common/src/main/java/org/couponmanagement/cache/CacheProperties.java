package org.couponmanagement.cache;

public interface CacheProperties {
    String getKeyPrefix();
    long getDefaultTtlSeconds();
}

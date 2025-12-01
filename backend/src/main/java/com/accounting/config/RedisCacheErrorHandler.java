package com.accounting.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Custom cache error handler that handles deserialization errors gracefully.
 * When a deserialization error occurs (e.g., old cache format), it evicts
 * the problematic cache entry and allows the method to proceed normally.
 */
public class RedisCacheErrorHandler implements CacheErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        // If it's a deserialization error, evict the bad cache entry
        if (exception instanceof SerializationException) {
            logger.warn(
                    "Deserialization error for cache '{}' key '{}'. Evicting bad cache entry. Error: {}",
                    cache.getName(),
                    key,
                    exception.getMessage());
            try {
                cache.evict(key);
            } catch (Exception e) {
                logger.error("Failed to evict bad cache entry for key '{}'", key, e);
            }
        } else {
            logger.error("Cache get error for cache '{}' key '{}'", cache.getName(), key, exception);
        }
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        logger.error("Cache put error for cache '{}' key '{}'", cache.getName(), key, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        logger.error("Cache evict error for cache '{}' key '{}'", cache.getName(), key, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        logger.error("Cache clear error for cache '{}'", cache.getName(), exception);
    }
}


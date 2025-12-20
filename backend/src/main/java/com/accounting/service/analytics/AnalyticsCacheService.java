package com.accounting.service.analytics;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.accounting.security.CompanyContext;

@Service
public class AnalyticsCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final AnalyticsCacheKeyGenerator cacheKeyGenerator;
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public AnalyticsCacheService(StringRedisTemplate redisTemplate, AnalyticsCacheKeyGenerator cacheKeyGenerator) {
        this.redisTemplate = redisTemplate;
        this.cacheKeyGenerator = cacheKeyGenerator;
    }

    public String generateWidgetCacheKey(String widgetType, LocalDate startDate, LocalDate endDate) {
        Long companyId = CompanyContext.getCompanyId();
        return cacheKeyGenerator.widgetCache(companyId, widgetType) + ":" +
                (startDate != null ? startDate.toString() : "null") + ":" +
                (endDate != null ? endDate.toString() : "null");
    }

    public void recordCacheHit(String widgetType) {
        cacheHits.incrementAndGet();
        incrementRedisCounter(cacheKeyGenerator.cacheMetrics("hits", widgetType));
        logger.debug("Cache HIT for widget: {}", widgetType);
    }

    public void recordCacheMiss(String widgetType) {
        cacheMisses.incrementAndGet();
        incrementRedisCounter(cacheKeyGenerator.cacheMetrics("misses", widgetType));
        logger.debug("Cache MISS for widget: {}", widgetType);
    }

    private void incrementRedisCounter(String key) {
        try {
            redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            logger.warn("Failed to increment Redis counter {}: {}", key, e.getMessage());
        }
    }

    public CacheMetrics getCacheMetrics() {
        return new CacheMetrics(cacheHits.get(), cacheMisses.get());
    }

    public CacheMetrics getCacheMetricsForWidget(String widgetType) {
        long hits = getRedisCounter(cacheKeyGenerator.cacheMetrics("hits", widgetType));
        long misses = getRedisCounter(cacheKeyGenerator.cacheMetrics("misses", widgetType));
        return new CacheMetrics(hits, misses);
    }

    private long getRedisCounter(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            logger.warn("Failed to get Redis counter {}: {}", key, e.getMessage());
            return 0;
        }
    }

    @CacheEvict(value = "analytics-widget", allEntries = true)
    public void invalidateAllWidgetCaches() {
        logger.info("Invalidating all analytics widget caches");
        resetLocalMetrics();
    }

    @CacheEvict(value = "analytics-widget", key = "'widget:' + #companyId + ':*'")
    public void invalidateCompanyWidgetCaches(Long companyId) {
        logger.info("Invalidating analytics widget caches for company: {}", companyId);
        try {
            String pattern = "analytics-widget::" + cacheKeyGenerator.companyWidgetPattern(companyId);
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Deleted {} cache entries for company {}", keys.size(), companyId);
            }
        } catch (Exception e) {
            logger.warn("Failed to invalidate company widget caches: {}", e.getMessage());
        }
    }

    private void resetLocalMetrics() {
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    public record CacheMetrics(long hits, long misses) {
        public double hitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}

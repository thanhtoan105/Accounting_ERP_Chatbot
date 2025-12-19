package com.accounting.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.accounting.security.CompanyContext;
import com.accounting.service.analytics.AnalyticsCacheService.CacheMetrics;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AnalyticsCacheServiceTest {

    private static final Long COMPANY_ID = 42L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AnalyticsCacheKeyGenerator cacheKeyGenerator;

    private AnalyticsCacheService service;

    @BeforeEach
    void setUp() {
        when(cacheKeyGenerator.widgetCache(any(), anyString())).thenAnswer(inv -> 
            "widget:" + inv.getArgument(0) + ":" + inv.getArgument(1));
        when(cacheKeyGenerator.cacheMetrics(anyString(), anyString())).thenAnswer(inv ->
            "analytics:cache:metrics:" + inv.getArgument(0) + ":" + inv.getArgument(1));
        when(cacheKeyGenerator.companyWidgetPattern(any())).thenAnswer(inv ->
            "widget:" + inv.getArgument(0) + ":*");
        
        service = new AnalyticsCacheService(redisTemplate, cacheKeyGenerator);
        CompanyContext.setCompanyId(COMPANY_ID);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Nested
    @DisplayName("generateWidgetCacheKey tests")
    class GenerateWidgetCacheKeyTests {

        @Test
        void shouldGenerateKeyWithCompanyIdWidgetTypeAndDates() {
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 1, 31);

            String key = service.generateWidgetCacheKey("revenue-vs-expenses", startDate, endDate);

            assertThat(key).isEqualTo("widget:42:revenue-vs-expenses:2024-01-01:2024-01-31");
        }

        @Test
        void shouldHandleNullDates() {
            String key = service.generateWidgetCacheKey("cash-position", null, null);

            assertThat(key).isEqualTo("widget:42:cash-position:null:null");
        }

        @Test
        void shouldHandlePartialNullDates() {
            LocalDate startDate = LocalDate.of(2024, 6, 15);

            String key = service.generateWidgetCacheKey("period-summary", startDate, null);

            assertThat(key).isEqualTo("widget:42:period-summary:2024-06-15:null");
        }

        @Test
        void shouldIncludeCompanyIdFromContext() {
            CompanyContext.setCompanyId(999L);
            LocalDate startDate = LocalDate.of(2024, 3, 1);
            LocalDate endDate = LocalDate.of(2024, 3, 31);

            String key = service.generateWidgetCacheKey("ar-ap-balances", startDate, endDate);

            assertThat(key).startsWith("widget:999:");
        }
    }

    @Nested
    @DisplayName("recordCacheHit tests")
    class RecordCacheHitTests {

        @Test
        void shouldIncrementRedisCounter() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            service.recordCacheHit("revenue-vs-expenses");

            verify(valueOperations).increment("analytics:cache:metrics:hits:revenue-vs-expenses");
        }

        @Test
        void shouldIncrementLocalCounter() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            service.recordCacheHit("widget1");
            service.recordCacheHit("widget2");

            CacheMetrics metrics = service.getCacheMetrics();
            assertThat(metrics.hits()).isEqualTo(2);
        }

        @Test
        void shouldHandleRedisFailureGracefully() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(new RuntimeException("Redis connection failed"))
                .when(valueOperations).increment(anyString());

            service.recordCacheHit("widget");

            CacheMetrics metrics = service.getCacheMetrics();
            assertThat(metrics.hits()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("recordCacheMiss tests")
    class RecordCacheMissTests {

        @Test
        void shouldIncrementRedisCounter() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            service.recordCacheMiss("cash-position");

            verify(valueOperations).increment("analytics:cache:metrics:misses:cash-position");
        }

        @Test
        void shouldIncrementLocalCounter() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            service.recordCacheMiss("widget1");
            service.recordCacheMiss("widget2");
            service.recordCacheMiss("widget3");

            CacheMetrics metrics = service.getCacheMetrics();
            assertThat(metrics.misses()).isEqualTo(3);
        }

        @Test
        void shouldHandleRedisFailureGracefully() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(new RuntimeException("Redis unavailable"))
                .when(valueOperations).increment(anyString());

            service.recordCacheMiss("widget");

            CacheMetrics metrics = service.getCacheMetrics();
            assertThat(metrics.misses()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getCacheMetrics tests")
    class GetCacheMetricsTests {

        @Test
        void shouldReturnZeroMetricsInitially() {
            CacheMetrics metrics = service.getCacheMetrics();

            assertThat(metrics.hits()).isZero();
            assertThat(metrics.misses()).isZero();
        }

        @Test
        void shouldAccumulateHitsAndMisses() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            service.recordCacheHit("w1");
            service.recordCacheHit("w2");
            service.recordCacheMiss("w3");

            CacheMetrics metrics = service.getCacheMetrics();
            assertThat(metrics.hits()).isEqualTo(2);
            assertThat(metrics.misses()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getCacheMetricsForWidget tests")
    class GetCacheMetricsForWidgetTests {

        @Test
        void shouldReturnZeroWhenNoMetricsExist() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("analytics:cache:metrics:hits:new-widget")).thenReturn(null);
            when(valueOperations.get("analytics:cache:metrics:misses:new-widget")).thenReturn(null);

            CacheMetrics metrics = service.getCacheMetricsForWidget("new-widget");

            assertThat(metrics.hits()).isZero();
            assertThat(metrics.misses()).isZero();
        }

        @Test
        void shouldReturnStoredMetrics() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("analytics:cache:metrics:hits:revenue")).thenReturn("150");
            when(valueOperations.get("analytics:cache:metrics:misses:revenue")).thenReturn("25");

            CacheMetrics metrics = service.getCacheMetricsForWidget("revenue");

            assertThat(metrics.hits()).isEqualTo(150);
            assertThat(metrics.misses()).isEqualTo(25);
        }

        @Test
        void shouldHandleRedisFailureGracefully() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

            CacheMetrics metrics = service.getCacheMetricsForWidget("widget");

            assertThat(metrics.hits()).isZero();
            assertThat(metrics.misses()).isZero();
        }
    }

    @Nested
    @DisplayName("CacheMetrics record tests")
    class CacheMetricsRecordTests {

        @Test
        void hitRateShouldBeZeroWhenNoActivity() {
            CacheMetrics metrics = new CacheMetrics(0, 0);

            assertThat(metrics.hitRate()).isEqualTo(0.0);
        }

        @Test
        void hitRateShouldCalculateCorrectly() {
            CacheMetrics metrics = new CacheMetrics(75, 25);

            assertThat(metrics.hitRate()).isEqualTo(0.75);
        }

        @Test
        void hitRateShouldBeOneWithAllHits() {
            CacheMetrics metrics = new CacheMetrics(100, 0);

            assertThat(metrics.hitRate()).isEqualTo(1.0);
        }

        @Test
        void hitRateShouldBeZeroWithAllMisses() {
            CacheMetrics metrics = new CacheMetrics(0, 50);

            assertThat(metrics.hitRate()).isEqualTo(0.0);
        }
    }
}

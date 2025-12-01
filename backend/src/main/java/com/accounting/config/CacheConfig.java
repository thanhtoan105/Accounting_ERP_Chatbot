package com.accounting.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Redis caching.
 * Enables Spring Cache abstraction with Redis backend.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

        /**
         * Create a Redis serializer configured to handle Spring Data Page objects.
         * This serializer uses an ObjectMapper configured to properly deserialize PageImpl
         * by enabling default typing and registering necessary modules.
         *
         * @return configured Redis serializer
         */
        @Bean
        public GenericJackson2JsonRedisSerializer redisSerializer() {
                ObjectMapper objectMapper = new ObjectMapper();
                // Register JavaTimeModule for LocalDate, LocalDateTime, etc.
                objectMapper.registerModule(new JavaTimeModule());
                
                // Register a custom module to handle PageImpl deserialization
                SimpleModule pageModule = new SimpleModule("PageModule");
                // Add custom deserializer for PageImpl if needed
                // For now, we rely on default typing
                
                objectMapper.registerModule(pageModule);
                
                // Enable default typing to handle polymorphic types like PageImpl
                // This is necessary because PageImpl doesn't have a default constructor
                objectMapper.activateDefaultTyping(
                                objectMapper.getPolymorphicTypeValidator(),
                                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL,
                                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
                
                // Configure deserialization features
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                // Allow empty beans (needed for some Spring Data types)
                objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false);
                
                return new GenericJackson2JsonRedisSerializer(objectMapper);
        }

        /**
         * Configure Redis cache manager with custom cache configurations.
         * AP aging reports use 5-minute TTL as per requirements.
         * AR aging reports use 1-hour TTL as per requirements.
         *
         * @param connectionFactory Redis connection factory
         * @param redisSerializer   Redis serializer configured for Spring Data types
         * @return configured cache manager
         */
        @Bean
        public CacheManager cacheManager(
                        RedisConnectionFactory connectionFactory,
                        GenericJackson2JsonRedisSerializer redisSerializer) {
                // Default cache configuration (1 hour TTL)
                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(1))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                redisSerializer))
                                .disableCachingNullValues();

                // AP aging cache configuration (5 minutes TTL as per requirements)
                RedisCacheConfiguration apAgingConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(5))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                redisSerializer))
                                .disableCachingNullValues();

                // AR aging cache configuration (1 hour TTL as per requirements)
                RedisCacheConfiguration arAgingConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(1))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                redisSerializer))
                                .disableCachingNullValues();

                // AR dashboard cache configuration (5 minutes TTL for dashboard metrics)
                RedisCacheConfiguration arDashboardConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(5))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                redisSerializer))
                                .disableCachingNullValues();

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withCacheConfiguration("ap-aging", apAgingConfig)
                                .withCacheConfiguration("ar-aging", arAgingConfig)
                                .withCacheConfiguration("ar-dashboard", arDashboardConfig)
                                .build();
        }

        /**
         * Register custom cache error handler to handle deserialization errors gracefully.
         * When a deserialization error occurs (e.g., old cache format), it evicts
         * the problematic cache entry and allows the method to proceed normally.
         *
         * @return cache error handler
         */
        @Override
        @Bean
        public CacheErrorHandler errorHandler() {
                return new RedisCacheErrorHandler();
        }
}

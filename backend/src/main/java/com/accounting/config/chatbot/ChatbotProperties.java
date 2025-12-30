package com.accounting.config.chatbot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for the AI Chatbot feature.
 * Binds to chatbot.* properties in application.yml.
 *
 * <p>This class provides type-safe access to chatbot configuration.
 * All RAG processing (embedding, vector search, LLM) is delegated to n8n workflows.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "chatbot")
public class ChatbotProperties {

    /**
     * Feature flag to enable/disable chatbot functionality.
     * When false, chatbot widget is hidden and API returns 503.
     */
    private boolean enabled = true;

    /**
     * n8n webhook configuration for RAG processing.
     */
    private N8n n8n = new N8n();

    /**
     * Query processing configuration.
     */
    private Query query = new Query();

    /**
     * Caching configuration.
     */
    private Cache cache = new Cache();

    @Data
    public static class N8n {
        /**
         * n8n webhook URL for voucher embedding automation.
         * Example: http://localhost:5678/webhook/voucher-embedding-v2
         */
        private String webhookUrl;

        /**
         * n8n webhook URL for batch voucher embedding.
         * Example: http://localhost:5678/webhook/batch-embed-vouchers
         */
        private String batchWebhookUrl;

        /**
         * n8n webhook URL for RAG query processing.
         * Example: http://localhost:5678/webhook/chatbot-query-v2
         */
        private String ragQueryUrl;

        /**
         * Shared secret for webhook authentication (X-Webhook-Secret header).
         */
        private String webhookSecret;

        /**
         * Number of retry attempts on failure. Default: 3
         */
        private int retryAttempts = 3;

        /**
         * Retry delay intervals in milliseconds (comma-separated).
         * Default: 1000,5000,15000 (1s, 5s, 15s)
         */
        private String retryDelays = "1000,5000,15000";

        /**
         * Webhook request timeout in seconds. Default: 30
         */
        private int timeoutSeconds = 30;

        /**
         * Parse retry delays from comma-separated string to long array.
         */
        public long[] getRetryDelaysArray() {
            String[] parts = retryDelays.split(",");
            long[] delays = new long[parts.length];
            for (int i = 0; i < parts.length; i++) {
                delays[i] = Long.parseLong(parts[i].trim());
            }
            return delays;
        }
    }

    @Data
    public static class Query {
        /**
         * Maximum query text length in characters. Default: 5000
         */
        private int maxLength = 5000;

        /**
         * Default language for responses. Default: vi (Vietnamese)
         */
        private String defaultLanguage = "vi";

        /**
         * Confidence threshold for low confidence. Default: 0.5
         * Below this: return "Không đủ dữ liệu" message
         */
        private double confidenceThresholdLow = 0.5;

        /**
         * Confidence threshold for high confidence. Default: 0.8
         * Above this: display "High" confidence badge
         */
        private double confidenceThresholdHigh = 0.8;
    }

    @Data
    public static class Cache {
        /**
         * Enable Redis caching for chatbot queries. Default: true
         */
        private boolean enabled = true;

        /**
         * Cache TTL in hours. Default: 1 hour
         */
        private int ttlHours = 1;
    }
}

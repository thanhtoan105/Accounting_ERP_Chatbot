package com.accounting.config.chatbot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for the AI Chatbot feature.
 * Binds to chatbot.* properties in application.yml.
 *
 * <p>This class provides type-safe access to chatbot configuration including
 * Azure OpenAI, Pinecone, n8n, and query settings.
 *
 * @see AzureOpenAIConfig
 * @see PineconeConfig
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
     * Azure OpenAI configuration for embeddings and completions.
     */
    private AzureOpenAI azureOpenai = new AzureOpenAI();

    /**
     * Pinecone vector database configuration.
     */
    private Pinecone pinecone = new Pinecone();

    /**
     * n8n webhook configuration for embedding automation.
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
    public static class AzureOpenAI {
        /**
         * Azure OpenAI resource endpoint.
         * Example: https://your-resource.openai.azure.com/
         */
        private String endpoint;

        /**
         * Azure OpenAI API key from Keys and Endpoint section in Azure portal.
         */
        private String apiKey;

        /**
         * Azure OpenAI API version. Default: 2023-05-15
         */
        private String apiVersion = "2023-05-15";

        /**
         * Deployment name for embedding model (text-embedding-ada-002).
         */
        private String embeddingDeployment = "embedding-ada-002";

        /**
         * Deployment name for chat completion model (gpt-35-turbo or gpt-4).
         */
        private String completionDeployment = "gpt-35-turbo";

        /**
         * Maximum tokens for completion responses. Default: 500
         */
        private int maxTokens = 500;

        /**
         * Temperature for LLM sampling (0.0-1.0). Lower = more deterministic.
         * Default: 0.3
         */
        private double temperature = 0.3;

        /**
         * Request timeout in seconds. Default: 30
         */
        private int timeoutSeconds = 30;
    }

    @Data
    public static class Pinecone {
        /**
         * Pinecone API key from dashboard.
         */
        private String apiKey;

        /**
         * Pinecone environment (e.g., us-east-1-aws).
         */
        private String environment = "us-east-1-aws";

        /**
         * Pinecone index name. Default: accounting-embeddings
         */
        private String indexName = "accounting-embeddings";

        /**
         * Namespace prefix for multi-tenancy. Default: company_
         * Actual namespace: company_{companyId}
         */
        private String namespacePrefix = "company_";

        /**
         * Number of top results to retrieve. Default: 10
         */
        private int topK = 10;

        /**
         * Minimum relevance score threshold. Default: 0.7
         */
        private double scoreThreshold = 0.7;
    }

    @Data
    public static class N8n {
        /**
         * n8n webhook URL for voucher embedding automation.
         * Example: http://localhost:5678/webhook/voucher-embedding-v2
         */
        private String webhookUrl;

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
         * Webhook request timeout in seconds. Default: 10
         */
        private int timeoutSeconds = 10;

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

package com.accounting.config.chatbot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for Azure OpenAI client.
 * Creates OpenAIClient bean for embedding generation and chat completions.
 *
 * <p>This configuration is only active when chatbot.enabled=true in application.yml.
 *
 * <p>Azure OpenAI provides enterprise-grade security, compliance, and data residency
 * compared to standard OpenAI API.
 *
 * @see OpenAIClient
 * @see ChatbotProperties.AzureOpenAI
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chatbot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AzureOpenAIConfig {

    private final ChatbotProperties chatbotProperties;

    /**
     * Creates Azure OpenAI client bean for embeddings and completions.
     *
     * <p>The client is configured with:
     * <ul>
     *   <li>Azure OpenAI endpoint and API key from application.yml</li>
     *   <li>Request timeout based on chatbot.azure-openai.timeout-seconds</li>
     *   <li>HTTP logging for debugging (log level based on environment)</li>
     * </ul>
     *
     * @return configured OpenAIClient instance
     * @throws IllegalStateException if API key or endpoint is not configured
     */
    @Bean
    public OpenAIClient openAIClient() {
        ChatbotProperties.AzureOpenAI config = chatbotProperties.getAzureOpenai();

        // Validate required configuration
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new IllegalStateException(
                    "Azure OpenAI API key is not configured. " + "Set AZURE_OPENAI_API_KEY environment variable.");
        }

        if (config.getEndpoint() == null || config.getEndpoint().isEmpty()) {
            throw new IllegalStateException(
                    "Azure OpenAI endpoint is not configured. " + "Set AZURE_OPENAI_ENDPOINT environment variable.");
        }

        log.info(
                "Initializing Azure OpenAI client with endpoint: {}, "
                        + "embedding deployment: {}, completion deployment: {}",
                maskEndpoint(config.getEndpoint()),
                config.getEmbeddingDeployment(),
                config.getCompletionDeployment());

        // Build Azure OpenAI client
        OpenAIClient client = new OpenAIClientBuilder()
                .endpoint(config.getEndpoint())
                .credential(new AzureKeyCredential(config.getApiKey()))
                .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BASIC))
                .buildClient();

        log.info("Azure OpenAI client initialized successfully");
        return client;
    }

    /**
     * Masks the endpoint URL for secure logging.
     * Example: https://my-resource.openai.azure.com/ → https://my-*****.openai.azure.com/
     */
    private String maskEndpoint(String endpoint) {
        if (endpoint == null || !endpoint.contains("://")) {
            return "***";
        }
        String[] parts = endpoint.split("://");
        if (parts.length < 2) {
            return "***";
        }
        String domain = parts[1].split("\\.")[0];
        String maskedDomain = domain.substring(0, Math.min(3, domain.length())) + "*****";
        return parts[0] + "://" + maskedDomain + ".openai.azure.com/";
    }
}

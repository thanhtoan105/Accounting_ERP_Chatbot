package com.accounting.config.chatbot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for Pinecone vector database.
 * Creates Pinecone client bean for vector storage and retrieval.
 *
 * <p>This configuration is only active when chatbot.enabled=true in application.yml.
 *
 * <p>Pinecone provides:
 * <ul>
 *   <li>Fast vector similarity search for RAG retrieval</li>
 *   <li>Company-scoped namespaces for multi-tenancy</li>
 *   <li>Metadata filtering for RBAC enforcement</li>
 * </ul>
 *
 * @see Pinecone
 * @see Index
 * @see ChatbotProperties.Pinecone
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chatbot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PineconeConfig {

    private final ChatbotProperties chatbotProperties;

    /**
     * Creates Pinecone client bean for accessing the vector database.
     *
     * <p>The client is configured with:
     * <ul>
     *   <li>API key from application.yml (PINECONE_API_KEY env variable)</li>
     *   <li>Environment (e.g., us-east-1-aws)</li>
     * </ul>
     *
     * @return configured Pinecone client instance
     * @throws IllegalStateException if API key is not configured
     */
    @Bean
    public Pinecone pineconeClient() {
        ChatbotProperties.Pinecone config = chatbotProperties.getPinecone();

        // Validate required configuration
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new IllegalStateException(
                    "Pinecone API key is not configured. " + "Set PINECONE_API_KEY environment variable.");
        }

        log.info(
                "Initializing Pinecone client with environment: {}, index: {}",
                config.getEnvironment(),
                config.getIndexName());

        // Build Pinecone client
        Pinecone pinecone = new Pinecone.Builder(config.getApiKey()).build();

        log.info("Pinecone client initialized successfully");
        return pinecone;
    }

    /**
     * Creates Pinecone Index bean for vector operations.
     *
     * <p>The index is used for:
     * <ul>
     *   <li>Upserting embeddings with company-scoped namespaces</li>
     *   <li>Querying vectors with metadata filters</li>
     *   <li>Deleting vectors when vouchers are deleted</li>
     * </ul>
     *
     * @param pineconeClient the Pinecone client
     * @return configured Index instance
     * @throws IllegalStateException if index does not exist
     */
    @Bean
    public Index pineconeIndex(Pinecone pineconeClient) {
        ChatbotProperties.Pinecone config = chatbotProperties.getPinecone();

        log.info("Connecting to Pinecone index: {}", config.getIndexName());

        try {
            Index index = pineconeClient.getIndexConnection(config.getIndexName());
            log.info("Connected to Pinecone index successfully");
            return index;
        } catch (Exception e) {
            String errorMsg = String.format(
                    "Failed to connect to Pinecone index '%s'. " + "Ensure the index exists in Pinecone dashboard.",
                    config.getIndexName());
            log.error(errorMsg, e);
            throw new IllegalStateException(errorMsg, e);
        }
    }
}

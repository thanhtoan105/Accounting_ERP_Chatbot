package com.accounting.service;

import java.util.List;

/**
 * Service interface for Azure OpenAI operations.
 *
 * <p>This service provides high-level abstractions for:
 * <ul>
 *   <li>Generating embeddings for text (RAG indexing and query encoding)</li>
 *   <li>Generating chat completions for LLM responses</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // Generate embedding for a voucher description
 * List<Float> embedding = azureOpenAIService.generateEmbedding(
 *     "Phiếu chi thanh toán tiền điện tháng 11/2025"
 * );
 *
 * // Generate chat completion with context
 * String answer = azureOpenAIService.generateCompletion(
 *     "Công nợ phải trả là bao nhiêu?",
 *     retrievedContext,
 *     "vi"
 * );
 * }</pre>
 *
 * @see com.azure.ai.openai.OpenAIClient
 * @see com.accounting.config.chatbot.AzureOpenAIConfig
 */
public interface AzureOpenAIService {

    /**
     * Generate embedding vector for text using Azure OpenAI.
     *
     * <p>Uses the configured embedding model (default: text-embedding-ada-002).
     * Returns a 1536-dimensional vector for semantic similarity search.
     *
     * <p><strong>Use Cases:</strong>
     * <ul>
     *   <li>Embedding voucher descriptions for Pinecone indexing</li>
     *   <li>Encoding user queries for semantic search</li>
     * </ul>
     *
     * @param text the text to embed (max 8191 tokens for ada-002)
     * @return embedding vector with 1536 dimensions
     * @throws IllegalArgumentException if text is null or exceeds token limit
     * @throws RuntimeException if Azure OpenAI API fails
     */
    List<Float> generateEmbedding(String text);

    /**
     * Generate chat completion response using Azure OpenAI.
     *
     * <p>Uses the configured completion model (default: gpt-35-turbo or gpt-4).
     * Constructs a prompt with system message, retrieved context, and user query.
     *
     * <p><strong>Response Format:</strong>
     * <ul>
     *   <li>Natural language answer in the specified language</li>
     *   <li>Grounded in the provided context (no hallucination)</li>
     *   <li>Vietnamese by default, with proper accounting terminology</li>
     * </ul>
     *
     * @param userQuery the user's question (e.g., "Công nợ phải trả là bao nhiêu?")
     * @param retrievedContext relevant context from RAG retrieval (voucher descriptions, balances)
     * @param language response language code ("vi" for Vietnamese, "en" for English)
     * @return generated response text
     * @throws IllegalArgumentException if userQuery is null or empty
     * @throws RuntimeException if Azure OpenAI API fails
     */
    String generateCompletion(String userQuery, String retrievedContext, String language);

    /**
     * Check if Azure OpenAI service is available and healthy.
     *
     * <p>Performs a lightweight health check without consuming tokens.
     *
     * @return true if Azure OpenAI is reachable, false otherwise
     */
    boolean isAvailable();
}

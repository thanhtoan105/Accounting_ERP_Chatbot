package com.accounting.service;

import java.util.List;

/**
 * Service interface for executing RAG queries via n8n workflow.
 * 
 * <p>This service delegates RAG processing to n8n workflow which handles:
 * <ul>
 *   <li>Query embedding generation</li>
 *   <li>Pinecone vector search with correct namespace</li>
 *   <li>LLM response generation with full context</li>
 *   <li>Citation extraction and formatting</li>
 * </ul>
 *
 * <p>Advantages over Java-native RAG:
 * <ul>
 *   <li>n8n workflow has direct access to embedded text content</li>
 *   <li>Consistent processing between embedding and query</li>
 *   <li>Easier to debug and modify without code deployment</li>
 * </ul>
 *
 * @see N8nWebhookService for embedding automation
 */
public interface N8nRAGQueryService {

    /**
     * Execute a RAG query via n8n workflow.
     *
     * @param query the user's natural language query
     * @param companyId the company ID for namespace isolation
     * @param userId the user ID for audit logging
     * @param sessionId session ID for conversation continuity
     * @param language response language (vi/en)
     * @return RAG query result with answer and citations
     */
    RAGQueryResult executeQuery(String query, Long companyId, Long userId, String sessionId, String language);

    /**
     * Check if n8n RAG query service is available.
     *
     * @return true if webhook is reachable
     */
    boolean isAvailable();

    /**
     * Result of RAG query execution.
     */
    record RAGQueryResult(
        String answer,
        List<Citation> citations,
        double confidenceScore,
        String confidenceBadge,
        String queryId,
        String sessionId,
        int responseTimeMs,
        String language,
        List<String> suggestions
    ) {}

    /**
     * Citation from RAG query result.
     */
    record Citation(
        String entityType,
        String entityId,
        String voucherNumber,
        String excerpt,
        double relevanceScore,
        String link
    ) {}
}

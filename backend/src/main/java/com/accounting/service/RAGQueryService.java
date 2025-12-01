package com.accounting.service;

import java.util.List;
import java.util.Map;

/**
 * Service interface for RAG (Retrieval-Augmented Generation) query processing.
 *
 * <p>This service orchestrates the retrieval phase of the RAG pipeline:
 * <ul>
 *   <li>Generate query embedding via Azure OpenAI</li>
 *   <li>Query Pinecone for semantically similar vouchers</li>
 *   <li>Load full voucher details from database</li>
 *   <li>Filter and rank results by relevance</li>
 *   <li>Build context string for LLM prompt</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * RetrievalResult result = ragQueryService.retrieveRelevantContext(
 *     "Công nợ phải trả là bao nhiêu?",
 *     companyId,
 *     userId,
 *     null  // no additional filters
 * );
 *
 * String context = result.getContextString();
 * List<Citation> citations = result.getCitations();
 * }</pre>
 *
 * @see AzureOpenAIService
 * @see PineconeService
 */
public interface RAGQueryService {

    /**
     * Retrieve relevant context for a user query using hybrid retrieval.
     *
     * <p>Retrieval flow:
     * <ol>
     *   <li>Generate embedding for user query (Azure OpenAI)</li>
     *   <li>Query Pinecone with company namespace + metadata filters</li>
     *   <li>Filter results by score threshold (default: 0.7)</li>
     *   <li>Load voucher details from database</li>
     *   <li>Build context string with voucher summaries</li>
     *   <li>Extract citations with voucher numbers and links</li>
     * </ol>
     *
     * @param queryText the user's question in natural language
     * @param companyId the company ID for namespace scoping
     * @param userId the user ID for RBAC filtering (future enhancement)
     * @param additionalFilters optional metadata filters (e.g., period_id)
     * @return RetrievalResult containing context string and citations
     * @throws IllegalArgumentException if queryText is null or empty
     * @throws RuntimeException if retrieval fails
     */
    RetrievalResult retrieveRelevantContext(
            String queryText, Long companyId, Long userId, Map<String, String> additionalFilters);

    /**
     * Result object containing retrieved context and citations.
     */
    class RetrievalResult {
        private final String contextString;
        private final List<Citation> citations;
        private final double averageScore;
        private final int totalMatches;

        public RetrievalResult(String contextString, List<Citation> citations, double averageScore, int totalMatches) {
            this.contextString = contextString;
            this.citations = citations;
            this.averageScore = averageScore;
            this.totalMatches = totalMatches;
        }

        public String getContextString() {
            return contextString;
        }

        public List<Citation> getCitations() {
            return citations;
        }

        public double getAverageScore() {
            return averageScore;
        }

        public int getTotalMatches() {
            return totalMatches;
        }
    }

    /**
     * Citation object linking to source voucher.
     */
    class Citation {
        private final String entityType;
        private final String entityId;
        private final String voucherNumber;
        private final String excerpt;
        private final double relevanceScore;
        private final String link;

        public Citation(
                String entityType,
                String entityId,
                String voucherNumber,
                String excerpt,
                double relevanceScore,
                String link) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.voucherNumber = voucherNumber;
            this.excerpt = excerpt;
            this.relevanceScore = relevanceScore;
            this.link = link;
        }

        public String getEntityType() {
            return entityType;
        }

        public String getEntityId() {
            return entityId;
        }

        public String getVoucherNumber() {
            return voucherNumber;
        }

        public String getExcerpt() {
            return excerpt;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }

        public String getLink() {
            return link;
        }
    }
}

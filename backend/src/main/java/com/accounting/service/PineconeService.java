package com.accounting.service;

import java.util.List;
import java.util.Map;

import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;

/**
 * Service interface for Pinecone vector database operations.
 *
 * <p>This service provides high-level abstractions for:
 * <ul>
 *   <li>Querying vectors for similarity search (RAG retrieval)</li>
 *   <li>Upserting (insert/update) vectors with metadata</li>
 *   <li>Deleting vectors by ID or namespace</li>
 * </ul>
 *
 * <p>All operations are company-scoped using namespaces in format: company-{companyId}
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // Query top 10 similar vectors
 * QueryResponseWithUnsignedIndices response = pineconeService.query(
 *     queryEmbedding,
 *     companyId,
 *     10,
 *     Map.of("period_id", "123")
 * );
 *
 * // Upsert a voucher embedding
 * pineconeService.upsert(
 *     "voucher_456",
 *     embedding,
 *     Map.of("voucher_number", "PT-001", "created_at", "2025-01-24"),
 *     companyId
 * );
 * }</pre>
 *
 * @see io.pinecone.clients.Index
 * @see com.accounting.config.chatbot.PineconeConfig
 */
public interface PineconeService {

    /**
     * Query Pinecone for similar vectors using semantic search.
     *
     * <p>This method performs hybrid retrieval:
     * <ol>
     *   <li>Semantic similarity search using the query embedding</li>
     *   <li>Metadata filtering for company-scoped results</li>
     *   <li>Returns top K results above the configured score threshold</li>
     * </ol>
     *
     * @param queryEmbedding the query vector (1536 dimensions for ada-002)
     * @param companyId the company ID for namespace scoping
     * @param topK number of top results to return (default: 10)
     * @param metadataFilters optional metadata filters (e.g., period_id, created_by)
     * @return QueryResponseWithUnsignedIndices containing matched vectors with scores and metadata
     * @throws IllegalArgumentException if queryEmbedding dimensions don't match index
     * @throws io.pinecone.exceptions.PineconeException if Pinecone API fails
     */
    QueryResponseWithUnsignedIndices query(List<Float> queryEmbedding, Long companyId, int topK, Map<String, String> metadataFilters);

    /**
     * Upsert (insert or update) a vector with metadata to Pinecone.
     *
     * <p>Idempotent operation: if vector ID exists, it will be updated.
     *
     * <p><strong>Metadata Best Practices:</strong>
     * <ul>
     *   <li>Include voucher_number, voucher_date for citation generation</li>
     *   <li>Include period_id for time-based filtering</li>
     *   <li>Include created_by for RBAC filtering (future enhancement)</li>
     * </ul>
     *
     * @param vectorId unique ID for the vector (e.g., "voucher_123")
     * @param embedding the vector to store (1536 dimensions for ada-002)
     * @param metadata key-value pairs for metadata filtering
     * @param companyId the company ID for namespace scoping
     * @throws IllegalArgumentException if embedding dimensions don't match index
     * @throws io.pinecone.exceptions.PineconeException if Pinecone API fails
     */
    void upsert(String vectorId, List<Float> embedding, Map<String, String> metadata, Long companyId);

    /**
     * Delete a vector by ID from the company namespace.
     *
     * <p>Use this when a voucher is deleted or should no longer be searchable.
     *
     * @param vectorId the vector ID to delete (e.g., "voucher_123")
     * @param companyId the company ID for namespace scoping
     * @throws io.pinecone.exceptions.PineconeException if Pinecone API fails
     */
    void delete(String vectorId, Long companyId);

    /**
     * Delete all vectors in a company namespace.
     *
     * <p><strong>WARNING:</strong> This operation is destructive and irreversible.
     * Use with caution, primarily for:
     * <ul>
     *   <li>Company data deletion (GDPR compliance)</li>
     *   <li>Full re-indexing scenarios</li>
     * </ul>
     *
     * @param companyId the company ID whose namespace to clear
     * @throws io.pinecone.exceptions.PineconeException if Pinecone API fails
     */
    void deleteNamespace(Long companyId);

    /**
     * Check if Pinecone service is available and healthy.
     *
     * <p>Performs a lightweight health check without querying vectors.
     *
     * @return true if Pinecone is reachable, false otherwise
     */
    boolean isAvailable();

    /**
     * Build company-scoped namespace string.
     *
     * <p>Format: company-{companyId}
     * Example: company-12345
     *
     * @param companyId the company ID
     * @return namespace string
     */
    default String buildNamespace(Long companyId) {
        return "company-" + companyId;
    }
}

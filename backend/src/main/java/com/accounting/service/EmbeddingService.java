package com.accounting.service;

import com.accounting.entity.Voucher;

/**
 * Service for direct voucher embedding operations (placeholder for future implementation).
 *
 * <p><strong>MVP Status:</strong> This interface is a placeholder for future direct embedding
 * functionality. The MVP implementation (Story 9.0) uses n8n webhook automation via
 * {@link N8nWebhookService} instead of direct embedding.
 *
 * <p><strong>Future Implementation (Post-MVP):</strong> Direct embedding will bypass n8n
 * for lower latency and provide more control over the embedding pipeline. This will require:
 * <ul>
 *   <li>Direct integration with Azure OpenAI embedding API</li>
 *   <li>Direct integration with Pinecone upsert/delete operations</li>
 *   <li>Async processing queue for high-volume voucher creation</li>
 *   <li>Batch embedding optimization for multiple vouchers</li>
 * </ul>
 *
 * @see N8nWebhookService for current MVP implementation
 */
public interface EmbeddingService {

    /**
     * Generate and store embedding for a voucher in Pinecone (future implementation).
     *
     * <p>This method will:
     * <ol>
     *   <li>Extract relevant text from voucher (header + line items)</li>
     *   <li>Generate embedding vector using Azure OpenAI ada-002</li>
     *   <li>Upsert to Pinecone under company-specific namespace</li>
     *   <li>Include metadata for filtering (period_id, created_by, voucher_date)</li>
     * </ol>
     *
     * @param voucher The voucher entity to embed
     * @throws UnsupportedOperationException in MVP - not yet implemented
     */
    void embedVoucher(Voucher voucher);

    /**
     * Delete voucher embedding from Pinecone (future implementation).
     *
     * <p>Used when vouchers are deleted or need to be re-indexed.
     *
     * @param voucherId The voucher ID to delete from vector store
     * @param companyId The company ID for namespace scoping
     * @throws UnsupportedOperationException in MVP - not yet implemented
     */
    void deleteEmbedding(String voucherId, Long companyId);

    /**
     * Batch embed multiple vouchers for efficiency (future implementation).
     *
     * <p>Optimizes API calls by batching embeddings and upserts.
     *
     * @param vouchers List of vouchers to embed
     * @throws UnsupportedOperationException in MVP - not yet implemented
     */
    void embedVouchersBatch(Iterable<Voucher> vouchers);
}

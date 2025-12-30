package com.accounting.service;

import java.util.concurrent.CompletableFuture;

import com.accounting.dto.VoucherEmbeddingPayload;
import com.accounting.dto.embedding.EntityEmbeddingPayload;

/**
 * Service for triggering n8n webhook automation for entity embedding.
 * Implements fire-and-forget pattern with retry logic for fault tolerance.
 *
 * <p>Supports multiple entity types: vouchers, customers, suppliers,
 * chart of accounts, invoices, payments, and regulatory content.</p>
 */
public interface N8nWebhookService {

    /**
     * Trigger n8n webhook to generate and store entity embeddings in Pinecone.
     * This is a fire-and-forget operation that runs asynchronously.
     *
     * <p>The payload contains pre-synthesized text, so n8n workflow is entity-agnostic.
     * It simply generates embeddings from the text field and upserts to Pinecone.</p>
     *
     * @param payload Entity data with pre-synthesized text and metadata
     * @return CompletableFuture that completes when webhook call finishes (or fails)
     */
    CompletableFuture<Void> triggerEntityEmbedding(EntityEmbeddingPayload payload);

    /**
     * Trigger entity embedding synchronously with retry logic.
     * Used for testing and scenarios requiring immediate feedback.
     *
     * @param payload Entity data with pre-synthesized text and metadata
     * @throws RuntimeException if all retry attempts fail
     */
    void triggerEntityEmbeddingSync(EntityEmbeddingPayload payload);

    /**
     * Trigger n8n webhook to generate and store voucher embeddings in Pinecone.
     * This is a fire-and-forget operation that runs asynchronously.
     *
     * @param payload Voucher data to be embedded (header + line items + metadata)
     * @return CompletableFuture that completes when webhook call finishes (or fails)
     * @deprecated Use {@link #triggerEntityEmbedding(EntityEmbeddingPayload)} instead.
     *             This method will be removed in a future release.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    CompletableFuture<Void> triggerEmbedding(VoucherEmbeddingPayload payload);

    /**
     * Trigger embedding synchronously with retry logic.
     * Used for testing and scenarios requiring immediate feedback.
     *
     * @param payload Voucher data to be embedded
     * @throws RuntimeException if all retry attempts fail
     * @deprecated Use {@link #triggerEntityEmbeddingSync(EntityEmbeddingPayload)} instead.
     *             This method will be removed in a future release.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    void triggerEmbeddingSync(VoucherEmbeddingPayload payload);

    /**
     * Check if n8n webhook service is available (health check).
     *
     * @return true if webhook endpoint is reachable, false otherwise
     */
    boolean isAvailable();
}

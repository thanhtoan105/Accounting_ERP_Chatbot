package com.accounting.service;

import java.util.concurrent.CompletableFuture;

import com.accounting.dto.VoucherEmbeddingPayload;

/**
 * Service for triggering n8n webhook automation for voucher embedding.
 * Implements fire-and-forget pattern with retry logic for fault tolerance.
 */
public interface N8nWebhookService {

    /**
     * Trigger n8n webhook to generate and store voucher embeddings in Pinecone.
     * This is a fire-and-forget operation that runs asynchronously.
     *
     * @param payload Voucher data to be embedded (header + line items + metadata)
     * @return CompletableFuture that completes when webhook call finishes (or fails)
     */
    CompletableFuture<Void> triggerEmbedding(VoucherEmbeddingPayload payload);

    /**
     * Trigger embedding synchronously with retry logic.
     * Used for testing and scenarios requiring immediate feedback.
     *
     * @param payload Voucher data to be embedded
     * @throws RuntimeException if all retry attempts fail
     */
    void triggerEmbeddingSync(VoucherEmbeddingPayload payload);

    /**
     * Check if n8n webhook service is available (health check).
     *
     * @return true if webhook endpoint is reachable, false otherwise
     */
    boolean isAvailable();
}

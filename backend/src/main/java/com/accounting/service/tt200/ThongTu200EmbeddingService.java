package com.accounting.service.tt200;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for embedding ThongTu200 regulatory content into Pinecone via n8n workflow.
 * Supports async batch processing with rate limiting.
 */
public interface ThongTu200EmbeddingService {

    /**
     * Embed all parsed chunks from ThongTu200 PDF asynchronously.
     * Rate-limited to 5-10 requests/second to avoid overwhelming n8n.
     *
     * @return CompletableFuture with embedding result
     */
    CompletableFuture<EmbeddingResult> embedAllChunks();

    /**
     * Embed chunks matching a specific account code prefix.
     *
     * @param prefix Account code prefix (e.g., "111" for cash accounts)
     * @return CompletableFuture with embedding result
     */
    CompletableFuture<EmbeddingResult> embedByAccountCode(String prefix);

    /**
     * Get current embedding job status.
     *
     * @return Current status or null if no job running
     */
    EmbeddingStatus getStatus();

    /**
     * Cancel the current embedding job if running.
     */
    void cancelEmbedding();

    /**
     * Result of an embedding operation.
     */
    record EmbeddingResult(
            int totalChunks,
            int successCount,
            int failedCount,
            List<String> failedChunkIds,
            Duration duration) {
    }

    /**
     * Status of an embedding job.
     */
    record EmbeddingStatus(
            String jobId,
            JobState state,
            int totalChunks,
            int processedChunks,
            int failedChunks,
            Instant startedAt,
            Instant completedAt,
            Duration elapsed) {

        public enum JobState {
            IDLE,
            IN_PROGRESS,
            COMPLETED,
            FAILED,
            CANCELLED
        }
    }
}

package com.accounting.service.tt200;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.dto.embedding.EntityEmbeddingPayload;
import com.accounting.dto.embedding.EntityType;
import com.accounting.dto.tt200.ThongTu200Chunk;
import com.accounting.service.EntityTextSynthesizer;
import com.accounting.service.N8nWebhookService;

@Service
public class ThongTu200EmbeddingServiceImpl implements ThongTu200EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ThongTu200EmbeddingServiceImpl.class);

    private static final int RATE_LIMIT_PERMITS = 5;
    private static final long RATE_LIMIT_DELAY_MS = 200;

    private final ThongTu200ParserService parserService;
    private final EntityTextSynthesizer textSynthesizer;
    private final N8nWebhookService webhookService;

    private final Semaphore rateLimiter = new Semaphore(RATE_LIMIT_PERMITS);

    private volatile EmbeddingStatus currentStatus;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final List<String> failedChunkIds = new ArrayList<>();

    public ThongTu200EmbeddingServiceImpl(
            ThongTu200ParserService parserService,
            EntityTextSynthesizer textSynthesizer,
            N8nWebhookService webhookService) {
        this.parserService = parserService;
        this.textSynthesizer = textSynthesizer;
        this.webhookService = webhookService;
    }

    @Override
    @Async
    public CompletableFuture<EmbeddingResult> embedAllChunks() {
        List<ThongTu200Chunk> chunks = parserService.parseAllChunks();
        return embedChunks(chunks, "all");
    }

    @Override
    @Async
    public CompletableFuture<EmbeddingResult> embedByAccountCode(String prefix) {
        List<ThongTu200Chunk> chunks = parserService.parseByAccountCode(prefix);
        return embedChunks(chunks, "account-" + prefix);
    }

    @Override
    public EmbeddingStatus getStatus() {
        if (currentStatus == null) {
            return new EmbeddingStatus(
                    null,
                    EmbeddingStatus.JobState.IDLE,
                    0, 0, 0, null, null, Duration.ZERO);
        }

        if (currentStatus.state() == EmbeddingStatus.JobState.IN_PROGRESS) {
            return new EmbeddingStatus(
                    currentStatus.jobId(),
                    EmbeddingStatus.JobState.IN_PROGRESS,
                    currentStatus.totalChunks(),
                    processedCount.get(),
                    failedCount.get(),
                    currentStatus.startedAt(),
                    null,
                    Duration.between(currentStatus.startedAt(), Instant.now()));
        }
        return currentStatus;
    }

    @Override
    public void cancelEmbedding() {
        cancelled.set(true);
        log.info("Embedding job cancellation requested");
    }

    private CompletableFuture<EmbeddingResult> embedChunks(List<ThongTu200Chunk> chunks, String jobType) {
        if (chunks.isEmpty()) {
            log.info("No chunks to embed for job type: {}", jobType);
            return CompletableFuture.completedFuture(
                    new EmbeddingResult(0, 0, 0, List.of(), Duration.ZERO));
        }

        String jobId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();

        cancelled.set(false);
        processedCount.set(0);
        failedCount.set(0);
        failedChunkIds.clear();

        currentStatus = new EmbeddingStatus(
                jobId,
                EmbeddingStatus.JobState.IN_PROGRESS,
                chunks.size(),
                0, 0,
                startTime, null, Duration.ZERO);

        log.info("Starting TT200 embedding job {} with {} chunks", jobId, chunks.size());

        for (ThongTu200Chunk chunk : chunks) {
            if (cancelled.get()) {
                log.info("Embedding job {} cancelled at chunk {}", jobId, processedCount.get());
                break;
            }

            try {
                rateLimiter.acquire();

                EntityEmbeddingPayload payload = buildPayload(chunk);
                webhookService.triggerEntityEmbedding(payload);
                processedCount.incrementAndGet();

                Thread.sleep(RATE_LIMIT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Embedding job {} interrupted", jobId);
                break;
            } catch (Exception e) {
                log.error("Failed to embed chunk {}: {}", chunk.chunkId(), e.getMessage());
                failedCount.incrementAndGet();
                synchronized (failedChunkIds) {
                    failedChunkIds.add(chunk.chunkId());
                }
            } finally {
                rateLimiter.release();
            }
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);

        EmbeddingStatus.JobState finalState;
        if (cancelled.get()) {
            finalState = EmbeddingStatus.JobState.CANCELLED;
        } else if (failedCount.get() > 0 && failedCount.get() == chunks.size()) {
            finalState = EmbeddingStatus.JobState.FAILED;
        } else {
            finalState = EmbeddingStatus.JobState.COMPLETED;
        }

        currentStatus = new EmbeddingStatus(
                jobId,
                finalState,
                chunks.size(),
                processedCount.get(),
                failedCount.get(),
                startTime,
                endTime,
                duration);

        log.info("TT200 embedding job {} completed: {} success, {} failed, duration: {}",
                jobId, processedCount.get() - failedCount.get(), failedCount.get(), duration);

        return CompletableFuture.completedFuture(
                new EmbeddingResult(
                        chunks.size(),
                        processedCount.get() - failedCount.get(),
                        failedCount.get(),
                        new ArrayList<>(failedChunkIds),
                        duration));
    }

    private EntityEmbeddingPayload buildPayload(ThongTu200Chunk chunk) {
        String text = synthesizeText(chunk);
        Map<String, Object> metadata = buildMetadata(chunk);

        return textSynthesizer.buildGenericPayload(
                EntityType.REGULATORY_TT200,
                chunk.chunkId(),
                null,
                text,
                metadata,
                EmbeddingAction.UPSERT);
    }

    private String synthesizeText(ThongTu200Chunk chunk) {
        StringBuilder sb = new StringBuilder();

        sb.append("Điều ").append(chunk.articleNumber()).append(". ").append(chunk.articleTitle()).append("\n");
        sb.append("Chương ").append(chunk.chapterNumber()).append(": ").append(chunk.chapterTitle()).append("\n");

        if (chunk.accountCode() != null) {
            sb.append("Tài khoản ").append(chunk.accountCode()).append("\n");
        }

        sb.append("\n").append(chunk.content());

        if (chunk.subAccounts() != null && !chunk.subAccounts().isEmpty()) {
            sb.append("\n\nTài khoản con: ").append(String.join(", ", chunk.subAccounts()));
        }

        return sb.toString();
    }

    private Map<String, Object> buildMetadata(ThongTu200Chunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "TT200");
        metadata.put("documentVersion", "2014");
        metadata.put("chapterNumber", chunk.chapterNumber());
        metadata.put("chapterTitle", chunk.chapterTitle());
        metadata.put("articleNumber", chunk.articleNumber());
        metadata.put("articleTitle", chunk.articleTitle());

        if (chunk.accountCode() != null) {
            metadata.put("accountCode", chunk.accountCode());
            metadata.put("accountCodePrimary", chunk.accountCode());
        }

        if (chunk.subAccounts() != null && !chunk.subAccounts().isEmpty()) {
            metadata.put("subAccounts", chunk.subAccounts());
            metadata.put("accountCodes", String.join(",", chunk.subAccounts()));
        }

        metadata.put("sectionType", chunk.sectionType());
        metadata.put("language", "vi");
        metadata.put("pageStart", chunk.pageStart());
        metadata.put("pageEnd", chunk.pageEnd());
        metadata.put("sourcePdfHash", chunk.pdfHash());

        return metadata;
    }
}

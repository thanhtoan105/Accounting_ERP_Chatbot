package com.accounting.controller.admin;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.tt200.ParserStats;
import com.accounting.dto.tt200.ThongTu200Chunk;
import com.accounting.service.tt200.ThongTu200EmbeddingService;
import com.accounting.service.tt200.ThongTu200EmbeddingService.EmbeddingStatus;
import com.accounting.service.tt200.ThongTu200ParserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/tt200")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "ThongTu200 Admin", description = "Admin endpoints for ThongTu200 regulatory content embedding")
public class ThongTu200Controller {

    private static final Logger log = LoggerFactory.getLogger(ThongTu200Controller.class);

    private final ThongTu200EmbeddingService embeddingService;
    private final ThongTu200ParserService parserService;

    public ThongTu200Controller(
            ThongTu200EmbeddingService embeddingService,
            ThongTu200ParserService parserService) {
        this.embeddingService = embeddingService;
        this.parserService = parserService;
    }

    @PostMapping("/embed")
    @Operation(summary = "Trigger full TT200 embedding", description = "Start async job to embed all TT200 chunks to Pinecone")
    public ResponseEntity<EmbeddingJobResponse> triggerFullEmbedding() {
        log.info("Admin triggered full TT200 embedding");

        EmbeddingStatus currentStatus = embeddingService.getStatus();
        if (currentStatus != null && currentStatus.state() == EmbeddingStatus.JobState.IN_PROGRESS) {
            return ResponseEntity.badRequest().body(
                    new EmbeddingJobResponse(
                            currentStatus.jobId(),
                            "ALREADY_RUNNING",
                            currentStatus.startedAt(),
                            "An embedding job is already in progress"));
        }

        embeddingService.embedAllChunks();

        EmbeddingStatus status = embeddingService.getStatus();
        return ResponseEntity.accepted().body(
                new EmbeddingJobResponse(
                        status.jobId(),
                        "STARTED",
                        status.startedAt(),
                        "Embedding job started for all TT200 chunks"));
    }

    @PostMapping("/embed/account/{prefix}")
    @Operation(summary = "Embed by account prefix", description = "Embed chunks matching account code prefix")
    public ResponseEntity<EmbeddingJobResponse> embedByAccountPrefix(@PathVariable String prefix) {
        log.info("Admin triggered TT200 embedding for account prefix: {}", prefix);

        EmbeddingStatus currentStatus = embeddingService.getStatus();
        if (currentStatus != null && currentStatus.state() == EmbeddingStatus.JobState.IN_PROGRESS) {
            return ResponseEntity.badRequest().body(
                    new EmbeddingJobResponse(
                            currentStatus.jobId(),
                            "ALREADY_RUNNING",
                            currentStatus.startedAt(),
                            "An embedding job is already in progress"));
        }

        embeddingService.embedByAccountCode(prefix);

        EmbeddingStatus status = embeddingService.getStatus();
        return ResponseEntity.accepted().body(
                new EmbeddingJobResponse(
                        status.jobId(),
                        "STARTED",
                        status.startedAt(),
                        "Embedding job started for account prefix: " + prefix));
    }

    @GetMapping("/status")
    @Operation(summary = "Get embedding status", description = "Get current embedding job status")
    public ResponseEntity<EmbeddingStatus> getStatus() {
        EmbeddingStatus status = embeddingService.getStatus();
        return ResponseEntity.ok(status);
    }

    @DeleteMapping("/cancel")
    @Operation(summary = "Cancel embedding job", description = "Cancel the currently running embedding job")
    public ResponseEntity<Void> cancelEmbedding() {
        log.info("Admin requested embedding cancellation");
        embeddingService.cancelEmbedding();
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/parser/stats")
    @Operation(summary = "Get parser stats", description = "Get statistics about parsed TT200 content")
    public ResponseEntity<ParserStats> getParserStats() {
        ParserStats stats = parserService.getStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/preview")
    @Operation(summary = "Preview chunks", description = "Preview parsed chunks without embedding")
    public ResponseEntity<List<ThongTu200Chunk>> previewChunks(
            @RequestParam(defaultValue = "10") int limit) {
        List<ThongTu200Chunk> chunks = parserService.parseAllChunks();
        List<ThongTu200Chunk> preview = chunks.stream().limit(limit).toList();
        return ResponseEntity.ok(preview);
    }

    public record EmbeddingJobResponse(
            String jobId,
            String status,
            Instant startedAt,
            String message) {
    }
}

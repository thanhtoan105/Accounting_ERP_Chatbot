package com.accounting.controller.admin;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.embedding.EntityType;
import com.accounting.service.BatchEntityEmbeddingService;
import com.accounting.service.BatchEntityEmbeddingService.BatchEmbeddingResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin controller for batch embedding operations.
 * Used to embed entities into Pinecone for RAG chatbot.
 */
@RestController
@RequestMapping("/api/v1/admin/embeddings")
@Tag(name = "Admin - Embeddings", description = "Batch embedding operations for RAG chatbot")
@PreAuthorize("hasRole('ADMIN')")
public class BatchEmbeddingController {

    private final BatchEntityEmbeddingService embeddingService;

    public BatchEmbeddingController(BatchEntityEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Operation(summary = "Get entity counts for a company")
    @GetMapping("/companies/{companyId}/counts")
    public ResponseEntity<Map<EntityType, Long>> getEntityCounts(@PathVariable Long companyId) {
        return ResponseEntity.ok(embeddingService.getEntityCounts(companyId));
    }

    @Operation(summary = "Embed sample entities for testing (1-2 per type)")
    @PostMapping("/companies/{companyId}/sample/{entityType}")
    public ResponseEntity<BatchEmbeddingResult> embedSample(
            @PathVariable Long companyId,
            @PathVariable EntityType entityType,
            @RequestParam(defaultValue = "2") int sampleSize) {
        return ResponseEntity.ok(embeddingService.embedSample(companyId, entityType, sampleSize));
    }

    @Operation(summary = "Embed all entities of a specific type")
    @PostMapping("/companies/{companyId}/all/{entityType}")
    public ResponseEntity<BatchEmbeddingResult> embedAll(
            @PathVariable Long companyId,
            @PathVariable EntityType entityType) {
        return ResponseEntity.ok(embeddingService.embedAll(companyId, entityType));
    }

    @Operation(summary = "Embed all entity types for a company")
    @PostMapping("/companies/{companyId}/all")
    public ResponseEntity<Map<EntityType, BatchEmbeddingResult>> embedAllTypes(
            @PathVariable Long companyId) {
        return ResponseEntity.ok(embeddingService.embedAllTypes(companyId));
    }
}

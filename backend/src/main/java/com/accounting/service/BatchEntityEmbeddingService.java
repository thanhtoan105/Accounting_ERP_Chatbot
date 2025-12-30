package com.accounting.service;

import com.accounting.dto.embedding.EntityType;

/**
 * Service for batch embedding of entities into Pinecone via n8n webhook.
 * Supports embedding individual entities or batches per entity type.
 */
public interface BatchEntityEmbeddingService {

    /**
     * Result of batch embedding operation.
     */
    record BatchEmbeddingResult(
        EntityType entityType,
        int total,
        int success,
        int failed,
        long durationMs
    ) {}

    /**
     * Embed a sample of entities for testing (1-2 per type).
     *
     * @param companyId company ID
     * @param entityType entity type to embed
     * @param sampleSize number of entities to sample (default 2)
     * @return result with counts
     */
    BatchEmbeddingResult embedSample(Long companyId, EntityType entityType, int sampleSize);

    /**
     * Embed all entities of a specific type for a company.
     *
     * @param companyId company ID
     * @param entityType entity type to embed
     * @return result with counts
     */
    BatchEmbeddingResult embedAll(Long companyId, EntityType entityType);

    /**
     * Embed all entity types for a company.
     *
     * @param companyId company ID
     * @return combined result with counts per type
     */
    java.util.Map<EntityType, BatchEmbeddingResult> embedAllTypes(Long companyId);

    /**
     * Get embedding status for a company (counts per entity type).
     *
     * @param companyId company ID
     * @return map of entity type to total count in DB
     */
    java.util.Map<EntityType, Long> getEntityCounts(Long companyId);
}

package com.accounting.dto.embedding;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified payload for embedding any entity type into Pinecone via n8n workflow.
 *
 * <p>This payload is entity-agnostic: the text is pre-synthesized in Java,
 * so the n8n workflow doesn't need entity-specific logic. It simply:
 * 1. Receives this payload
 * 2. Generates embedding from the text field
 * 3. Upserts/deletes in Pinecone with the provided metadata
 *
 * <p>Example payload for a voucher:
 * <pre>{@code
 * {
 *   "entityType": "VOUCHER",
 *   "entityId": "550e8400-e29b-41d4-a716-446655440000",
 *   "companyId": 1,
 *   "action": "UPSERT",
 *   "text": "Chứng từ ghi sổ số PC-2024-001 ngày 2024-12-28...",
 *   "metadata": {
 *     "voucherNumber": "PC-2024-001",
 *     "voucherDate": "2024-12-28",
 *     "status": "POSTED"
 *   },
 *   "namespace": "company_1",
 *   "timestamp": "2024-12-28T10:30:00Z"
 * }
 * }</pre>
 *
 * @param entityType The type of entity being embedded
 * @param entityId Unique identifier of the entity (UUID as string)
 * @param companyId Company ID for multi-tenancy (null for global entities like TT200)
 * @param action Whether to upsert or delete the embedding
 * @param text Pre-synthesized text for embedding (Vietnamese, human-readable)
 * @param metadata Entity-specific metadata for Pinecone filtering and citations
 * @param namespace Pinecone namespace (computed from entityType and companyId)
 * @param timestamp When this embedding was generated
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntityEmbeddingPayload(
    EntityType entityType,
    String entityId,
    Long companyId,
    EmbeddingAction action,
    String text,
    Map<String, Object> metadata,
    String namespace,
    Instant timestamp
) {

    /**
     * Builder for EntityEmbeddingPayload with sensible defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EntityType entityType;
        private String entityId;
        private Long companyId;
        private EmbeddingAction action = EmbeddingAction.UPSERT;
        private String text;
        private Map<String, Object> metadata;
        private String namespace;
        private Instant timestamp;

        public Builder entityType(EntityType entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public Builder action(EmbeddingAction action) {
            this.action = action;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public EntityEmbeddingPayload build() {
            if (entityType == null) {
                throw new IllegalStateException("entityType is required");
            }
            if (entityId == null || entityId.isBlank()) {
                throw new IllegalStateException("entityId is required");
            }
            if (entityType.isCompanyScoped() && companyId == null) {
                throw new IllegalStateException("companyId is required for company-scoped entities");
            }
            if (action == EmbeddingAction.UPSERT && (text == null || text.isBlank())) {
                throw new IllegalStateException("text is required for UPSERT action");
            }

            // Auto-compute namespace if not provided
            if (namespace == null) {
                namespace = entityType.getPineconeNamespace(companyId);
            }

            // Auto-set timestamp if not provided
            if (timestamp == null) {
                timestamp = Instant.now();
            }

            return new EntityEmbeddingPayload(
                entityType, entityId, companyId, action, text, metadata, namespace, timestamp
            );
        }
    }
}

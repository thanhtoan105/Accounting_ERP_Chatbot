package com.accounting.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.accounting.config.chatbot.ChatbotProperties;
import com.accounting.service.PineconeService;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import io.pinecone.clients.Index;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of PineconeService for vector database operations.
 *
 * <p>This service wraps the Pinecone Java SDK with:
 * <ul>
 *   <li>Company-scoped namespaces for multi-tenancy</li>
 *   <li>Error handling and logging</li>
 *   <li>Configuration from application.yml</li>
 * </ul>
 *
 * <p>Only active when chatbot.enabled=true in application.yml.
 *
 * @see PineconeService
 * @see Index
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chatbot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PineconeServiceImpl implements PineconeService {

    private final Index pineconeIndex;
    private final ChatbotProperties chatbotProperties;

    @Override
    public QueryResponseWithUnsignedIndices query(
            List<Float> queryEmbedding, Long companyId, int topK, Map<String, String> metadataFilters) {

        String namespace = buildNamespace(companyId);

        log.debug(
                "Querying Pinecone: namespace={}, topK={}, filters={}",
                namespace,
                topK,
                metadataFilters != null ? metadataFilters.size() : 0);

        try {
            // Build metadata filters if provided
            Struct filter = null;
            if (metadataFilters != null && !metadataFilters.isEmpty()) {
                Struct.Builder filterBuilder = Struct.newBuilder();
                metadataFilters.forEach((key, value) -> {
                    filterBuilder.putFields(key, Value.newBuilder().setStringValue(value).build());
                });
                filter = filterBuilder.build();
            }

            // Execute query using Pinecone SDK v2.x API
            // Method signature: query(int topK, List<Float> values, List<Long> sparseIndices,
            //                         List<Float> sparseValues, String id, String namespace,
            //                         Struct filter, boolean includeValues, boolean includeMetadata)
            QueryResponseWithUnsignedIndices response = pineconeIndex.query(
                    topK,                    // topK
                    queryEmbedding,          // values (dense vector)
                    null,                    // sparseIndices (not used in MVP)
                    null,                    // sparseValues (not used in MVP)
                    null,                    // id (not used for semantic search)
                    namespace,               // namespace
                    filter,                  // metadata filter
                    false,                   // includeValues (don't return embeddings)
                    true                     // includeMetadata (return metadata for citations)
            );

            int matchCount = response.getMatchesList().size();
            log.info("Pinecone query completed: namespace={}, matches={}", namespace, matchCount);

            return response;

        } catch (Exception e) {
            log.error("Pinecone query failed: namespace={}, error={}", namespace, e.getMessage(), e);
            throw new RuntimeException("Failed to query Pinecone for company " + companyId, e);
        }
    }

    @Override
    public void upsert(String vectorId, List<Float> embedding, Map<String, String> metadata, Long companyId) {

        String namespace = buildNamespace(companyId);

        log.debug("Upserting to Pinecone: namespace={}, vectorId={}, metadataKeys={}", namespace, vectorId, metadata != null ? metadata.keySet() : Collections.emptySet());

        try {
            // Build metadata Struct
            Struct metadataStruct = null;
            if (metadata != null && !metadata.isEmpty()) {
                Struct.Builder metadataBuilder = Struct.newBuilder();
                metadata.forEach((key, value) -> {
                    metadataBuilder.putFields(key, Value.newBuilder().setStringValue(value).build());
                });
                metadataStruct = metadataBuilder.build();
            }

            // Execute upsert using Pinecone SDK v2.x API
            // Method signature: upsert(String id, List<Float> values, List<Long> sparseIndices,
            //                          List<Float> sparseValues, Struct metadata, String namespace)
            pineconeIndex.upsert(
                    vectorId,                // id
                    embedding,               // values (dense vector)
                    null,                    // sparseIndices (not used in MVP)
                    null,                    // sparseValues (not used in MVP)
                    metadataStruct,          // metadata
                    namespace                // namespace
            );

            log.info("Pinecone upsert completed: namespace={}, vectorId={}", namespace, vectorId);

        } catch (Exception e) {
            log.error("Pinecone upsert failed: namespace={}, vectorId={}, error={}", namespace, vectorId, e.getMessage(), e);
            throw new RuntimeException("Failed to upsert vector to Pinecone for company " + companyId, e);
        }
    }

    @Override
    public void delete(String vectorId, Long companyId) {
        String namespace = buildNamespace(companyId);

        log.debug("Deleting from Pinecone: namespace={}, vectorId={}", namespace, vectorId);

        try {
            // Execute delete using Pinecone SDK v2.x API
            // Method signature: delete(List<String> ids, boolean deleteAll, String namespace, Struct filter)
            pineconeIndex.delete(
                    List.of(vectorId),       // ids to delete
                    false,                   // deleteAll (false for specific IDs)
                    namespace,               // namespace
                    null                     // filter (not used for ID-based deletion)
            );

            log.info("Pinecone delete completed: namespace={}, vectorId={}", namespace, vectorId);

        } catch (Exception e) {
            log.error("Pinecone delete failed: namespace={}, vectorId={}, error={}", namespace, vectorId, e.getMessage(), e);
            // Don't throw exception for delete failures - log and continue
            // This prevents voucher deletion from failing if Pinecone is down
        }
    }

    @Override
    public void deleteNamespace(Long companyId) {
        String namespace = buildNamespace(companyId);

        log.warn("Deleting entire Pinecone namespace: namespace={}", namespace);

        try {
            // Execute delete all using Pinecone SDK v2.x API
            // Method signature: delete(List<String> ids, boolean deleteAll, String namespace, Struct filter)
            pineconeIndex.delete(
                    Collections.emptyList(), // ids (empty for deleteAll)
                    true,                    // deleteAll (true to delete entire namespace)
                    namespace,               // namespace
                    null                     // filter (not used for deleteAll)
            );

            log.info("Pinecone namespace deleted: namespace={}", namespace);

        } catch (Exception e) {
            log.error("Pinecone namespace delete failed: namespace={}, error={}", namespace, e.getMessage(), e);
            throw new RuntimeException("Failed to delete Pinecone namespace for company " + companyId, e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // Lightweight health check: attempt to describe index stats
            // This doesn't query vectors, just checks if Pinecone is reachable
            pineconeIndex.describeIndexStats();
            return true;
        } catch (Exception e) {
            log.warn("Pinecone health check failed: {}", e.getMessage());
            return false;
        }
    }
}

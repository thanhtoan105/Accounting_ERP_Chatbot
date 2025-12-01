package com.accounting.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.accounting.config.chatbot.ChatbotProperties;
import com.accounting.service.AzureOpenAIService;
import com.accounting.service.PineconeService;
import com.accounting.service.RAGQueryService;
import com.google.protobuf.Struct;

import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of RAGQueryService for hybrid retrieval.
 *
 * <p>
 * This service orchestrates the retrieval phase by:
 * <ul>
 * <li>Generating query embeddings via Azure OpenAI</li>
 * <li>Querying Pinecone for semantic similarity</li>
 * <li>Filtering results by relevance threshold</li>
 * <li>Building context string for LLM</li>
 * <li>Extracting citations for transparency</li>
 * </ul>
 *
 * <p>
 * Only active when chatbot.enabled=true in application.yml.
 *
 * @see RAGQueryService
 * @see AzureOpenAIService
 * @see PineconeService
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chatbot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RAGQueryServiceImpl implements RAGQueryService {

    private final AzureOpenAIService azureOpenAIService;
    private final PineconeService pineconeService;
    private final ChatbotProperties chatbotProperties;

    @Override
    public RetrievalResult retrieveRelevantContext(
            String queryText, Long companyId, Long userId, Map<String, String> additionalFilters) {

        if (queryText == null || queryText.trim().isEmpty()) {
            throw new IllegalArgumentException("Query text cannot be null or empty");
        }

        log.info("Retrieving relevant context: companyId={}, query={}", companyId,
                queryText.substring(0, Math.min(50, queryText.length())));

        try {
            // Step 1: Generate query embedding
            List<Float> queryEmbedding = azureOpenAIService.generateEmbedding(queryText);
            log.debug("Generated query embedding: dimensions={}", queryEmbedding.size());

            // Step 2: Query Pinecone with filters
            int topK = chatbotProperties.getPinecone().getTopK();
            QueryResponseWithUnsignedIndices response = pineconeService.query(queryEmbedding, companyId, topK,
                    additionalFilters);

            // Step 3: Filter by score threshold
            double scoreThreshold = chatbotProperties.getPinecone().getScoreThreshold();
            List<ScoredVectorWithUnsignedIndices> filteredMatches = response.getMatchesList().stream()
                    .filter(match -> match.getScore() >= scoreThreshold)
                    .collect(Collectors.toList());

            log.info(
                    "Pinecone retrieval: totalMatches={}, filteredMatches={}, threshold={}",
                    response.getMatchesList().size(),
                    filteredMatches.size(),
                    scoreThreshold);

            if (filteredMatches.isEmpty()) {
                log.warn("No relevant matches found for query: companyId={}", companyId);
                return new RetrievalResult("", List.of(), 0.0, 0);
            }

            // Step 4: Build context string and deduplicated citations
            // Use LinkedHashMap to deduplicate by voucher_id while preserving order
            // (highest score first)
            Map<String, Citation> citationMap = new LinkedHashMap<>();
            Map<String, VoucherContext> contextMap = new LinkedHashMap<>();
            double totalScore = 0.0;

            for (ScoredVectorWithUnsignedIndices match : filteredMatches) {
                String vectorId = match.getId();
                double score = match.getScore();
                Struct metadata = match.getMetadata();

                totalScore += score;

                // Extract metadata - prefer voucher_id from metadata, fallback to parsing
                // vectorId
                String voucherId = extractMetadataString(metadata, "voucher_id", null);
                if (voucherId == null || voucherId.isEmpty()) {
                    voucherId = vectorId.replace("voucher_", "").split("-")[0]; // Handle chunked IDs like
                                                                                // "voucher_uuid-chunk-1"
                    // If still contains the full UUID, use it
                    if (vectorId.startsWith("voucher_")) {
                        voucherId = vectorId.substring(8); // Remove "voucher_" prefix
                        // Remove any chunk suffix if present (e.g., "-0", "-1")
                        int dashIndex = voucherId.lastIndexOf('-');
                        if (dashIndex > 30) { // UUID is 36 chars, so if dash is after that, it's likely a chunk suffix
                            voucherId = voucherId.substring(0, dashIndex);
                        }
                    }
                }

                String voucherNumber = extractMetadataString(metadata, "voucher_number", "N/A");
                String voucherDate = extractMetadataString(metadata, "voucher_date", "");
                String description = extractMetadataString(metadata, "description", "");
                String totalDebit = extractMetadataString(metadata, "total_debit", "0");
                String totalCredit = extractMetadataString(metadata, "total_credit", "0");

                // Only add if not already seen (first occurrence has highest score due to
                // Pinecone ordering)
                if (!citationMap.containsKey(voucherId)) {
                    String link = "/accounting/vouchers/" + voucherId;
                    String excerpt = description.length() > 100 ? description.substring(0, 100) + "..." : description;
                    citationMap.put(voucherId, new Citation("voucher", voucherId, voucherNumber, excerpt, score, link));
                    contextMap.put(voucherId, new VoucherContext(voucherNumber, voucherDate, description, totalDebit,
                            totalCredit, score));
                }
            }

            // Build context string from deduplicated vouchers
            StringBuilder contextBuilder = new StringBuilder();
            int index = 1;
            for (VoucherContext ctx : contextMap.values()) {
                contextBuilder
                        .append(String.format(
                                "[%d] Chứng từ: %s | Ngày: %s | Nợ: %s | Có: %s\n",
                                index++, ctx.voucherNumber, ctx.voucherDate, ctx.totalDebit, ctx.totalCredit))
                        .append(String.format("Diễn giải: %s\n", ctx.description))
                        .append(String.format("Độ liên quan: %.2f\n\n", ctx.score));
            }

            List<Citation> citations = new ArrayList<>(citationMap.values());
            double averageScore = totalScore / filteredMatches.size();

            log.info(
                    "Context built: uniqueCitations={}, totalMatches={}, averageScore={:.3f}",
                    citations.size(),
                    filteredMatches.size(),
                    averageScore);

            return new RetrievalResult(contextBuilder.toString(), citations, averageScore, filteredMatches.size());

        } catch (Exception e) {
            log.error("Failed to retrieve relevant context: companyId={}, error={}", companyId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve relevant context for query", e);
        }
    }

    /**
     * Extract string value from Pinecone metadata Struct.
     *
     * @param metadata     the metadata Struct
     * @param key          the metadata key
     * @param defaultValue default value if key not found
     * @return extracted string value or default
     */
    private String extractMetadataString(Struct metadata, String key, String defaultValue) {
        if (metadata == null || !metadata.containsFields(key)) {
            return defaultValue;
        }
        try {
            return metadata.getFieldsOrDefault(key, null).getStringValue();
        } catch (Exception e) {
            log.warn("Failed to extract metadata key '{}': {}", key, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Helper record to hold voucher context for building LLM prompt.
     */
    private record VoucherContext(
            String voucherNumber,
            String voucherDate,
            String description,
            String totalDebit,
            String totalCredit,
            double score) {
    }
}

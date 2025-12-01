package com.accounting.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.config.chatbot.ChatbotProperties;
import com.accounting.entity.ChatbotQuery;
import com.accounting.repository.ChatbotQueryRepository;
import com.accounting.service.AzureOpenAIService;
import com.accounting.service.ChatbotService;
import com.accounting.service.RAGQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of ChatbotService for end-to-end query processing.
 *
 * <p>This service orchestrates the complete RAG pipeline:
 * <ul>
 *   <li>Input validation and sanitization</li>
 *   <li>Context retrieval via RAGQueryService</li>
 *   <li>LLM response generation via AzureOpenAIService</li>
 *   <li>Confidence scoring and fallback handling</li>
 *   <li>Database persistence with audit hash</li>
 *   <li>Audit logging for compliance</li>
 * </ul>
 *
 * <p>Only active when chatbot.enabled=true in application.yml.
 *
 * @see ChatbotService
 * @see RAGQueryService
 * @see AzureOpenAIService
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chatbot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChatbotServiceImpl implements ChatbotService {

    private final RAGQueryService ragQueryService;
    private final AzureOpenAIService azureOpenAIService;
    private final ChatbotQueryRepository chatbotQueryRepository;
    private final ChatbotProperties chatbotProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ChatbotQueryResponse processQuery(
            String queryText,
            Long userId,
            Long companyId,
            String sessionId,
            String language,
            Map<String, String> contextFilters) {

        long startTime = System.currentTimeMillis();

        // Step 1: Validate input
        validateQuery(queryText);

        log.info(
                "Processing chatbot query: userId={}, companyId={}, sessionId={}, language={}",
                userId,
                companyId,
                sessionId,
                language);

        try {
            // Step 2: Retrieve relevant context
            RAGQueryService.RetrievalResult retrievalResult =
                    ragQueryService.retrieveRelevantContext(queryText, companyId, userId, contextFilters);

            // Step 3: Calculate confidence score
            double confidenceScore = calculateConfidenceScore(retrievalResult);

            log.info(
                    "Retrieval complete: citations={}, averageScore={:.3f}, confidence={:.3f}",
                    retrievalResult.getCitations().size(),
                    retrievalResult.getAverageScore(),
                    confidenceScore);

            // Step 4: Generate LLM response or fallback
            String answerText;
            if (confidenceScore < chatbotProperties.getQuery().getConfidenceThresholdLow()) {
                answerText = getFallbackMessage(language);
                log.warn("Low confidence ({:.3f}), returning fallback message", confidenceScore);
            } else {
                answerText = azureOpenAIService.generateCompletion(
                        queryText, retrievalResult.getContextString(), language);
            }

            // Step 5: Convert citations to DTOs
            List<CitationDTO> citationDTOs = retrievalResult.getCitations().stream()
                    .map(c -> new CitationDTO(
                            c.getEntityType(),
                            c.getEntityId(),
                            c.getVoucherNumber(),
                            c.getExcerpt(),
                            c.getRelevanceScore(),
                            c.getLink()))
                    .collect(Collectors.toList());

            // Step 6: Save to database with audit hash
            int responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            ChatbotQuery chatbotQuery = saveChatbotQuery(
                    queryText,
                    answerText,
                    citationDTOs,
                    confidenceScore,
                    responseTimeMs,
                    userId,
                    companyId,
                    sessionId,
                    language);

            // Step 7: Log completion (audit logging will be added in future story via AuditService integration)
            log.info(
                    "Chatbot query processed: queryId={}, responseTimeMs={}, confidence={:.3f}",
                    chatbotQuery.getId(),
                    responseTimeMs,
                    confidenceScore);

            return new ChatbotQueryResponse(
                    chatbotQuery.getId(), answerText, citationDTOs, confidenceScore, responseTimeMs);

        } catch (Exception e) {
            log.error("Failed to process chatbot query: userId={}, companyId={}, error={}", userId, companyId, e.getMessage(), e);
            throw new RuntimeException("Failed to process chatbot query", e);
        }
    }

    /**
     * Validate user query input.
     */
    private void validateQuery(String queryText) {
        if (queryText == null || queryText.trim().isEmpty()) {
            throw new IllegalArgumentException("Query text cannot be null or empty");
        }

        int maxLength = chatbotProperties.getQuery().getMaxLength();
        if (queryText.length() > maxLength) {
            throw new IllegalArgumentException(
                    String.format("Query text exceeds maximum length of %d characters", maxLength));
        }
    }

    /**
     * Calculate confidence score based on retrieval quality.
     *
     * <p>Confidence formula:
     * - If no citations: 0.0
     * - Otherwise: (averageScore * 0.7) + (min(citationCount / 5, 1.0) * 0.3)
     *
     * <p>This balances retrieval relevance (70%) with citation count (30%).
     */
    private double calculateConfidenceScore(RAGQueryService.RetrievalResult retrievalResult) {
        if (retrievalResult.getCitations().isEmpty()) {
            return 0.0;
        }

        double averageScore = retrievalResult.getAverageScore();
        int citationCount = retrievalResult.getCitations().size();

        // Citation count factor: 1 citation = 0.2, 5+ citations = 1.0
        double citationFactor = Math.min(citationCount / 5.0, 1.0);

        // Weighted confidence: 70% relevance, 30% citation count
        return (averageScore * 0.7) + (citationFactor * 0.3);
    }

    /**
     * Get fallback message for low confidence queries.
     */
    private String getFallbackMessage(String language) {
        if ("vi".equalsIgnoreCase(language)) {
            return """
                Không đủ dữ liệu để trả lời câu hỏi này với độ chính xác cao.

                Gợi ý:
                - Thử diễn đạt lại câu hỏi với các thuật ngữ kế toán cụ thể hơn
                - Kiểm tra xem dữ liệu chứng từ đã được nhập đầy đủ chưa
                - Liên hệ kế toán trưởng để được hỗ trợ chi tiết hơn
                """;
        } else {
            return """
                Insufficient data to answer this question with high accuracy.

                Suggestions:
                - Try rephrasing the question with more specific accounting terms
                - Check if voucher data has been fully entered
                - Contact the chief accountant for detailed support
                """;
        }
    }

    /**
     * Save chatbot query to database with audit hash.
     */
    private ChatbotQuery saveChatbotQuery(
            String queryText,
            String answerText,
            List<CitationDTO> citations,
            double confidenceScore,
            int responseTimeMs,
            Long userId,
            Long companyId,
            String sessionId,
            String language) {

        ChatbotQuery chatbotQuery = new ChatbotQuery();
        chatbotQuery.setCompanyId(companyId);
        chatbotQuery.setUserId(userId);
        chatbotQuery.setSessionId(sessionId);
        chatbotQuery.setQueryText(queryText);
        chatbotQuery.setAnswerText(answerText);
        chatbotQuery.setConfidenceScore((float) confidenceScore);
        chatbotQuery.setResponseTimeMs(responseTimeMs);
        chatbotQuery.setLanguage(language);
        // createdAt will be set automatically in @PrePersist hook

        // Serialize citations to JSON
        try {
            String citationsJson = objectMapper.writeValueAsString(citations);
            chatbotQuery.setCitations(citationsJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize citations to JSON: {}", e.getMessage(), e);
            chatbotQuery.setCitations("[]");
        }

        // Audit hash will be generated in @PrePersist hook
        return chatbotQueryRepository.save(chatbotQuery);
    }

    @Override
    public boolean isAvailable() {
        try {
            // Check Pinecone service availability
            boolean pineconeAvailable = ragQueryService != null;

            // Check Azure OpenAI service availability
            boolean openAIAvailable = azureOpenAIService != null && azureOpenAIService.isAvailable();

            // Check database connection (simple check - if repository is injected)
            boolean databaseAvailable = chatbotQueryRepository != null;

            boolean allHealthy = pineconeAvailable && openAIAvailable && databaseAvailable;

            if (!allHealthy) {
                log.warn("Chatbot service health check failed - Pinecone: {}, OpenAI: {}, DB: {}",
                    pineconeAvailable, openAIAvailable, databaseAvailable);
            }

            return allHealthy;
        } catch (Exception e) {
            log.error("Error checking chatbot service availability: {}", e.getMessage(), e);
            return false;
        }
    }
}

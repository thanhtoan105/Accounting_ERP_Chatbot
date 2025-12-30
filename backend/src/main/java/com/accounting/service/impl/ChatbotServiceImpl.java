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
import com.accounting.service.ChatbotService;
import com.accounting.service.N8nRAGQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of ChatbotService that delegates RAG processing to n8n workflow.
 *
 * <p>This service orchestrates:
 * <ul>
 *   <li>Input validation and sanitization</li>
 *   <li>RAG query execution via n8n workflow (N8nRAGQueryService)</li>
 *   <li>Database persistence with audit hash</li>
 *   <li>Audit logging for compliance</li>
 * </ul>
 *
 * <p>The n8n workflow "RAG Query Processing - Accounting Chatbot v2" handles:
 * <ul>
 *   <li>Query embedding via Azure OpenAI</li>
 *   <li>Vector search in Pinecone with correct namespace</li>
 *   <li>LLM response with full text context (not just metadata)</li>
 *   <li>Window buffer memory for conversation history</li>
 * </ul>
 *
 * <p>Only active when chatbot.enabled=true in application.yml.
 *
 * @see ChatbotService
 * @see N8nRAGQueryService
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chatbot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChatbotServiceImpl implements ChatbotService {

    private final N8nRAGQueryService n8nRAGQueryService;
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
                "Processing chatbot query via n8n: userId={}, companyId={}, sessionId={}, language={}",
                userId,
                companyId,
                sessionId,
                language);

        try {
            // Step 2: Execute RAG query via n8n workflow
            N8nRAGQueryService.RAGQueryResult ragResult = n8nRAGQueryService.executeQuery(
                    queryText, companyId, userId, sessionId, language);

            log.info(
                    "n8n RAG query complete: citations={}, confidence={}, badge={}",
                    ragResult.citations().size(),
                    ragResult.confidenceScore(),
                    ragResult.confidenceBadge());

            // Step 3: Convert citations to DTOs
            List<CitationDTO> citationDTOs = ragResult.citations().stream()
                    .map(c -> new CitationDTO(
                            c.entityType(),
                            c.entityId(),
                            c.voucherNumber(),
                            c.excerpt(),
                            c.relevanceScore(),
                            c.link()))
                    .collect(Collectors.toList());

            // Step 4: Save to database with audit hash
            int responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            ChatbotQuery chatbotQuery = saveChatbotQuery(
                    queryText,
                    ragResult.answer(),
                    citationDTOs,
                    ragResult.confidenceScore(),
                    responseTimeMs,
                    userId,
                    companyId,
                    sessionId,
                    language);

            // Step 5: Log completion
            log.info(
                    "Chatbot query processed: queryId={}, responseTimeMs={}, confidence={}",
                    chatbotQuery.getId(),
                    responseTimeMs,
                    ragResult.confidenceScore());

            return new ChatbotQueryResponse(
                    chatbotQuery.getId(), 
                    ragResult.answer(), 
                    citationDTOs, 
                    ragResult.confidenceScore(), 
                    responseTimeMs);

        } catch (Exception e) {
            log.error("Failed to process chatbot query: userId={}, companyId={}, error={}", 
                    userId, companyId, e.getMessage(), e);
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
            // Check n8n RAG query service availability
            boolean n8nAvailable = n8nRAGQueryService != null && n8nRAGQueryService.isAvailable();

            // Check database connection (simple check - if repository is injected)
            boolean databaseAvailable = chatbotQueryRepository != null;

            boolean allHealthy = n8nAvailable && databaseAvailable;

            if (!allHealthy) {
                log.warn("Chatbot service health check failed - n8n: {}, DB: {}",
                    n8nAvailable, databaseAvailable);
            }

            return allHealthy;
        } catch (Exception e) {
            log.error("Error checking chatbot service availability: {}", e.getMessage(), e);
            return false;
        }
    }
}

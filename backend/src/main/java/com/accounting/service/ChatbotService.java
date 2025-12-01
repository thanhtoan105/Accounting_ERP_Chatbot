package com.accounting.service;

import java.util.List;
import java.util.Map;

/**
 * Service interface for end-to-end chatbot query processing.
 *
 * <p>This service orchestrates the complete RAG pipeline:
 * <ul>
 *   <li>Validate and sanitize user input</li>
 *   <li>Retrieve relevant context via RAGQueryService</li>
 *   <li>Generate LLM response via AzureOpenAIService</li>
 *   <li>Calculate confidence score</li>
 *   <li>Save query to database with audit hash</li>
 *   <li>Log audit event via AuditService</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * ChatbotQueryResponse response = chatbotService.processQuery(
 *     "Tình hình công nợ hiện tại ra sao?",
 *     userId,
 *     companyId,
 *     sessionId,
 *     "vi",
 *     null
 * );
 *
 * String answer = response.getAnswerText();
 * List<CitationDTO> citations = response.getCitations();
 * double confidence = response.getConfidenceScore();
 * }</pre>
 *
 * @see RAGQueryService
 * @see AzureOpenAIService
 * @see com.accounting.repository.ChatbotQueryRepository
 */
public interface ChatbotService {

    /**
     * Process a user query end-to-end with RAG pipeline.
     *
     * <p>Processing flow:
     * <ol>
     *   <li>Validate query (non-empty, max length check)</li>
     *   <li>Retrieve relevant context from Pinecone + database</li>
     *   <li>Generate LLM response with context</li>
     *   <li>Calculate confidence score based on retrieval quality</li>
     *   <li>Save query to database with audit hash</li>
     *   <li>Log audit event</li>
     *   <li>Return response with answer + citations</li>
     * </ol>
     *
     * <p>If confidence < 0.5, returns fallback message:
     * "Không đủ dữ liệu để trả lời câu hỏi này. Vui lòng kiểm tra lại hoặc liên hệ kế toán trưởng."
     *
     * @param queryText the user's question in natural language
     * @param userId the user ID making the query
     * @param companyId the company ID for data scoping
     * @param sessionId conversation session ID (UUID string)
     * @param language response language ("vi" or "en")
     * @param contextFilters optional metadata filters (e.g., period_id)
     * @return ChatbotQueryResponse with answer, citations, confidence, and metadata
     * @throws IllegalArgumentException if queryText exceeds max length or is invalid
     * @throws RuntimeException if processing fails
     */
    ChatbotQueryResponse processQuery(
            String queryText,
            Long userId,
            Long companyId,
            String sessionId,
            String language,
            Map<String, String> contextFilters);

    /**
     * Response object containing chatbot answer and metadata.
     */
    class ChatbotQueryResponse {
        private final Long queryId;
        private final String answerText;
        private final List<CitationDTO> citations;
        private final double confidenceScore;
        private final int responseTimeMs;

        public ChatbotQueryResponse(
                Long queryId, String answerText, List<CitationDTO> citations, double confidenceScore, int responseTimeMs) {
            this.queryId = queryId;
            this.answerText = answerText;
            this.citations = citations;
            this.confidenceScore = confidenceScore;
            this.responseTimeMs = responseTimeMs;
        }

        public Long getQueryId() {
            return queryId;
        }

        public String getAnswerText() {
            return answerText;
        }

        public List<CitationDTO> getCitations() {
            return citations;
        }

        public double getConfidenceScore() {
            return confidenceScore;
        }

        public int getResponseTimeMs() {
            return responseTimeMs;
        }
    }

    /**
     * Citation DTO for frontend rendering.
     */
    class CitationDTO {
        private final String entityType;
        private final String entityId;
        private final String voucherNumber;
        private final String excerpt;
        private final double relevanceScore;
        private final String link;

        public CitationDTO(
                String entityType,
                String entityId,
                String voucherNumber,
                String excerpt,
                double relevanceScore,
                String link) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.voucherNumber = voucherNumber;
            this.excerpt = excerpt;
            this.relevanceScore = relevanceScore;
            this.link = link;
        }

        public String getEntityType() {
            return entityType;
        }

        public String getEntityId() {
            return entityId;
        }

        public String getVoucherNumber() {
            return voucherNumber;
        }

        public String getExcerpt() {
            return excerpt;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }

        public String getLink() {
            return link;
        }
    }

    /**
     * Check if chatbot service and its dependencies are available.
     *
     * <p>This method checks the health status of:
     * <ul>
     *   <li>Pinecone service (vector database)</li>
     *   <li>Azure OpenAI service (embeddings and completions)</li>
     *   <li>Database connection</li>
     * </ul>
     *
     * @return true if all dependencies are healthy, false otherwise
     */
    boolean isAvailable();
}

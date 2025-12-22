package com.accounting.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.accounting.config.chatbot.ChatbotProperties;
import com.accounting.service.N8nRAGQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Implementation of N8nRAGQueryService that delegates RAG processing to n8n workflow.
 *
 * <p>This service calls the "RAG Query Processing - Accounting Chatbot v2" n8n workflow
 * which handles the complete RAG pipeline:
 * <ul>
 *   <li>Query validation and parameter extraction</li>
 *   <li>Query embedding via Azure OpenAI</li>
 *   <li>Vector search in Pinecone with correct company namespace</li>
 *   <li>LLM response generation with full text context</li>
 *   <li>Response formatting with citations</li>
 * </ul>
 *
 * <p>This approach is preferred over Java-native RAG because:
 * <ul>
 *   <li>n8n retrieves the full embedded text (not just metadata)</li>
 *   <li>Consistent namespace handling with embedding workflow</li>
 *   <li>Window buffer memory for conversation history</li>
 *   <li>Easier to debug and modify without code deployment</li>
 * </ul>
 *
 * @see N8nRAGQueryService
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "chatbot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class N8nRAGQueryServiceImpl implements N8nRAGQueryService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ChatbotProperties chatbotProperties;

    public N8nRAGQueryServiceImpl(
            ChatbotProperties chatbotProperties,
            ObjectMapper objectMapper) {
        this.chatbotProperties = chatbotProperties;
        this.objectMapper = objectMapper;

        int timeout = chatbotProperties.getN8n().getTimeoutSeconds();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout * 2, TimeUnit.SECONDS) // Allow more time for LLM response
            .build();
    }

    @Override
    public RAGQueryResult executeQuery(String query, Long companyId, Long userId, String sessionId, String language) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }

        String requestId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        log.info("Executing RAG query via n8n: requestId={}, companyId={}, sessionId={}", 
            requestId, companyId, sessionId);

        try {
            // Build request payload matching n8n webhook expected format
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("query", query);
            payload.put("company_id", companyId.toString());
            payload.put("user_id", userId != null ? userId.toString() : "");
            payload.put("session_id", sessionId != null ? sessionId : String.valueOf(System.currentTimeMillis()));
            payload.put("language", language != null ? language : "vi");

            String jsonPayload = objectMapper.writeValueAsString(payload);

            RequestBody body = RequestBody.create(jsonPayload, JSON);
            Request request = new Request.Builder()
                .url(chatbotProperties.getN8n().getRagQueryUrl())
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Request-ID", requestId)
                .build();

            String webhookSecret = chatbotProperties.getN8n().getWebhookSecret();
            if (webhookSecret != null && !webhookSecret.isEmpty()) {
                request = request.newBuilder()
                    .addHeader("X-Webhook-Secret", webhookSecret)
                    .build();
            }

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "empty";
                    log.error("n8n RAG query failed: requestId={}, status={}, body={}", 
                        requestId, response.code(), responseBody);
                    throw new RuntimeException("n8n RAG query failed with status " + response.code());
                }

                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonNode responseJson = objectMapper.readTree(responseBody);

                // Parse n8n workflow response
                RAGQueryResult result = parseResponse(responseJson, startTime);

                log.info("RAG query completed: requestId={}, confidence={}, citations={}, responseTimeMs={}",
                    requestId, result.confidenceScore(), result.citations().size(), result.responseTimeMs());

                return result;
            }

        } catch (IOException e) {
            log.error("Failed to execute RAG query via n8n: requestId={}, error={}", requestId, e.getMessage(), e);
            throw new RuntimeException("Failed to execute RAG query via n8n", e);
        }
    }

    @Override
    public boolean isAvailable() {
        if (!chatbotProperties.isEnabled()) {
            return false;
        }

        String ragQueryUrl = chatbotProperties.getN8n().getRagQueryUrl();
        if (ragQueryUrl == null || ragQueryUrl.isEmpty()) {
            log.warn("n8n RAG query URL not configured");
            return false;
        }

        try {
            Request request = new Request.Builder()
                .url(ragQueryUrl)
                .head()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                // 404/405 means endpoint exists but doesn't support HEAD - still available
                return response.isSuccessful() || response.code() == 404 || response.code() == 405;
            }
        } catch (IOException e) {
            log.debug("n8n RAG query health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parse n8n workflow response to RAGQueryResult.
     *
     * Expected response format from n8n "Format Response" node:
     * {
     *   "answer": "...",
     *   "citations": [...],
     *   "confidenceScore": 0.85,
     *   "confidenceBadge": "HIGH",
     *   "queryId": "q_123_456",
     *   "sessionId": "123",
     *   "responseTimeMs": 1500,
     *   "language": "vi",
     *   "suggestions": [...]
     * }
     */
    private RAGQueryResult parseResponse(JsonNode json, long startTime) {
        String answer = json.path("answer").asText("Không thể xử lý câu hỏi. Vui lòng thử lại.");
        double confidenceScore = json.path("confidenceScore").asDouble(0.5);
        String confidenceBadge = json.path("confidenceBadge").asText("MEDIUM");
        String queryId = json.path("queryId").asText(UUID.randomUUID().toString());
        String sessionId = json.path("sessionId").asText("");
        int responseTimeMs = json.path("responseTimeMs").asInt((int)(System.currentTimeMillis() - startTime));
        String language = json.path("language").asText("vi");

        // Parse citations
        List<Citation> citations = new ArrayList<>();
        JsonNode citationsNode = json.path("citations");
        if (citationsNode.isArray()) {
            for (JsonNode citationNode : citationsNode) {
                citations.add(new Citation(
                    citationNode.path("entityType").asText("VOUCHER"),
                    citationNode.path("entityId").asText(""),
                    citationNode.path("voucherNumber").asText(""),
                    citationNode.path("excerpt").asText(""),
                    citationNode.path("relevanceScore").asDouble(0.0),
                    citationNode.path("link").asText("")
                ));
            }
        }

        // Parse suggestions
        List<String> suggestions = new ArrayList<>();
        JsonNode suggestionsNode = json.path("suggestions");
        if (suggestionsNode.isArray()) {
            for (JsonNode suggestionNode : suggestionsNode) {
                suggestions.add(suggestionNode.asText());
            }
        }

        return new RAGQueryResult(
            answer,
            citations,
            confidenceScore,
            confidenceBadge,
            queryId,
            sessionId,
            responseTimeMs,
            language,
            suggestions
        );
    }
}

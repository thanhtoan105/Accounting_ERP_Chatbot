package com.accounting.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.accounting.config.chatbot.ChatbotProperties;
import com.accounting.dto.ChatbotQueryRequest;
import com.accounting.dto.ChatbotQueryResponse;
import com.accounting.dto.Citation;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ChatbotService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for chatbot query processing.
 *
 * Provides endpoints for AI-powered natural language queries about voucher
 * transactions.
 * Supports Vietnamese and English queries with RAG (Retrieval-Augmented
 * Generation).
 *
 * Security:
 * - All endpoints require JWT authentication
 * - Company context automatically extracted from JWT token
 * - User context automatically extracted from JWT token
 *
 * Feature Flag:
 * - Controller only active when chatbot.enabled=true in application.yml
 * - Returns 503 Service Unavailable when disabled
 *
 * @see ChatbotService
 * @see ChatbotQueryRequest
 * @see ChatbotQueryResponse
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Chatbot", description = "AI-powered chatbot for voucher queries with RAG")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    prefix = "chatbot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final ChatbotProperties chatbotProperties;

    /**
     * Process a natural language query about voucher transactions.
     *
     * This endpoint:
     * 1. Validates the query request (length, format)
     * 2. Extracts user and company context from JWT token
     * 3. Performs hybrid retrieval (semantic + metadata filters) over Pinecone
     * 4. Generates Vietnamese/English answer with citations
     * 5. Logs query to database for audit trail
     *
     * Example Vietnamese queries:
     * - "Công nợ phải trả là bao nhiêu?" (How much is payable?)
     * - "Có phiếu chi nào trong tháng 11?" (Any payment vouchers in Nov?)
     * - "Tổng số tiền đã thanh toán cho nhà cung cấp ABC?" (Total paid to supplier
     * ABC?)
     *
     * @param request The chatbot query request (query text, session ID, language,
     *                filters)
     * @return ChatbotQueryResponse with answer, citations, confidence score
     * @throws IllegalArgumentException if query is invalid (empty, too long)
     * @throws RuntimeException         if Pinecone or OpenAI service fails
     */
    @PostMapping("/query")
    @Operation(summary = "Submit a chatbot query", description = "Process a natural language question about voucher transactions with RAG retrieval")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Query processed successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatbotQueryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (empty query, exceeds max length, invalid filters)", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized (missing or invalid JWT token)", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Forbidden (insufficient permissions)", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "429", description = "Too many requests (rate limit exceeded)", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal server error (service failure, database error)", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Service unavailable (Pinecone or OpenAI service down, or chatbot disabled)", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ChatbotQueryResponse> query(
            @Parameter(description = "Chatbot query request with query text and optional filters", required = true) @Valid @RequestBody ChatbotQueryRequest request) {
        if (!chatbotProperties.isEnabled()) {
            log.warn("Chatbot query rejected - feature is disabled");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        log.info("Received chatbot query request - sessionId: {}, language: {}",
                request.getSessionId(), request.getLanguage());

        try {
            // Extract authentication context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long userId = extractUserId(authentication);
            Long companyId = extractCompanyId(authentication);

            log.debug("Processing query for userId: {}, companyId: {}", userId, companyId);

            // Process query via ChatbotService (returns service layer response)
            ChatbotService.ChatbotQueryResponse serviceResponse = chatbotService.processQuery(
                    request.getQuery(),
                    userId,
                    companyId,
                    request.getSessionId(),
                    request.getLanguage(),
                    request.getContextFilters());

            // Convert service layer response to API DTO
            ChatbotQueryResponse apiResponse = convertToApiResponse(serviceResponse);

            log.info("Query processed successfully - queryId: {}, confidence: {}, responseTimeMs: {}",
                    apiResponse.getQueryId(), apiResponse.getConfidenceScore(), apiResponse.getResponseTimeMs());

            return ResponseEntity.ok(apiResponse);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid chatbot query request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error processing chatbot query: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint for chatbot service.
     *
     * Returns service status and availability of external dependencies (Pinecone,
     * OpenAI).
     *
     * @return Service status with dependency checks
     */
    @GetMapping("/health")
    @Operation(summary = "Chatbot health check", description = "Check chatbot service status and external dependency availability")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Service healthy", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Service unavailable (dependencies down)", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("Chatbot health check requested");

        if (!chatbotProperties.isEnabled()) {
            Map<String, Object> health = Map.of(
                    "status", "DISABLED",
                    "service", "chatbot",
                    "message", "Chatbot feature is disabled");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }

        try {
            boolean isHealthy = chatbotService.isAvailable();

            Map<String, Object> health = Map.of(
                    "status", isHealthy ? "UP" : "DOWN",
                    "service", "chatbot",
                    "message", isHealthy ? "Chatbot service is operational" : "Chatbot service is unavailable");

            return isHealthy
                    ? ResponseEntity.ok(health)
                    : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);

        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage(), e);
            Map<String, Object> health = Map.of(
                    "status", "DOWN",
                    "service", "chatbot",
                    "message", "Health check failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    /**
     * Extract user ID from authentication token.
     * Uses SecurityUtils for consistent extraction across the application.
     *
     * @param authentication Spring Security authentication object (unused, kept for
     *                       API compatibility)
     * @return User ID as Long
     * @throws ResponseStatusException if user ID cannot be extracted (401
     *                                 Unauthorized)
     */
    private Long extractUserId(Authentication authentication) {
        return SecurityUtils.getCurrentUserId();
    }

    /**
     * Extract company ID from CompanyContext ThreadLocal.
     * Uses CompanyContext which is populated by CompanyContextFilter from
     * JWT/headers.
     *
     * @param authentication Spring Security authentication object (unused, kept for
     *                       API compatibility)
     * @return Company ID as Long
     * @throws IllegalStateException if company ID cannot be extracted
     */
    private Long extractCompanyId(Authentication authentication) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException(
                    "Company context is missing. Ensure X-Company-Id header is set or JWT contains company claim.");
        }
        return companyId;
    }

    /**
     * Convert service layer response to API DTO.
     *
     * Maps from ChatbotService.ChatbotQueryResponse to ChatbotQueryResponse DTO
     * for API layer serialization.
     *
     * @param serviceResponse Service layer response from ChatbotService
     * @return API layer ChatbotQueryResponse DTO
     */
    private ChatbotQueryResponse convertToApiResponse(ChatbotService.ChatbotQueryResponse serviceResponse) {
        // Convert citations from service DTOs to API DTOs
        List<Citation> apiCitations = serviceResponse.getCitations().stream()
                .map(this::convertCitation)
                .collect(Collectors.toList());

        // Calculate confidence level for UI display
        float confidenceScore = (float) serviceResponse.getConfidenceScore();
        String confidenceLevel = ChatbotQueryResponse.calculateConfidenceLevel(confidenceScore);

        return ChatbotQueryResponse.builder()
                .queryId(serviceResponse.getQueryId() != null ?
                    String.valueOf(serviceResponse.getQueryId()) : null)
                .answer(serviceResponse.getAnswerText())
                .citations(apiCitations)
                .confidenceScore(confidenceScore)
                .confidenceLevel(confidenceLevel)
                .responseTimeMs(serviceResponse.getResponseTimeMs())
                .build();
    }

    /**
     * Convert service layer citation to API DTO.
     *
     * @param serviceCitation Service layer CitationDTO
     * @return API layer Citation DTO
     */
    private Citation convertCitation(ChatbotService.CitationDTO serviceCitation) {
        return Citation.builder()
                .entityType(serviceCitation.getEntityType())
                .entityId(serviceCitation.getEntityId())
                .voucherNumber(serviceCitation.getVoucherNumber())
                .excerpt(serviceCitation.getExcerpt())
                .relevanceScore((float) serviceCitation.getRelevanceScore())
                .link(serviceCitation.getLink())
                .build();
    }
}

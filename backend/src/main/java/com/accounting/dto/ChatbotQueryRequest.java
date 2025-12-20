package com.accounting.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for chatbot query submission.
 *
 * Contains the user's natural language query and optional context filters
 * for more targeted retrieval from Pinecone vector database.
 *
 * Example usage:
 * <pre>
 * ChatbotQueryRequest request = ChatbotQueryRequest.builder()
 *     .query("Công nợ phải trả là bao nhiêu?")
 *     .sessionId("session-123456")
 *     .language("vi")
 *     .contextFilters(Map.of("period_id", "202311"))
 *     .build();
 * </pre>
 *
 * Validation rules:
 * - Query text: Required, non-blank, max 5000 characters
 * - Session ID: Required for conversation threading
 * - Language: Must be 'vi' (Vietnamese) or 'en' (English)
 *
 * @see ChatbotQueryResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for chatbot query processing with optional context filters")
public class ChatbotQueryRequest {

    /**
     * Natural language query text in Vietnamese or English.
     *
     * Examples:
     * - "Công nợ phải trả là bao nhiêu?" (Vietnamese)
     * - "What is the total payable amount?" (English)
     * - "Có phiếu chi nào trong tháng 11?" (Vietnamese)
     */
    @NotBlank(message = "Query text is required")
    @Size(min = 1, max = 5000, message = "Query must be between 1 and 5000 characters")
    @Schema(
        description = "Natural language query text in Vietnamese or English",
        example = "Công nợ phải trả là bao nhiêu?",
        required = true,
        minLength = 1,
        maxLength = 5000
    )
    private String query;

    /**
     * Session ID for conversation threading.
     *
     * Used to group related queries together for analytics and context tracking.
     * Client should generate a unique session ID per conversation and reuse it
     * for all queries in that conversation.
     *
     * Format: UUID or client-generated unique string
     */
    @NotBlank(message = "Session ID is required")
    @Size(min = 1, max = 255, message = "Session ID must be between 1 and 255 characters")
    @Schema(
        description = "Session ID for conversation threading (UUID or unique string)",
        example = "550e8400-e29b-41d4-a716-446655440000",
        required = true,
        minLength = 1,
        maxLength = 255
    )
    private String sessionId;

    /**
     * Language code for query and response.
     *
     * Supported languages:
     * - "vi": Vietnamese (default)
     * - "en": English
     *
     * Determines the language of the LLM-generated response.
     */
    @NotNull(message = "Language is required")
    @Pattern(regexp = "^(vi|en)$", message = "Language must be 'vi' (Vietnamese) or 'en' (English)")
    @Builder.Default
    @Schema(
        description = "Language code for query and response",
        example = "vi",
        allowableValues = {"vi", "en"},
        required = true,
        defaultValue = "vi"
    )
    private String language = "vi";

    /**
     * Optional context filters for metadata-based filtering in Pinecone.
     *
     * Allows narrowing retrieval to specific accounting periods, voucher types,
     * or other metadata dimensions stored in Pinecone.
     *
     * Supported filter keys:
     * - "period_id": Filter by accounting period (e.g., "202311")
     * - "voucher_type": Filter by voucher type (e.g., "PC" for payment voucher)
     * - "customer_id": Filter by customer ID (for AR queries)
     * - "supplier_id": Filter by supplier ID (for AP queries)
     *
     * Example:
     * <pre>
     * Map.of(
     *   "period_id", "202311",
     *   "voucher_type", "PC"
     * )
     * </pre>
     */
    @Schema(
        description = "Optional metadata filters for targeted retrieval (period_id, voucher_type, etc.)",
        example = "{\"period_id\": \"202311\", \"voucher_type\": \"PC\"}"
    )
    private Map<String, String> contextFilters;
}

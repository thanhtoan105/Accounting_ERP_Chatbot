package com.accounting.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for chatbot query processing.
 *
 * Contains the AI-generated answer, citations from voucher data,
 * confidence score, and performance metrics.
 *
 * Example response structure:
 * <pre>
 * {
 *   "queryId": "550e8400-e29b-41d4-a716-446655440000",
 *   "answer": "Tổng công nợ phải trả hiện tại là 125,000,000 VND...",
 *   "citations": [
 *     {
 *       "entityType": "voucher",
 *       "entityId": "550e8400-e29b-41d4-a716-446655440001",
 *       "voucherNumber": "PC-2023-001",
 *       "excerpt": "Thanh toán nhà cung cấp ABC - 50,000,000 VND",
 *       "relevanceScore": 0.92,
 *       "link": "/vouchers/550e8400-e29b-41d4-a716-446655440001"
 *     }
 *   ],
 *   "confidenceScore": 0.85,
 *   "confidenceLevel": "HIGH",
 *   "responseTimeMs": 1234
 * }
 * </pre>
 *
 * @see ChatbotQueryRequest
 * @see Citation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from chatbot query with answer, citations, and confidence metrics")
public class ChatbotQueryResponse {

    /**
     * Unique identifier for this query (for tracking and audit).
     */
    @Schema(
        description = "Unique query ID for tracking and audit",
        example = "12345"
    )
    private String queryId;

    /**
     * AI-generated answer in Vietnamese or English.
     *
     * Contains natural language response based on retrieved voucher data.
     * If confidence is low (< 0.5), returns fallback message with suggestions.
     */
    @Schema(
        description = "AI-generated answer in Vietnamese or English based on retrieved voucher data",
        example = "Tổng công nợ phải trả hiện tại là 125,000,000 VND dựa trên 3 phiếu chi chưa thanh toán."
    )
    private String answer;

    /**
     * List of citations from voucher data supporting the answer.
     *
     * Each citation includes voucher details and clickable link to voucher page.
     * Citations are ordered by relevance score (highest first).
     */
    @Schema(
        description = "List of citations from voucher data supporting the answer, ordered by relevance"
    )
    private List<Citation> citations;

    /**
     * Confidence score (0.0 - 1.0) indicating answer quality.
     *
     * Formula: (avgRetrievalScore × 0.7) + (citationCount/5 × 0.3)
     *
     * Interpretation:
     * - 0.8 - 1.0: High confidence (green badge)
     * - 0.5 - 0.8: Medium confidence (yellow badge)
     * - 0.0 - 0.5: Low confidence (red badge, fallback message)
     */
    @Schema(
        description = "Confidence score (0.0-1.0) based on retrieval quality and citation count",
        example = "0.85",
        minimum = "0.0",
        maximum = "1.0"
    )
    private Float confidenceScore;

    /**
     * Confidence level indicator for UI display.
     *
     * Values:
     * - "HIGH": confidence >= 0.8
     * - "MEDIUM": 0.5 <= confidence < 0.8
     * - "LOW": confidence < 0.5
     */
    @Schema(
        description = "Confidence level indicator (HIGH/MEDIUM/LOW) for UI badge display",
        example = "HIGH",
        allowableValues = {"HIGH", "MEDIUM", "LOW"}
    )
    private String confidenceLevel;

    /**
     * Query processing time in milliseconds.
     *
     * Includes:
     * - Embedding generation
     * - Pinecone vector search
     * - LLM completion
     * - Database persistence
     */
    @Schema(
        description = "Total query processing time in milliseconds (embedding + retrieval + LLM + DB)",
        example = "1234"
    )
    private Integer responseTimeMs;

    /**
     * Helper method to calculate confidence level from score.
     *
     * @param score Confidence score (0.0 - 1.0)
     * @return Confidence level string (HIGH/MEDIUM/LOW)
     */
    public static String calculateConfidenceLevel(Float score) {
        if (score == null) {
            return "LOW";
        }
        if (score >= 0.8f) {
            return "HIGH";
        } else if (score >= 0.5f) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}

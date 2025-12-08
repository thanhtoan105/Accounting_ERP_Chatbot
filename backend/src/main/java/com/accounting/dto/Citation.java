package com.accounting.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Citation DTO representing a reference to a voucher or document.
 *
 * Citations provide evidence supporting the chatbot's answer, allowing users
 * to click through to view the original voucher data.
 *
 * Example citation:
 * <pre>
 * Citation citation = Citation.builder()
 *     .entityType("voucher")
 *     .entityId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
 *     .voucherNumber("PC-2023-001")
 *     .excerpt("Thanh toán nhà cung cấp ABC - 50,000,000 VND")
 *     .relevanceScore(0.92)
 *     .link("/vouchers/550e8400-e29b-41d4-a716-446655440001")
 *     .build();
 * </pre>
 *
 * @see ChatbotQueryResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Citation reference to a voucher or document supporting the chatbot answer")
public class Citation {

    /**
     * Type of entity referenced.
     *
     * Supported types:
     * - "voucher": General voucher
     * - "sales_invoice": Sales invoice
     * - "purchase_bill": Purchase bill
     * - "receipt": Customer receipt
     * - "payment": Cash payment
     */
    @Schema(
        description = "Entity type (voucher, sales_invoice, purchase_bill, receipt, payment)",
        example = "voucher",
        allowableValues = {"voucher", "sales_invoice", "purchase_bill", "receipt", "payment"}
    )
    private String entityType;

    /**
     * Unique identifier of the entity (UUID).
     */
    @Schema(
        description = "Unique entity ID (UUID) for the referenced voucher/document",
        example = "550e8400-e29b-41d4-a716-446655440001"
    )
    private UUID entityId;

    /**
     * Voucher number for display (human-readable).
     *
     * Examples:
     * - "PC-2023-001" (payment voucher)
     * - "PT-2023-015" (receipt voucher)
     * - "INV-2023-042" (sales invoice)
     */
    @Schema(
        description = "Human-readable voucher number for display",
        example = "PC-2023-001"
    )
    private String voucherNumber;

    /**
     * Short text excerpt from voucher data.
     *
     * Provides context about why this voucher was retrieved.
     * Typically includes description and amount.
     *
     * Example: "Thanh toán nhà cung cấp ABC - 50,000,000 VND"
     */
    @Schema(
        description = "Short excerpt from voucher data providing context (description + amount)",
        example = "Thanh toán nhà cung cấp ABC - 50,000,000 VND"
    )
    private String excerpt;

    /**
     * Relevance score from Pinecone vector search (0.0 - 1.0).
     *
     * Indicates how well this voucher matches the user's query semantically.
     * Higher scores indicate stronger relevance.
     */
    @Schema(
        description = "Semantic relevance score from Pinecone (0.0-1.0, higher is more relevant)",
        example = "0.92",
        minimum = "0.0",
        maximum = "1.0"
    )
    private Float relevanceScore;

    /**
     * Clickable link to voucher detail page in frontend.
     *
     * Format: /vouchers/{entityId} or /sales-invoices/{entityId}
     * Frontend should navigate to this URL when user clicks citation.
     */
    @Schema(
        description = "Clickable link to voucher detail page (frontend route)",
        example = "/vouchers/550e8400-e29b-41d4-a716-446655440001"
    )
    private String link;
}

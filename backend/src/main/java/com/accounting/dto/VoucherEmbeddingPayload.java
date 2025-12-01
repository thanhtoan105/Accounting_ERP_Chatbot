package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload for n8n webhook trigger containing voucher data for embedding.
 * This DTO is sent to n8n workflow which generates embeddings and stores in Pinecone.
 */
public record VoucherEmbeddingPayload(
    Long companyId,
    String voucherId,
    VoucherHeader header,
    List<VoucherLine> lineItems,
    BalanceSummary summary) {

    public record VoucherHeader(
        String voucherNumber,
        LocalDate voucherDate,
        String description,
        String status) {
    }

    public record VoucherLine(
        String accountCode,
        String accountName,
        BigDecimal debit,
        BigDecimal credit,
        String description) {
    }

    public record BalanceSummary(
        BigDecimal totalDebit,
        BigDecimal totalCredit) {
    }
}

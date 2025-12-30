package com.accounting.service;

import java.util.List;
import java.util.Map;

import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.dto.embedding.EntityEmbeddingPayload;
import com.accounting.dto.embedding.EntityType;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.Supplier;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;

/**
 * Service for synthesizing human-readable text from entities for embedding.
 * Each entity type has a specific text template optimized for Vietnamese RAG queries.
 */
public interface EntityTextSynthesizer {

    /**
     * Synthesize text and build embedding payload for a Voucher.
     *
     * @param voucher The voucher entity
     * @param lines The voucher line items
     * @param action UPSERT or DELETE
     * @return EntityEmbeddingPayload ready for n8n webhook
     */
    EntityEmbeddingPayload buildVoucherPayload(Voucher voucher, List<VoucherLine> lines, EmbeddingAction action);

    /**
     * Synthesize text and build embedding payload for a Customer.
     *
     * @param customer The customer entity
     * @param action UPSERT or DELETE
     * @return EntityEmbeddingPayload ready for n8n webhook
     */
    EntityEmbeddingPayload buildCustomerPayload(Customer customer, EmbeddingAction action);

    /**
     * Synthesize text and build embedding payload for a Supplier.
     *
     * @param supplier The supplier entity
     * @param action UPSERT or DELETE
     * @return EntityEmbeddingPayload ready for n8n webhook
     */
    EntityEmbeddingPayload buildSupplierPayload(Supplier supplier, EmbeddingAction action);

    /**
     * Synthesize text and build embedding payload for a Chart of Account.
     *
     * @param account The chart of account entity
     * @param parentCode The parent account code (if any)
     * @param action UPSERT or DELETE
     * @return EntityEmbeddingPayload ready for n8n webhook
     */
    EntityEmbeddingPayload buildChartOfAccountPayload(ChartOfAccount account, String parentCode, EmbeddingAction action);

    /**
     * Build a generic embedding payload for any entity type.
     * Used for custom entity types or when entity-specific method is not available.
     *
     * @param entityType The type of entity
     * @param entityId The entity ID
     * @param companyId The company ID (null for global entities)
     * @param text Pre-synthesized text for embedding
     * @param metadata Entity-specific metadata
     * @param action UPSERT or DELETE
     * @return EntityEmbeddingPayload ready for n8n webhook
     */
    EntityEmbeddingPayload buildGenericPayload(
        EntityType entityType,
        String entityId,
        Long companyId,
        String text,
        Map<String, Object> metadata,
        EmbeddingAction action
    );

    /**
     * Synthesize text and build embedding payload for a Sales Invoice.
     *
     * @param invoice The sales invoice entity
     * @param lines The invoice line items
     * @param customerName The customer name
     * @param action UPSERT or DELETE
     * @return EntityEmbeddingPayload ready for n8n webhook
     */
    EntityEmbeddingPayload buildSalesInvoicePayload(
        com.accounting.entity.SalesInvoice invoice,
        List<com.accounting.entity.SalesInvoiceLine> lines,
        String customerName,
        EmbeddingAction action
    );

    /**
     * Synthesize text and build embedding payload for a Purchase Bill.
     *
     * @param bill The purchase bill entity
     * @param lines The bill line items
     * @param supplierName The supplier name
     * @param action UPSERT or DELETE
     * @return EntityEmbeddingPayload ready for n8n webhook
     */
    EntityEmbeddingPayload buildPurchaseBillPayload(
        com.accounting.entity.PurchaseBill bill,
        List<com.accounting.entity.PurchaseBillLine> lines,
        String supplierName,
        EmbeddingAction action
    );

    /**
     * Synthesize text and build embedding payload for an AR Payment (Receipt).
     *
     * @param payment The AR payment entity
     * @param allocations The receipt allocations to invoices
     * @param customerName The customer name
     * @param bankAccountName The bank account name (if applicable)
     * @param action UPSERT or DELETE
     * @return EntityEmbeddingPayload ready for n8n webhook
     */
    EntityEmbeddingPayload buildARPaymentPayload(
        com.accounting.entity.ARPayment payment,
        List<com.accounting.entity.ReceiptAllocation> allocations,
        String customerName,
        String bankAccountName,
        EmbeddingAction action
    );

    /**
     * Synthesize text and build embedding payload for an AP Payment.
     *
     * @param payment The AP payment entity
     * @param allocations The payment allocations to bills
     * @param supplierName The supplier name
     * @param bankAccountName The bank account name (if applicable)
     * @param action UPSERT or DELETE
     * @return EntityEmbeddingPayload ready for n8n webhook
     */
    EntityEmbeddingPayload buildAPPaymentPayload(
        com.accounting.entity.APPayment payment,
        List<com.accounting.entity.PaymentAllocation> allocations,
        String supplierName,
        String bankAccountName,
        EmbeddingAction action
    );
}

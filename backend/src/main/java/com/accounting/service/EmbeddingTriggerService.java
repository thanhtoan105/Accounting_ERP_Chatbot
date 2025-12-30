package com.accounting.service;

import java.util.List;

import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.Supplier;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;

/**
 * Service for triggering entity embeddings for RAG chatbot.
 * Encapsulates the logic for building embedding payloads and sending to n8n webhook.
 *
 * <p>All methods are fire-and-forget and non-blocking:
 * - Entity operations (create/update/delete) should NOT fail if embedding fails
 * - Errors are logged but not propagated
 * - Actual webhook call is async with retry logic in N8nWebhookService
 *
 * <p>Usage pattern in entity services:
 * <pre>{@code
 * Customer saved = customerRepository.save(customer);
 * embeddingTriggerService.triggerCustomerEmbedding(saved, EmbeddingAction.UPSERT);
 * }</pre>
 */
public interface EmbeddingTriggerService {

    /**
     * Trigger embedding for a Voucher entity.
     *
     * @param voucher The voucher entity (must be persisted with ID)
     * @param lines   The voucher line items
     * @param action  UPSERT for create/update, DELETE for removal
     */
    void triggerVoucherEmbedding(Voucher voucher, List<VoucherLine> lines, EmbeddingAction action);

    /**
     * Trigger embedding for a Customer entity.
     *
     * @param customer The customer entity (must be persisted with ID)
     * @param action   UPSERT for create/update, DELETE for removal
     */
    void triggerCustomerEmbedding(Customer customer, EmbeddingAction action);

    /**
     * Trigger embedding for a Supplier entity.
     *
     * @param supplier The supplier entity (must be persisted with ID)
     * @param action   UPSERT for create/update, DELETE for removal
     */
    void triggerSupplierEmbedding(Supplier supplier, EmbeddingAction action);

    /**
     * Trigger embedding for a Chart of Account entity.
     *
     * @param account    The account entity (must be persisted with ID)
     * @param parentCode The parent account code (null if top-level)
     * @param action     UPSERT for create/update, DELETE for removal
     */
    void triggerChartOfAccountEmbedding(ChartOfAccount account, String parentCode, EmbeddingAction action);

    /**
     * Trigger embedding for a Sales Invoice entity.
     *
     * @param invoice      The sales invoice entity (must be persisted with ID)
     * @param lines        The invoice line items
     * @param customerName The customer name for display
     * @param action       UPSERT for create/update, DELETE for removal
     */
    void triggerSalesInvoiceEmbedding(
        com.accounting.entity.SalesInvoice invoice,
        java.util.List<com.accounting.entity.SalesInvoiceLine> lines,
        String customerName,
        EmbeddingAction action
    );

    /**
     * Trigger embedding for a Purchase Bill entity.
     *
     * @param bill         The purchase bill entity (must be persisted with ID)
     * @param lines        The bill line items
     * @param supplierName The supplier name for display
     * @param action       UPSERT for create/update, DELETE for removal
     */
    void triggerPurchaseBillEmbedding(
        com.accounting.entity.PurchaseBill bill,
        java.util.List<com.accounting.entity.PurchaseBillLine> lines,
        String supplierName,
        EmbeddingAction action
    );

    /**
     * Trigger embedding for an AR Payment (Receipt) entity.
     *
     * @param payment         The AR payment entity (must be persisted with ID)
     * @param allocations     The receipt allocations to invoices
     * @param customerName    The customer name for display
     * @param bankAccountName The bank account name (null if cash payment)
     * @param action          UPSERT for create/update, DELETE for removal
     */
    void triggerARPaymentEmbedding(
        com.accounting.entity.ARPayment payment,
        java.util.List<com.accounting.entity.ReceiptAllocation> allocations,
        String customerName,
        String bankAccountName,
        EmbeddingAction action
    );

    /**
     * Trigger embedding for an AP Payment entity.
     *
     * @param payment         The AP payment entity (must be persisted with ID)
     * @param allocations     The payment allocations to bills
     * @param supplierName    The supplier name for display
     * @param bankAccountName The bank account name (null if cash payment)
     * @param action          UPSERT for create/update, DELETE for removal
     */
    void triggerAPPaymentEmbedding(
        com.accounting.entity.APPayment payment,
        java.util.List<com.accounting.entity.PaymentAllocation> allocations,
        String supplierName,
        String bankAccountName,
        EmbeddingAction action
    );

    /**
     * Check if embedding triggers are enabled.
     * Returns false if chatbot feature is disabled or n8n is unavailable.
     *
     * @return true if embeddings will be triggered, false otherwise
     */
    boolean isEnabled();
}

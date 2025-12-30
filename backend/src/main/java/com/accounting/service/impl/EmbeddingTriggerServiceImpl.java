package com.accounting.service.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.dto.embedding.EntityEmbeddingPayload;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.Supplier;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.service.EmbeddingTriggerService;
import com.accounting.service.EntityTextSynthesizer;
import com.accounting.service.N8nWebhookService;

/**
 * Implementation of EmbeddingTriggerService.
 * Fire-and-forget pattern: all exceptions are caught and logged, never propagated.
 * 
 * <p>The service checks both the configuration flag (chatbot.enabled) AND webhook availability
 * before attempting to trigger embeddings. This ensures graceful degradation when n8n is down.
 */
@Service
public class EmbeddingTriggerServiceImpl implements EmbeddingTriggerService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingTriggerServiceImpl.class);

    private final EntityTextSynthesizer textSynthesizer;
    private final N8nWebhookService webhookService;
    private final boolean configEnabled;

    public EmbeddingTriggerServiceImpl(
            EntityTextSynthesizer textSynthesizer,
            N8nWebhookService webhookService,
            @Value("${chatbot.enabled:false}") boolean enabled) {
        this.textSynthesizer = textSynthesizer;
        this.webhookService = webhookService;
        this.configEnabled = enabled;

        if (enabled) {
            log.info("EmbeddingTriggerService initialized - embeddings ENABLED");
        } else {
            log.info("EmbeddingTriggerService initialized - embeddings DISABLED (chatbot.enabled=false)");
        }
    }

    /**
     * Check if embedding should be triggered.
     * Checks both config flag and webhook availability.
     */
    private boolean shouldTrigger() {
        if (!configEnabled) {
            return false;
        }
        if (!webhookService.isAvailable()) {
            log.debug("Embedding trigger skipped - n8n webhook not available");
            return false;
        }
        return true;
    }

    @Override
    public void triggerVoucherEmbedding(Voucher voucher, List<VoucherLine> lines, EmbeddingAction action) {
        if (!shouldTrigger()) {
            log.debug("Embedding trigger skipped for voucher: {}", voucher.getVoucherNumber());
            return;
        }

        try {
            EntityEmbeddingPayload payload = textSynthesizer.buildVoucherPayload(voucher, lines, action);
            webhookService.triggerEntityEmbedding(payload);

            log.debug("Triggered {} embedding for voucher: {} (id: {})",
                action, voucher.getVoucherNumber(), voucher.getId());
        } catch (Exception e) {
            log.error("Failed to trigger embedding for voucher {} (id: {}): {}",
                voucher.getVoucherNumber(), voucher.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void triggerCustomerEmbedding(Customer customer, EmbeddingAction action) {
        if (!shouldTrigger()) {
            log.debug("Embedding trigger skipped for customer: {}", customer.getCode());
            return;
        }

        try {
            EntityEmbeddingPayload payload = textSynthesizer.buildCustomerPayload(customer, action);
            webhookService.triggerEntityEmbedding(payload);

            log.debug("Triggered {} embedding for customer: {} (id: {})",
                action, customer.getCode(), customer.getId());
        } catch (Exception e) {
            log.error("Failed to trigger embedding for customer {} (id: {}): {}",
                customer.getCode(), customer.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void triggerSupplierEmbedding(Supplier supplier, EmbeddingAction action) {
        if (!shouldTrigger()) {
            log.debug("Embedding trigger skipped for supplier: {}", supplier.getCode());
            return;
        }

        try {
            EntityEmbeddingPayload payload = textSynthesizer.buildSupplierPayload(supplier, action);
            webhookService.triggerEntityEmbedding(payload);

            log.debug("Triggered {} embedding for supplier: {} (id: {})",
                action, supplier.getCode(), supplier.getId());
        } catch (Exception e) {
            log.error("Failed to trigger embedding for supplier {} (id: {}): {}",
                supplier.getCode(), supplier.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void triggerChartOfAccountEmbedding(ChartOfAccount account, String parentCode, EmbeddingAction action) {
        if (!shouldTrigger()) {
            log.debug("Embedding trigger skipped for account: {}", account.getCode());
            return;
        }

        try {
            EntityEmbeddingPayload payload = textSynthesizer.buildChartOfAccountPayload(account, parentCode, action);
            webhookService.triggerEntityEmbedding(payload);

            log.debug("Triggered {} embedding for account: {} (id: {})",
                action, account.getCode(), account.getId());
        } catch (Exception e) {
            log.error("Failed to trigger embedding for account {} (id: {}): {}",
                account.getCode(), account.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void triggerSalesInvoiceEmbedding(
            com.accounting.entity.SalesInvoice invoice,
            java.util.List<com.accounting.entity.SalesInvoiceLine> lines,
            String customerName,
            EmbeddingAction action) {
        if (!shouldTrigger()) {
            log.debug("Embedding trigger skipped for sales invoice: {}", invoice.getInvoiceNumber());
            return;
        }

        try {
            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(invoice, lines, customerName, action);
            webhookService.triggerEntityEmbedding(payload);

            log.debug("Triggered {} embedding for sales invoice: {} (id: {})",
                action, invoice.getInvoiceNumber(), invoice.getId());
        } catch (Exception e) {
            log.error("Failed to trigger embedding for sales invoice {} (id: {}): {}",
                invoice.getInvoiceNumber(), invoice.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void triggerPurchaseBillEmbedding(
            com.accounting.entity.PurchaseBill bill,
            java.util.List<com.accounting.entity.PurchaseBillLine> lines,
            String supplierName,
            EmbeddingAction action) {
        if (!shouldTrigger()) {
            log.debug("Embedding trigger skipped for purchase bill: {}", bill.getBillNumber());
            return;
        }

        try {
            EntityEmbeddingPayload payload = textSynthesizer.buildPurchaseBillPayload(bill, lines, supplierName, action);
            webhookService.triggerEntityEmbedding(payload);

            log.debug("Triggered {} embedding for purchase bill: {} (id: {})",
                action, bill.getBillNumber(), bill.getId());
        } catch (Exception e) {
            log.error("Failed to trigger embedding for purchase bill {} (id: {}): {}",
                bill.getBillNumber(), bill.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void triggerARPaymentEmbedding(
            com.accounting.entity.ARPayment payment,
            java.util.List<com.accounting.entity.ReceiptAllocation> allocations,
            String customerName,
            String bankAccountName,
            EmbeddingAction action) {
        if (!shouldTrigger()) {
            log.debug("Embedding trigger skipped for AR payment: {}", payment.getReceiptNumber());
            return;
        }

        try {
            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, allocations, customerName, bankAccountName, action);
            webhookService.triggerEntityEmbedding(payload);

            log.debug("Triggered {} embedding for AR payment: {} (id: {})",
                action, payment.getReceiptNumber(), payment.getId());
        } catch (Exception e) {
            log.error("Failed to trigger embedding for AR payment {} (id: {}): {}",
                payment.getReceiptNumber(), payment.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void triggerAPPaymentEmbedding(
            com.accounting.entity.APPayment payment,
            java.util.List<com.accounting.entity.PaymentAllocation> allocations,
            String supplierName,
            String bankAccountName,
            EmbeddingAction action) {
        if (!shouldTrigger()) {
            log.debug("Embedding trigger skipped for AP payment: {}", payment.getPaymentNumber());
            return;
        }

        try {
            EntityEmbeddingPayload payload = textSynthesizer.buildAPPaymentPayload(
                payment, allocations, supplierName, bankAccountName, action);
            webhookService.triggerEntityEmbedding(payload);

            log.debug("Triggered {} embedding for AP payment: {} (id: {})",
                action, payment.getPaymentNumber(), payment.getId());
        } catch (Exception e) {
            log.error("Failed to trigger embedding for AP payment {} (id: {}): {}",
                payment.getPaymentNumber(), payment.getId(), e.getMessage(), e);
        }
    }

    @Override
    public boolean isEnabled() {
        return configEnabled && webhookService.isAvailable();
    }
}

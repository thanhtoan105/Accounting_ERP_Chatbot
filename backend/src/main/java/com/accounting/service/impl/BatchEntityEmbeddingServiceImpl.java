package com.accounting.service.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.dto.embedding.EntityEmbeddingPayload;
import com.accounting.dto.embedding.EntityType;
import com.accounting.entity.APPayment;
import com.accounting.entity.ARPayment;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.PaymentAllocation;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillLine;
import com.accounting.entity.ReceiptAllocation;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.Supplier;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.APPaymentRepository;
import com.accounting.repository.ARPaymentRepository;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.PaymentAllocationRepository;
import com.accounting.repository.PurchaseBillLineRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.ReceiptAllocationRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.service.BatchEntityEmbeddingService;
import com.accounting.service.EntityTextSynthesizer;
import com.accounting.service.N8nWebhookService;

/**
 * Implementation of BatchEntityEmbeddingService.
 * Fetches entities from DB, synthesizes text, and sends to n8n webhook.
 */
@Service
public class BatchEntityEmbeddingServiceImpl implements BatchEntityEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(BatchEntityEmbeddingServiceImpl.class);

    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherLineRepository voucherLineRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceLineRepository salesInvoiceLineRepository;
    private final PurchaseBillRepository purchaseBillRepository;
    private final PurchaseBillLineRepository purchaseBillLineRepository;
    private final ARPaymentRepository arPaymentRepository;
    private final APPaymentRepository apPaymentRepository;
    private final ReceiptAllocationRepository receiptAllocationRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final BankAccountRepository bankAccountRepository;
    private final EntityTextSynthesizer textSynthesizer;
    private final N8nWebhookService webhookService;

    public BatchEntityEmbeddingServiceImpl(
            CustomerRepository customerRepository,
            SupplierRepository supplierRepository,
            ChartOfAccountsRepository chartOfAccountsRepository,
            VoucherRepository voucherRepository,
            VoucherLineRepository voucherLineRepository,
            SalesInvoiceRepository salesInvoiceRepository,
            SalesInvoiceLineRepository salesInvoiceLineRepository,
            PurchaseBillRepository purchaseBillRepository,
            PurchaseBillLineRepository purchaseBillLineRepository,
            ARPaymentRepository arPaymentRepository,
            APPaymentRepository apPaymentRepository,
            ReceiptAllocationRepository receiptAllocationRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            BankAccountRepository bankAccountRepository,
            EntityTextSynthesizer textSynthesizer,
            N8nWebhookService webhookService) {
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
        this.voucherRepository = voucherRepository;
        this.voucherLineRepository = voucherLineRepository;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.salesInvoiceLineRepository = salesInvoiceLineRepository;
        this.purchaseBillRepository = purchaseBillRepository;
        this.purchaseBillLineRepository = purchaseBillLineRepository;
        this.arPaymentRepository = arPaymentRepository;
        this.apPaymentRepository = apPaymentRepository;
        this.receiptAllocationRepository = receiptAllocationRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.textSynthesizer = textSynthesizer;
        this.webhookService = webhookService;
    }

    @Override
    @Transactional(readOnly = true)
    public BatchEmbeddingResult embedSample(Long companyId, EntityType entityType, int sampleSize) {
        logger.info("Embedding sample of {} {} entities for company {}", sampleSize, entityType, companyId);
        long start = System.currentTimeMillis();

        List<EntityEmbeddingPayload> payloads = fetchSamplePayloads(companyId, entityType, sampleSize);
        int success = 0;
        int failed = 0;

        for (EntityEmbeddingPayload payload : payloads) {
            try {
                webhookService.triggerEntityEmbeddingSync(payload);
                success++;
                logger.info("Successfully embedded {} entity: {}", entityType, payload.entityId());
            } catch (Exception e) {
                failed++;
                logger.error("Failed to embed {} entity: {}", entityType, payload.entityId(), e);
            }
        }

        long duration = System.currentTimeMillis() - start;
        logger.info("Sample embedding complete for {}: {} success, {} failed in {}ms",
            entityType, success, failed, duration);

        return new BatchEmbeddingResult(entityType, payloads.size(), success, failed, duration);
    }

    @Override
    @Transactional(readOnly = true)
    public BatchEmbeddingResult embedAll(Long companyId, EntityType entityType) {
        logger.info("Embedding all {} entities for company {}", entityType, companyId);
        long start = System.currentTimeMillis();

        List<EntityEmbeddingPayload> payloads = fetchAllPayloads(companyId, entityType);
        int success = 0;
        int failed = 0;

        for (EntityEmbeddingPayload payload : payloads) {
            try {
                // Use async for batch processing
                webhookService.triggerEntityEmbedding(payload);
                success++;
            } catch (Exception e) {
                failed++;
                logger.error("Failed to trigger embedding for {} entity: {}", entityType, payload.entityId(), e);
            }
        }

        long duration = System.currentTimeMillis() - start;
        logger.info("Batch embedding triggered for {}: {} total, {} success, {} failed in {}ms",
            entityType, payloads.size(), success, failed, duration);

        return new BatchEmbeddingResult(entityType, payloads.size(), success, failed, duration);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<EntityType, BatchEmbeddingResult> embedAllTypes(Long companyId) {
        Map<EntityType, BatchEmbeddingResult> results = new EnumMap<>(EntityType.class);

        for (EntityType type : getCompanyScopedTypes()) {
            try {
                results.put(type, embedAll(companyId, type));
            } catch (Exception e) {
                logger.error("Failed to embed {} entities for company {}", type, companyId, e);
                results.put(type, new BatchEmbeddingResult(type, 0, 0, 0, 0));
            }
        }

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<EntityType, Long> getEntityCounts(Long companyId) {
        Map<EntityType, Long> counts = new EnumMap<>(EntityType.class);

        counts.put(EntityType.CUSTOMER, (long) customerRepository.findByCompanyId(companyId).size());
        counts.put(EntityType.SUPPLIER, (long) supplierRepository.findByCompanyId(companyId).size());
        counts.put(EntityType.CHART_OF_ACCOUNTS, (long) chartOfAccountsRepository.findByCompanyId(companyId).size());
        counts.put(EntityType.VOUCHER, voucherRepository.countByCompanyId(companyId));
        counts.put(EntityType.SALES_INVOICE, (long) salesInvoiceRepository.findByCompanyId(companyId).size());
        counts.put(EntityType.PURCHASE_INVOICE, (long) purchaseBillRepository.findByCompanyId(companyId).size());
        counts.put(EntityType.AR_PAYMENT, (long) arPaymentRepository.findByCompanyId(companyId).size());
        counts.put(EntityType.AP_PAYMENT, (long) apPaymentRepository.findByCompanyId(companyId).size());

        return counts;
    }

    // ==================== Private Helper Methods ====================

    private List<EntityType> getCompanyScopedTypes() {
        return List.of(
            EntityType.CUSTOMER,
            EntityType.SUPPLIER,
            EntityType.CHART_OF_ACCOUNTS,
            EntityType.VOUCHER,
            EntityType.SALES_INVOICE,
            EntityType.PURCHASE_INVOICE,
            EntityType.AR_PAYMENT,
            EntityType.AP_PAYMENT
        );
    }

    private List<EntityEmbeddingPayload> fetchSamplePayloads(Long companyId, EntityType entityType, int sampleSize) {
        return switch (entityType) {
            case CUSTOMER -> fetchCustomerPayloads(companyId, sampleSize);
            case SUPPLIER -> fetchSupplierPayloads(companyId, sampleSize);
            case CHART_OF_ACCOUNTS -> fetchChartOfAccountPayloads(companyId, sampleSize);
            case VOUCHER -> fetchVoucherPayloads(companyId, sampleSize);
            case SALES_INVOICE -> fetchSalesInvoicePayloads(companyId, sampleSize);
            case PURCHASE_INVOICE -> fetchPurchaseBillPayloads(companyId, sampleSize);
            case AR_PAYMENT -> fetchARPaymentPayloads(companyId, sampleSize);
            case AP_PAYMENT -> fetchAPPaymentPayloads(companyId, sampleSize);
            default -> List.of();
        };
    }

    private List<EntityEmbeddingPayload> fetchAllPayloads(Long companyId, EntityType entityType) {
        return switch (entityType) {
            case CUSTOMER -> fetchCustomerPayloads(companyId, Integer.MAX_VALUE);
            case SUPPLIER -> fetchSupplierPayloads(companyId, Integer.MAX_VALUE);
            case CHART_OF_ACCOUNTS -> fetchChartOfAccountPayloads(companyId, Integer.MAX_VALUE);
            case VOUCHER -> fetchVoucherPayloads(companyId, Integer.MAX_VALUE);
            case SALES_INVOICE -> fetchSalesInvoicePayloads(companyId, Integer.MAX_VALUE);
            case PURCHASE_INVOICE -> fetchPurchaseBillPayloads(companyId, Integer.MAX_VALUE);
            case AR_PAYMENT -> fetchARPaymentPayloads(companyId, Integer.MAX_VALUE);
            case AP_PAYMENT -> fetchAPPaymentPayloads(companyId, Integer.MAX_VALUE);
            default -> List.of();
        };
    }

    // ==================== Entity-Specific Fetchers ====================

    private List<EntityEmbeddingPayload> fetchCustomerPayloads(Long companyId, int limit) {
        List<Customer> customers = customerRepository.findByCompanyId(companyId);
        return customers.stream()
            .limit(limit)
            .map(c -> textSynthesizer.buildCustomerPayload(c, EmbeddingAction.UPSERT))
            .toList();
    }

    private List<EntityEmbeddingPayload> fetchSupplierPayloads(Long companyId, int limit) {
        List<Supplier> suppliers = supplierRepository.findByCompanyId(companyId);
        return suppliers.stream()
            .limit(limit)
            .map(s -> textSynthesizer.buildSupplierPayload(s, EmbeddingAction.UPSERT))
            .toList();
    }

    private List<EntityEmbeddingPayload> fetchChartOfAccountPayloads(Long companyId, int limit) {
        List<ChartOfAccount> accounts = chartOfAccountsRepository.findByCompanyId(companyId);
        List<EntityEmbeddingPayload> payloads = new ArrayList<>();

        for (ChartOfAccount account : accounts) {
            if (payloads.size() >= limit) break;

            String parentCode = null;
            if (account.getParentId() != null) {
                parentCode = chartOfAccountsRepository.findById(account.getParentId())
                    .map(ChartOfAccount::getCode)
                    .orElse(null);
            }
            payloads.add(textSynthesizer.buildChartOfAccountPayload(account, parentCode, EmbeddingAction.UPSERT));
        }

        return payloads;
    }

    private List<EntityEmbeddingPayload> fetchVoucherPayloads(Long companyId, int limit) {
        List<Voucher> vouchers = voucherRepository.findByCompanyId(companyId);
        List<EntityEmbeddingPayload> payloads = new ArrayList<>();

        for (Voucher voucher : vouchers) {
            if (payloads.size() >= limit) break;

            List<VoucherLine> lines = voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucher.getId());
            payloads.add(textSynthesizer.buildVoucherPayload(voucher, lines, EmbeddingAction.UPSERT));
        }

        return payloads;
    }

    private List<EntityEmbeddingPayload> fetchSalesInvoicePayloads(Long companyId, int limit) {
        List<SalesInvoice> invoices = salesInvoiceRepository.findByCompanyId(companyId);
        List<EntityEmbeddingPayload> payloads = new ArrayList<>();

        for (SalesInvoice invoice : invoices) {
            if (payloads.size() >= limit) break;

            List<SalesInvoiceLine> lines = salesInvoiceLineRepository
                .findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId());
            String customerName = customerRepository.findById(invoice.getCustomerId())
                .map(Customer::getName)
                .orElse("Unknown");

            payloads.add(textSynthesizer.buildSalesInvoicePayload(invoice, lines, customerName, EmbeddingAction.UPSERT));
        }

        return payloads;
    }

    private List<EntityEmbeddingPayload> fetchPurchaseBillPayloads(Long companyId, int limit) {
        List<PurchaseBill> bills = purchaseBillRepository.findByCompanyId(companyId);
        List<EntityEmbeddingPayload> payloads = new ArrayList<>();

        for (PurchaseBill bill : bills) {
            if (payloads.size() >= limit) break;

            List<PurchaseBillLine> lines = purchaseBillLineRepository
                .findByPurchaseBillIdOrderByLineNumberAsc(bill.getId());
            String supplierName = supplierRepository.findById(bill.getSupplierId())
                .map(Supplier::getName)
                .orElse("Unknown");

            payloads.add(textSynthesizer.buildPurchaseBillPayload(bill, lines, supplierName, EmbeddingAction.UPSERT));
        }

        return payloads;
    }

    private List<EntityEmbeddingPayload> fetchARPaymentPayloads(Long companyId, int limit) {
        List<ARPayment> payments = arPaymentRepository.findByCompanyId(companyId);
        List<EntityEmbeddingPayload> payloads = new ArrayList<>();

        for (ARPayment payment : payments) {
            if (payloads.size() >= limit) break;

            List<ReceiptAllocation> allocations = receiptAllocationRepository
                .findByReceiptIdOrderByAllocationOrderAsc(payment.getId());
            String customerName = customerRepository.findById(payment.getCustomerId())
                .map(Customer::getName)
                .orElse("Unknown");
            String bankAccountName = payment.getBankAccountId() != null
                ? bankAccountRepository.findById(payment.getBankAccountId())
                    .map(ba -> ba.getBankName() + " - " + ba.getAccountNumber())
                    .orElse(null)
                : null;

            payloads.add(textSynthesizer.buildARPaymentPayload(
                payment, allocations, customerName, bankAccountName, EmbeddingAction.UPSERT));
        }

        return payloads;
    }

    private List<EntityEmbeddingPayload> fetchAPPaymentPayloads(Long companyId, int limit) {
        List<APPayment> payments = apPaymentRepository.findByCompanyId(companyId);
        List<EntityEmbeddingPayload> payloads = new ArrayList<>();

        for (APPayment payment : payments) {
            if (payloads.size() >= limit) break;

            List<PaymentAllocation> allocations = paymentAllocationRepository
                .findByPaymentIdOrderByAllocationOrder(payment.getId());
            String supplierName = supplierRepository.findById(payment.getSupplierId())
                .map(Supplier::getName)
                .orElse("Unknown");
            String bankAccountName = payment.getBankAccountId() != null
                ? bankAccountRepository.findById(payment.getBankAccountId())
                    .map(ba -> ba.getBankName() + " - " + ba.getAccountNumber())
                    .orElse(null)
                : null;

            payloads.add(textSynthesizer.buildAPPaymentPayload(
                payment, allocations, supplierName, bankAccountName, EmbeddingAction.UPSERT));
        }

        return payloads;
    }
}

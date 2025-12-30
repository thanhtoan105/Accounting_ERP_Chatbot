package com.accounting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.dto.embedding.EntityEmbeddingPayload;
import com.accounting.dto.embedding.EntityType;
import com.accounting.entity.APPayment;
import com.accounting.entity.ARPayment;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.PaymentAllocation;
import com.accounting.entity.PaymentMethod;
import com.accounting.entity.PaymentStatus;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillLine;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.ReceiptAllocation;
import com.accounting.entity.ReceiptStatus;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.VatRate;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.service.EntityTextSynthesizer;
import com.accounting.service.N8nWebhookService;

@DisplayName("EmbeddingTriggerService Tests")
class EmbeddingTriggerServiceImplTest {

    private EntityTextSynthesizer textSynthesizer;
    private N8nWebhookService webhookService;
    private EmbeddingTriggerServiceImpl embeddingTriggerService;

    @BeforeEach
    void setUp() {
        textSynthesizer = mock(EntityTextSynthesizer.class);
        webhookService = mock(N8nWebhookService.class);
    }

    @Nested
    @DisplayName("When embedding is enabled")
    class WhenEnabled {

        @BeforeEach
        void setUp() {
            // Mock webhook availability check
            when(webhookService.isAvailable()).thenReturn(true);
            embeddingTriggerService = new EmbeddingTriggerServiceImpl(
                textSynthesizer, webhookService, true);
        }

        @Test
        @DisplayName("should trigger voucher embedding with UPSERT action")
        void triggerVoucherEmbedding_upsert() {
            Voucher voucher = createTestVoucher();
            List<VoucherLine> lines = createTestVoucherLines(voucher.getId());
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.VOUCHER, voucher.getId().toString());

            when(textSynthesizer.buildVoucherPayload(voucher, lines, EmbeddingAction.UPSERT))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerVoucherEmbedding(voucher, lines, EmbeddingAction.UPSERT);

            verify(textSynthesizer).buildVoucherPayload(voucher, lines, EmbeddingAction.UPSERT);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should trigger voucher embedding with DELETE action")
        void triggerVoucherEmbedding_delete() {
            Voucher voucher = createTestVoucher();
            List<VoucherLine> lines = List.of();
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.VOUCHER, voucher.getId().toString());

            when(textSynthesizer.buildVoucherPayload(voucher, lines, EmbeddingAction.DELETE))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerVoucherEmbedding(voucher, lines, EmbeddingAction.DELETE);

            verify(textSynthesizer).buildVoucherPayload(voucher, lines, EmbeddingAction.DELETE);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should trigger customer embedding with UPSERT action")
        void triggerCustomerEmbedding_upsert() {
            Customer customer = createTestCustomer();
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.CUSTOMER, customer.getId().toString());

            when(textSynthesizer.buildCustomerPayload(customer, EmbeddingAction.UPSERT))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerCustomerEmbedding(customer, EmbeddingAction.UPSERT);

            verify(textSynthesizer).buildCustomerPayload(customer, EmbeddingAction.UPSERT);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should trigger supplier embedding with UPSERT action")
        void triggerSupplierEmbedding_upsert() {
            Supplier supplier = createTestSupplier();
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.SUPPLIER, supplier.getId().toString());

            when(textSynthesizer.buildSupplierPayload(supplier, EmbeddingAction.UPSERT))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerSupplierEmbedding(supplier, EmbeddingAction.UPSERT);

            verify(textSynthesizer).buildSupplierPayload(supplier, EmbeddingAction.UPSERT);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should trigger chart of account embedding with parent code")
        void triggerChartOfAccountEmbedding_withParent() {
            ChartOfAccount account = createTestAccount();
            String parentCode = "111";
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.CHART_OF_ACCOUNTS, account.getId().toString());

            when(textSynthesizer.buildChartOfAccountPayload(account, parentCode, EmbeddingAction.UPSERT))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerChartOfAccountEmbedding(account, parentCode, EmbeddingAction.UPSERT);

            verify(textSynthesizer).buildChartOfAccountPayload(account, parentCode, EmbeddingAction.UPSERT);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should trigger sales invoice embedding with UPSERT action")
        void triggerSalesInvoiceEmbedding_upsert() {
            SalesInvoice invoice = createTestSalesInvoice();
            List<SalesInvoiceLine> lines = createTestSalesInvoiceLines(invoice.getId());
            String customerName = "Test Customer";
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.SALES_INVOICE, invoice.getId().toString());

            when(textSynthesizer.buildSalesInvoicePayload(invoice, lines, customerName, EmbeddingAction.UPSERT))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerSalesInvoiceEmbedding(invoice, lines, customerName, EmbeddingAction.UPSERT);

            verify(textSynthesizer).buildSalesInvoicePayload(invoice, lines, customerName, EmbeddingAction.UPSERT);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should trigger sales invoice embedding with DELETE action")
        void triggerSalesInvoiceEmbedding_delete() {
            SalesInvoice invoice = createTestSalesInvoice();
            List<SalesInvoiceLine> lines = List.of();
            String customerName = "Test Customer";
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.SALES_INVOICE, invoice.getId().toString());

            when(textSynthesizer.buildSalesInvoicePayload(invoice, lines, customerName, EmbeddingAction.DELETE))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerSalesInvoiceEmbedding(invoice, lines, customerName, EmbeddingAction.DELETE);

            verify(textSynthesizer).buildSalesInvoicePayload(invoice, lines, customerName, EmbeddingAction.DELETE);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should trigger purchase bill embedding with UPSERT action")
        void triggerPurchaseBillEmbedding_upsert() {
            PurchaseBill bill = createTestPurchaseBill();
            List<PurchaseBillLine> lines = createTestPurchaseBillLines(bill.getId());
            String supplierName = "Test Supplier";
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.PURCHASE_INVOICE, bill.getId().toString());

            when(textSynthesizer.buildPurchaseBillPayload(bill, lines, supplierName, EmbeddingAction.UPSERT))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerPurchaseBillEmbedding(bill, lines, supplierName, EmbeddingAction.UPSERT);

            verify(textSynthesizer).buildPurchaseBillPayload(bill, lines, supplierName, EmbeddingAction.UPSERT);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should trigger AR payment embedding with UPSERT action")
        void triggerARPaymentEmbedding_upsert() {
            ARPayment payment = createTestARPayment();
            List<ReceiptAllocation> allocations = List.of();
            String customerName = "Test Customer";
            String bankAccountName = "VCB - 123456";
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.AR_PAYMENT, payment.getId().toString());

            when(textSynthesizer.buildARPaymentPayload(payment, allocations, customerName, bankAccountName, EmbeddingAction.UPSERT))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerARPaymentEmbedding(payment, allocations, customerName, bankAccountName, EmbeddingAction.UPSERT);

            verify(textSynthesizer).buildARPaymentPayload(payment, allocations, customerName, bankAccountName, EmbeddingAction.UPSERT);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should trigger AP payment embedding with UPSERT action")
        void triggerAPPaymentEmbedding_upsert() {
            APPayment payment = createTestAPPayment();
            List<PaymentAllocation> allocations = List.of();
            String supplierName = "Test Supplier";
            String bankAccountName = "VCB - 123456";
            EntityEmbeddingPayload mockPayload = createMockPayload(EntityType.AP_PAYMENT, payment.getId().toString());

            when(textSynthesizer.buildAPPaymentPayload(payment, allocations, supplierName, bankAccountName, EmbeddingAction.UPSERT))
                .thenReturn(mockPayload);
            when(webhookService.triggerEntityEmbedding(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            embeddingTriggerService.triggerAPPaymentEmbedding(payment, allocations, supplierName, bankAccountName, EmbeddingAction.UPSERT);

            verify(textSynthesizer).buildAPPaymentPayload(payment, allocations, supplierName, bankAccountName, EmbeddingAction.UPSERT);
            verify(webhookService).triggerEntityEmbedding(mockPayload);
        }

        @Test
        @DisplayName("should not throw exception when webhook fails")
        void triggerEmbedding_webhookFailure_noException() {
            Customer customer = createTestCustomer();

            when(textSynthesizer.buildCustomerPayload(any(), any()))
                .thenThrow(new RuntimeException("Simulated failure"));

            embeddingTriggerService.triggerCustomerEmbedding(customer, EmbeddingAction.UPSERT);

            verify(webhookService, never()).triggerEntityEmbedding(any());
        }

        @Test
        @DisplayName("isEnabled should return true when webhook is available")
        void isEnabled_whenWebhookAvailable_returnsTrue() {
            when(webhookService.isAvailable()).thenReturn(true);

            assertThat(embeddingTriggerService.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("isEnabled should return false when webhook is unavailable")
        void isEnabled_whenWebhookUnavailable_returnsFalse() {
            when(webhookService.isAvailable()).thenReturn(false);

            assertThat(embeddingTriggerService.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("When embedding is disabled")
    class WhenDisabled {

        @BeforeEach
        void setUp() {
            embeddingTriggerService = new EmbeddingTriggerServiceImpl(
                textSynthesizer, webhookService, false);
        }

        @Test
        @DisplayName("should not trigger voucher embedding")
        void triggerVoucherEmbedding_disabled() {
            Voucher voucher = createTestVoucher();
            List<VoucherLine> lines = createTestVoucherLines(voucher.getId());

            embeddingTriggerService.triggerVoucherEmbedding(voucher, lines, EmbeddingAction.UPSERT);

            verify(textSynthesizer, never()).buildVoucherPayload(any(), any(), any());
            verify(webhookService, never()).triggerEntityEmbedding(any());
        }

        @Test
        @DisplayName("should not trigger customer embedding")
        void triggerCustomerEmbedding_disabled() {
            Customer customer = createTestCustomer();

            embeddingTriggerService.triggerCustomerEmbedding(customer, EmbeddingAction.UPSERT);

            verify(textSynthesizer, never()).buildCustomerPayload(any(), any());
            verify(webhookService, never()).triggerEntityEmbedding(any());
        }

        @Test
        @DisplayName("should not trigger supplier embedding")
        void triggerSupplierEmbedding_disabled() {
            Supplier supplier = createTestSupplier();

            embeddingTriggerService.triggerSupplierEmbedding(supplier, EmbeddingAction.UPSERT);

            verify(textSynthesizer, never()).buildSupplierPayload(any(), any());
            verify(webhookService, never()).triggerEntityEmbedding(any());
        }

        @Test
        @DisplayName("isEnabled should return false")
        void isEnabled_returnsFalse() {
            assertThat(embeddingTriggerService.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("When embedding is enabled but webhook is unavailable")
    class WhenWebhookUnavailable {

        @BeforeEach
        void setUp() {
            // Mock webhook as unavailable
            when(webhookService.isAvailable()).thenReturn(false);
            embeddingTriggerService = new EmbeddingTriggerServiceImpl(
                textSynthesizer, webhookService, true);
        }

        @Test
        @DisplayName("should not trigger voucher embedding when webhook unavailable")
        void triggerVoucherEmbedding_webhookUnavailable() {
            Voucher voucher = createTestVoucher();
            List<VoucherLine> lines = createTestVoucherLines(voucher.getId());

            embeddingTriggerService.triggerVoucherEmbedding(voucher, lines, EmbeddingAction.UPSERT);

            verify(textSynthesizer, never()).buildVoucherPayload(any(), any(), any());
            verify(webhookService, never()).triggerEntityEmbedding(any());
        }

        @Test
        @DisplayName("should not trigger customer embedding when webhook unavailable")
        void triggerCustomerEmbedding_webhookUnavailable() {
            Customer customer = createTestCustomer();

            embeddingTriggerService.triggerCustomerEmbedding(customer, EmbeddingAction.UPSERT);

            verify(textSynthesizer, never()).buildCustomerPayload(any(), any());
            verify(webhookService, never()).triggerEntityEmbedding(any());
        }

        @Test
        @DisplayName("isEnabled should return false when webhook unavailable")
        void isEnabled_returnsFalse() {
            assertThat(embeddingTriggerService.isEnabled()).isFalse();
        }
    }

    private Voucher createTestVoucher() {
        Voucher voucher = new Voucher();
        voucher.setId(UUID.randomUUID());
        voucher.setCompanyId(1L);
        voucher.setVoucherNumber("VC2024-001");
        voucher.setVoucherDate(LocalDate.now());
        voucher.setDescription("Test voucher");
        voucher.setStatus("draft");
        voucher.setCurrency("VND");
        voucher.setTotalDebit(BigDecimal.valueOf(1000000));
        voucher.setTotalCredit(BigDecimal.valueOf(1000000));
        return voucher;
    }

    private List<VoucherLine> createTestVoucherLines(UUID voucherId) {
        VoucherLine line1 = new VoucherLine();
        line1.setVoucherId(voucherId);
        line1.setLineNumber(1);
        line1.setAccountId(131L);
        line1.setDebit(BigDecimal.valueOf(1000000));
        line1.setCredit(BigDecimal.ZERO);
        line1.setCompanyId(1L);

        VoucherLine line2 = new VoucherLine();
        line2.setVoucherId(voucherId);
        line2.setLineNumber(2);
        line2.setAccountId(511L);
        line2.setDebit(BigDecimal.ZERO);
        line2.setCredit(BigDecimal.valueOf(1000000));
        line2.setCompanyId(1L);

        return List.of(line1, line2);
    }

    private Customer createTestCustomer() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCompanyId(1L);
        customer.setCode("KH001");
        customer.setName("Test Customer");
        customer.setActive(true);
        return customer;
    }

    private Supplier createTestSupplier() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setCompanyId(1L);
        supplier.setCode("NCC001");
        supplier.setName("Test Supplier");
        supplier.setActive(true);
        return supplier;
    }

    private ChartOfAccount createTestAccount() {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(1L);
        account.setCompanyId(1L);
        account.setCode("1111");
        account.setName("Tiền mặt");
        account.setType("asset");
        account.setNormalSide("debit");
        account.setPostable(true);
        return account;
    }

    private EntityEmbeddingPayload createMockPayload(EntityType type, String entityId) {
        return EntityEmbeddingPayload.builder()
            .entityType(type)
            .entityId(entityId)
            .companyId(1L)
            .action(EmbeddingAction.UPSERT)
            .text("Test embedding text")
            .build();
    }

    private SalesInvoice createTestSalesInvoice() {
        SalesInvoice invoice = new SalesInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setCompanyId(1L);
        invoice.setCustomerId(1L);
        invoice.setInvoiceNumber("SI2024-001");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setReference("REF-001");
        invoice.setDescription("Test sales invoice");
        invoice.setStatus(SalesInvoiceStatus.DRAFT);
        invoice.setTotalAmount(BigDecimal.valueOf(1000000));
        invoice.setVatAmount(BigDecimal.valueOf(100000));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setRemainingBalance(BigDecimal.valueOf(1000000));
        invoice.setCreatedById(1L);
        return invoice;
    }

    private List<SalesInvoiceLine> createTestSalesInvoiceLines(UUID invoiceId) {
        SalesInvoiceLine line = new SalesInvoiceLine();
        line.setSalesInvoiceId(invoiceId);
        line.setLineNumber(1);
        line.setAccountId(511L);
        line.setDescription("Test product");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(BigDecimal.valueOf(1000000));
        line.setAmount(BigDecimal.valueOf(1000000));
        line.setVatRate(VatRate.TEN);
        line.setVatAmount(BigDecimal.valueOf(100000));
        line.setCompanyId(1L);
        return List.of(line);
    }

    private PurchaseBill createTestPurchaseBill() {
        PurchaseBill bill = new PurchaseBill();
        bill.setId(UUID.randomUUID());
        bill.setCompanyId(1L);
        bill.setSupplierId(1L);
        bill.setBillNumber("PB2024-001");
        bill.setBillDate(LocalDate.now());
        bill.setDueDate(LocalDate.now().plusDays(30));
        bill.setReference("REF-001");
        bill.setDescription("Test purchase bill");
        bill.setStatus(PurchaseBillStatus.DRAFT);
        bill.setTotalAmount(BigDecimal.valueOf(500000));
        bill.setVatAmount(BigDecimal.valueOf(50000));
        bill.setAmountPaid(BigDecimal.ZERO);
        bill.setRemainingBalance(BigDecimal.valueOf(500000));
        bill.setCreatedById(1L);
        return bill;
    }

    private List<PurchaseBillLine> createTestPurchaseBillLines(UUID billId) {
        PurchaseBillLine line = new PurchaseBillLine();
        line.setPurchaseBillId(billId);
        line.setLineNumber(1);
        line.setAccountId(621L);
        line.setDescription("Test expense");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(BigDecimal.valueOf(500000));
        line.setAmount(BigDecimal.valueOf(500000));
        line.setVatRate(VatRate.TEN);
        line.setVatAmount(BigDecimal.valueOf(50000));
        line.setCompanyId(1L);
        return List.of(line);
    }

    private ARPayment createTestARPayment() {
        ARPayment payment = new ARPayment();
        payment.setId(UUID.randomUUID());
        payment.setCompanyId(1L);
        payment.setCustomerId(1L);
        payment.setReceiptNumber("RC2024-001");
        payment.setReceiptDate(LocalDate.now());
        payment.setPayee("Test Payee");
        payment.setAmount(BigDecimal.valueOf(1000000));
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(ReceiptStatus.DRAFT);
        payment.setIsStandalone(false);
        payment.setCreatedById(1L);
        return payment;
    }

    private APPayment createTestAPPayment() {
        APPayment payment = new APPayment();
        payment.setId(UUID.randomUUID());
        payment.setCompanyId(1L);
        payment.setSupplierId(1L);
        payment.setPaymentNumber("PM2024-001");
        payment.setPaymentDate(LocalDate.now());
        payment.setPayee("Test Payee");
        payment.setAmount(BigDecimal.valueOf(500000));
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.DRAFT);
        payment.setIsStandalone(false);
        payment.setCreatedById(1L);
        return payment;
    }
}

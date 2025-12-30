package com.accounting.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.dto.embedding.EntityEmbeddingPayload;
import com.accounting.dto.embedding.EntityType;
import com.accounting.entity.APPayment;
import com.accounting.entity.ARPayment;
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
import com.accounting.entity.VatRate;
import com.accounting.repository.ChartOfAccountsRepository;

@DisplayName("EntityTextSynthesizerImpl Tests")
class EntityTextSynthesizerImplTest {

    private ChartOfAccountsRepository chartOfAccountsRepository;
    private EntityTextSynthesizerImpl textSynthesizer;

    @BeforeEach
    void setUp() {
        chartOfAccountsRepository = mock(ChartOfAccountsRepository.class);
        textSynthesizer = new EntityTextSynthesizerImpl(chartOfAccountsRepository);
    }

    @Nested
    @DisplayName("Sales Invoice Payload Tests")
    class SalesInvoicePayloadTests {

        @Test
        @DisplayName("should build sales invoice payload with correct entity type and metadata")
        void buildSalesInvoicePayload_withFullData() {
            SalesInvoice invoice = createTestSalesInvoice();
            List<SalesInvoiceLine> lines = createTestSalesInvoiceLines(invoice.getId());
            String customerName = "Công ty TNHH ABC";

            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(
                invoice, lines, customerName, EmbeddingAction.UPSERT);

            assertThat(payload.entityType()).isEqualTo(EntityType.SALES_INVOICE);
            assertThat(payload.entityId()).isEqualTo(invoice.getId().toString());
            assertThat(payload.companyId()).isEqualTo(1L);
            assertThat(payload.action()).isEqualTo(EmbeddingAction.UPSERT);
            assertThat(payload.namespace()).isEqualTo("company_1");
        }

        @Test
        @DisplayName("should synthesize Vietnamese text with invoice details")
        void buildSalesInvoicePayload_textContainsVietnameseFormat() {
            SalesInvoice invoice = createTestSalesInvoice();
            List<SalesInvoiceLine> lines = createTestSalesInvoiceLines(invoice.getId());
            String customerName = "Công ty TNHH ABC";

            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(
                invoice, lines, customerName, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Hóa đơn bán hàng số SI2024-001");
            assertThat(text).contains("Khách hàng: Công ty TNHH ABC");
            assertThat(text).contains("Trạng thái: Nháp");
            assertThat(text).contains("Tổng tiền hàng:");
            assertThat(text).contains("Thuế VAT:");
            assertThat(text).contains("VND");
        }

        @Test
        @DisplayName("should include line details in synthesized text")
        void buildSalesInvoicePayload_textContainsLineDetails() {
            SalesInvoice invoice = createTestSalesInvoice();
            List<SalesInvoiceLine> lines = createTestSalesInvoiceLines(invoice.getId());
            String customerName = "Công ty TNHH ABC";

            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(
                invoice, lines, customerName, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Chi tiết:");
            assertThat(text).contains("Sản phẩm test");
            assertThat(text).contains("SL");
        }

        @Test
        @DisplayName("should build metadata with all required fields")
        void buildSalesInvoicePayload_metadataContainsAllFields() {
            SalesInvoice invoice = createTestSalesInvoice();
            List<SalesInvoiceLine> lines = createTestSalesInvoiceLines(invoice.getId());
            String customerName = "Công ty TNHH ABC";

            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(
                invoice, lines, customerName, EmbeddingAction.UPSERT);

            assertThat(payload.metadata()).containsEntry("entityType", "sales_invoice");
            assertThat(payload.metadata()).containsEntry("invoiceNumber", "SI2024-001");
            assertThat(payload.metadata()).containsEntry("customerId", 1L);
            assertThat(payload.metadata()).containsEntry("customerName", "Công ty TNHH ABC");
            assertThat(payload.metadata()).containsEntry("status", "DRAFT");
            assertThat(payload.metadata()).containsKey("totalAmount");
            assertThat(payload.metadata()).containsKey("vatAmount");
        }

        @Test
        @DisplayName("should handle empty line list")
        void buildSalesInvoicePayload_withEmptyLines() {
            SalesInvoice invoice = createTestSalesInvoice();

            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(
                invoice, List.of(), "Customer", EmbeddingAction.UPSERT);

            assertThat(payload.text()).doesNotContain("Chi tiết:");
            assertThat(payload.text()).contains("Hóa đơn bán hàng số");
        }

        @Test
        @DisplayName("should handle DELETE action")
        void buildSalesInvoicePayload_withDeleteAction() {
            SalesInvoice invoice = createTestSalesInvoice();

            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(
                invoice, List.of(), "Customer", EmbeddingAction.DELETE);

            assertThat(payload.action()).isEqualTo(EmbeddingAction.DELETE);
        }

        @Test
        @DisplayName("should handle null description gracefully")
        void buildSalesInvoicePayload_withNullDescription() {
            SalesInvoice invoice = createTestSalesInvoice();
            invoice.setDescription(null);

            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(
                invoice, List.of(), "Customer", EmbeddingAction.UPSERT);

            assertThat(payload.text()).doesNotContain("Nội dung:");
        }
    }

    @Nested
    @DisplayName("Purchase Bill Payload Tests")
    class PurchaseBillPayloadTests {

        @Test
        @DisplayName("should build purchase bill payload with correct entity type")
        void buildPurchaseBillPayload_withFullData() {
            PurchaseBill bill = createTestPurchaseBill();
            List<PurchaseBillLine> lines = createTestPurchaseBillLines(bill.getId());
            String supplierName = "NCC Văn phòng phẩm";

            EntityEmbeddingPayload payload = textSynthesizer.buildPurchaseBillPayload(
                bill, lines, supplierName, EmbeddingAction.UPSERT);

            assertThat(payload.entityType()).isEqualTo(EntityType.PURCHASE_INVOICE);
            assertThat(payload.entityId()).isEqualTo(bill.getId().toString());
            assertThat(payload.companyId()).isEqualTo(1L);
            assertThat(payload.action()).isEqualTo(EmbeddingAction.UPSERT);
        }

        @Test
        @DisplayName("should synthesize Vietnamese text with bill details")
        void buildPurchaseBillPayload_textContainsVietnameseFormat() {
            PurchaseBill bill = createTestPurchaseBill();
            List<PurchaseBillLine> lines = createTestPurchaseBillLines(bill.getId());
            String supplierName = "NCC Văn phòng phẩm";

            EntityEmbeddingPayload payload = textSynthesizer.buildPurchaseBillPayload(
                bill, lines, supplierName, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Hóa đơn mua hàng số PB2024-001");
            assertThat(text).contains("Nhà cung cấp: NCC Văn phòng phẩm");
            assertThat(text).contains("Trạng thái: Nháp");
            assertThat(text).contains("Hạn thanh toán:");
            assertThat(text).contains("Tổng tiền hàng:");
            assertThat(text).contains("Đã thanh toán:");
            assertThat(text).contains("Còn lại:");
        }

        @Test
        @DisplayName("should include line details with VAT")
        void buildPurchaseBillPayload_textContainsLineDetailsWithVAT() {
            PurchaseBill bill = createTestPurchaseBill();
            List<PurchaseBillLine> lines = createTestPurchaseBillLines(bill.getId());
            String supplierName = "NCC Văn phòng phẩm";

            EntityEmbeddingPayload payload = textSynthesizer.buildPurchaseBillPayload(
                bill, lines, supplierName, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Chi tiết:");
            assertThat(text).contains("VAT:");
        }

        @Test
        @DisplayName("should build metadata with supplier info")
        void buildPurchaseBillPayload_metadataContainsSupplierInfo() {
            PurchaseBill bill = createTestPurchaseBill();
            String supplierName = "NCC XYZ";

            EntityEmbeddingPayload payload = textSynthesizer.buildPurchaseBillPayload(
                bill, List.of(), supplierName, EmbeddingAction.UPSERT);

            assertThat(payload.metadata()).containsEntry("entityType", "purchase_invoice");
            assertThat(payload.metadata()).containsEntry("billNumber", "PB2024-001");
            assertThat(payload.metadata()).containsEntry("supplierId", 1L);
            assertThat(payload.metadata()).containsEntry("supplierName", "NCC XYZ");
            assertThat(payload.metadata()).containsEntry("status", "DRAFT");
        }

        @Test
        @DisplayName("should handle null supplier name")
        void buildPurchaseBillPayload_withNullSupplierName() {
            PurchaseBill bill = createTestPurchaseBill();

            EntityEmbeddingPayload payload = textSynthesizer.buildPurchaseBillPayload(
                bill, List.of(), null, EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Nhà cung cấp: null");
            assertThat(payload.metadata()).containsEntry("supplierName", "");
        }
    }

    @Nested
    @DisplayName("AR Payment Payload Tests")
    class ARPaymentPayloadTests {

        @Test
        @DisplayName("should build AR payment payload with correct entity type")
        void buildARPaymentPayload_withFullData() {
            ARPayment payment = createTestARPayment();
            List<ReceiptAllocation> allocations = createTestReceiptAllocations(payment.getId());
            String customerName = "Khách hàng VIP";
            String bankAccountName = "VCB - 123456789";

            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, allocations, customerName, bankAccountName, EmbeddingAction.UPSERT);

            assertThat(payload.entityType()).isEqualTo(EntityType.AR_PAYMENT);
            assertThat(payload.entityId()).isEqualTo(payment.getId().toString());
            assertThat(payload.companyId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should synthesize Vietnamese text for receipt")
        void buildARPaymentPayload_textContainsVietnameseFormat() {
            ARPayment payment = createTestARPayment();
            String customerName = "Khách hàng VIP";
            String bankAccountName = "VCB - 123456789";

            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, List.of(), customerName, bankAccountName, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Phiếu thu số RC2024-001");
            assertThat(text).contains("Khách hàng: Khách hàng VIP");
            assertThat(text).contains("Người nộp: Nguyễn Văn A");
            assertThat(text).contains("Trạng thái: Nháp");
            assertThat(text).contains("Phương thức: Chuyển khoản");
            assertThat(text).contains("Tài khoản: VCB - 123456789");
            assertThat(text).contains("Số tiền:");
        }

        @Test
        @DisplayName("should include allocation details")
        void buildARPaymentPayload_textContainsAllocations() {
            ARPayment payment = createTestARPayment();
            List<ReceiptAllocation> allocations = createTestReceiptAllocations(payment.getId());

            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, allocations, "Customer", null, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Phân bổ cho hóa đơn:");
        }

        @Test
        @DisplayName("should indicate standalone payment when no allocations")
        void buildARPaymentPayload_standalonePayment() {
            ARPayment payment = createTestARPayment();
            payment.setIsStandalone(true);

            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, List.of(), "Customer", null, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Thanh toán trước (chưa gắn hóa đơn)");
        }

        @Test
        @DisplayName("should build metadata with payment method")
        void buildARPaymentPayload_metadataContainsPaymentMethod() {
            ARPayment payment = createTestARPayment();

            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, List.of(), "Customer", null, EmbeddingAction.UPSERT);

            assertThat(payload.metadata()).containsEntry("entityType", "ar_payment");
            assertThat(payload.metadata()).containsEntry("receiptNumber", "RC2024-001");
            assertThat(payload.metadata()).containsEntry("paymentMethod", "BANK_TRANSFER");
            assertThat(payload.metadata()).containsEntry("isStandalone", false);
        }

        @Test
        @DisplayName("should handle null bank account name")
        void buildARPaymentPayload_withNullBankAccountName() {
            ARPayment payment = createTestARPayment();

            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, List.of(), "Customer", null, EmbeddingAction.UPSERT);

            assertThat(payload.text()).doesNotContain("Tài khoản:");
        }

        @Test
        @DisplayName("should include reference if present")
        void buildARPaymentPayload_withReference() {
            ARPayment payment = createTestARPayment();
            payment.setReference("REF-2024-001");

            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, List.of(), "Customer", null, EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Tham chiếu: REF-2024-001");
        }
    }

    @Nested
    @DisplayName("AP Payment Payload Tests")
    class APPaymentPayloadTests {

        @Test
        @DisplayName("should build AP payment payload with correct entity type")
        void buildAPPaymentPayload_withFullData() {
            APPayment payment = createTestAPPayment();
            List<PaymentAllocation> allocations = createTestPaymentAllocations(payment.getId());
            String supplierName = "NCC Thiết bị";
            String bankAccountName = "VCB - 987654321";

            EntityEmbeddingPayload payload = textSynthesizer.buildAPPaymentPayload(
                payment, allocations, supplierName, bankAccountName, EmbeddingAction.UPSERT);

            assertThat(payload.entityType()).isEqualTo(EntityType.AP_PAYMENT);
            assertThat(payload.entityId()).isEqualTo(payment.getId().toString());
            assertThat(payload.companyId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should synthesize Vietnamese text for payment")
        void buildAPPaymentPayload_textContainsVietnameseFormat() {
            APPayment payment = createTestAPPayment();
            String supplierName = "NCC Thiết bị";
            String bankAccountName = "VCB - 987654321";

            EntityEmbeddingPayload payload = textSynthesizer.buildAPPaymentPayload(
                payment, List.of(), supplierName, bankAccountName, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Phiếu chi số PM2024-001");
            assertThat(text).contains("Nhà cung cấp: NCC Thiết bị");
            assertThat(text).contains("Người nhận: Trần Văn B");
            assertThat(text).contains("Trạng thái: Nháp");
            assertThat(text).contains("Phương thức: Chuyển khoản");
            assertThat(text).contains("Tài khoản: VCB - 987654321");
        }

        @Test
        @DisplayName("should include payment allocations to bills")
        void buildAPPaymentPayload_textContainsAllocations() {
            APPayment payment = createTestAPPayment();
            List<PaymentAllocation> allocations = createTestPaymentAllocations(payment.getId());

            EntityEmbeddingPayload payload = textSynthesizer.buildAPPaymentPayload(
                payment, allocations, "Supplier", null, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Phân bổ cho hóa đơn mua:");
        }

        @Test
        @DisplayName("should indicate standalone payment")
        void buildAPPaymentPayload_standalonePayment() {
            APPayment payment = createTestAPPayment();
            payment.setIsStandalone(true);

            EntityEmbeddingPayload payload = textSynthesizer.buildAPPaymentPayload(
                payment, List.of(), "Supplier", null, EmbeddingAction.UPSERT);

            String text = payload.text();
            assertThat(text).contains("Thanh toán trước (chưa gắn hóa đơn)");
        }

        @Test
        @DisplayName("should build metadata with supplier info")
        void buildAPPaymentPayload_metadataContainsSupplierInfo() {
            APPayment payment = createTestAPPayment();

            EntityEmbeddingPayload payload = textSynthesizer.buildAPPaymentPayload(
                payment, List.of(), "NCC ABC", null, EmbeddingAction.UPSERT);

            assertThat(payload.metadata()).containsEntry("entityType", "ap_payment");
            assertThat(payload.metadata()).containsEntry("paymentNumber", "PM2024-001");
            assertThat(payload.metadata()).containsEntry("supplierId", 1L);
            assertThat(payload.metadata()).containsEntry("supplierName", "NCC ABC");
            assertThat(payload.metadata()).containsEntry("paymentMethod", "BANK_TRANSFER");
        }

        @Test
        @DisplayName("should handle CASH payment method")
        void buildAPPaymentPayload_withCashPaymentMethod() {
            APPayment payment = createTestAPPayment();
            payment.setPaymentMethod(PaymentMethod.CASH);

            EntityEmbeddingPayload payload = textSynthesizer.buildAPPaymentPayload(
                payment, List.of(), "Supplier", null, EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Phương thức: Tiền mặt");
            assertThat(payload.metadata()).containsEntry("paymentMethod", "CASH");
        }
    }

    @Nested
    @DisplayName("Status Translation Tests")
    class StatusTranslationTests {

        @Test
        @DisplayName("should translate POSTED status correctly")
        void translatePostedStatus() {
            SalesInvoice invoice = createTestSalesInvoice();
            invoice.setStatus(SalesInvoiceStatus.POSTED);

            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(
                invoice, List.of(), "Customer", EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Trạng thái: Đã ghi sổ");
        }

        @Test
        @DisplayName("should translate PAID status correctly")
        void translatePaidStatus() {
            SalesInvoice invoice = createTestSalesInvoice();
            invoice.setStatus(SalesInvoiceStatus.PAID);

            EntityEmbeddingPayload payload = textSynthesizer.buildSalesInvoicePayload(
                invoice, List.of(), "Customer", EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Trạng thái: Đã thanh toán");
        }

        @Test
        @DisplayName("should translate PARTIALLY_PAID status correctly")
        void translatePartiallyPaidStatus() {
            PurchaseBill bill = createTestPurchaseBill();
            bill.setStatus(PurchaseBillStatus.PARTIALLY_PAID);

            EntityEmbeddingPayload payload = textSynthesizer.buildPurchaseBillPayload(
                bill, List.of(), "Supplier", EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Trạng thái: Thanh toán một phần");
        }

        @Test
        @DisplayName("should translate REVERSED receipt status")
        void translateReversedReceiptStatus() {
            ARPayment payment = createTestARPayment();
            payment.setStatus(ReceiptStatus.REVERSED);

            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, List.of(), "Customer", null, EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Trạng thái: Đã hủy bỏ");
        }

        @Test
        @DisplayName("should translate CANCELLED payment status")
        void translateCancelledPaymentStatus() {
            APPayment payment = createTestAPPayment();
            payment.setStatus(PaymentStatus.CANCELLED);

            EntityEmbeddingPayload payload = textSynthesizer.buildAPPaymentPayload(
                payment, List.of(), "Supplier", null, EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Trạng thái: Đã hủy");
        }
    }

    @Nested
    @DisplayName("Payment Method Translation Tests")
    class PaymentMethodTranslationTests {

        @Test
        @DisplayName("should translate CHECK payment method")
        void translateCheckPaymentMethod() {
            ARPayment payment = createTestARPayment();
            payment.setPaymentMethod(PaymentMethod.CHECK);

            EntityEmbeddingPayload payload = textSynthesizer.buildARPaymentPayload(
                payment, List.of(), "Customer", null, EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Phương thức: Séc");
        }

        @Test
        @DisplayName("should translate OTHER payment method")
        void translateOtherPaymentMethod() {
            APPayment payment = createTestAPPayment();
            payment.setPaymentMethod(PaymentMethod.OTHER);

            EntityEmbeddingPayload payload = textSynthesizer.buildAPPaymentPayload(
                payment, List.of(), "Supplier", null, EmbeddingAction.UPSERT);

            assertThat(payload.text()).contains("Phương thức: Khác");
        }
    }

    // ================== Helper Methods ==================

    private SalesInvoice createTestSalesInvoice() {
        SalesInvoice invoice = new SalesInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setCompanyId(1L);
        invoice.setCustomerId(1L);
        invoice.setInvoiceNumber("SI2024-001");
        invoice.setInvoiceDate(LocalDate.of(2024, 12, 28));
        invoice.setDueDate(LocalDate.of(2025, 1, 28));
        invoice.setReference("REF-001");
        invoice.setDescription("Test sales invoice");
        invoice.setStatus(SalesInvoiceStatus.DRAFT);
        invoice.setTotalAmount(BigDecimal.valueOf(10000000));
        invoice.setVatAmount(BigDecimal.valueOf(1000000));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setRemainingBalance(BigDecimal.valueOf(10000000));
        invoice.setCreatedById(1L);
        return invoice;
    }

    private List<SalesInvoiceLine> createTestSalesInvoiceLines(UUID invoiceId) {
        SalesInvoiceLine line = new SalesInvoiceLine();
        line.setId(UUID.randomUUID());
        line.setSalesInvoiceId(invoiceId);
        line.setLineNumber(1);
        line.setAccountId(511L);
        line.setDescription("Sản phẩm test");
        line.setQuantity(BigDecimal.valueOf(10));
        line.setUnitPrice(BigDecimal.valueOf(1000000));
        line.setAmount(BigDecimal.valueOf(10000000));
        line.setVatRate(VatRate.TEN);
        line.setVatAmount(BigDecimal.valueOf(1000000));
        line.setCompanyId(1L);
        return List.of(line);
    }

    private PurchaseBill createTestPurchaseBill() {
        PurchaseBill bill = new PurchaseBill();
        bill.setId(UUID.randomUUID());
        bill.setCompanyId(1L);
        bill.setSupplierId(1L);
        bill.setBillNumber("PB2024-001");
        bill.setBillDate(LocalDate.of(2024, 12, 28));
        bill.setDueDate(LocalDate.of(2025, 1, 28));
        bill.setReference("REF-PB-001");
        bill.setDescription("Test purchase bill");
        bill.setStatus(PurchaseBillStatus.DRAFT);
        bill.setTotalAmount(BigDecimal.valueOf(5000000));
        bill.setVatAmount(BigDecimal.valueOf(500000));
        bill.setAmountPaid(BigDecimal.ZERO);
        bill.setRemainingBalance(BigDecimal.valueOf(5000000));
        bill.setCreatedById(1L);
        return bill;
    }

    private List<PurchaseBillLine> createTestPurchaseBillLines(UUID billId) {
        PurchaseBillLine line = new PurchaseBillLine();
        line.setId(UUID.randomUUID());
        line.setPurchaseBillId(billId);
        line.setLineNumber(1);
        line.setAccountId(621L);
        line.setDescription("Chi phí văn phòng phẩm");
        line.setQuantity(BigDecimal.valueOf(5));
        line.setUnitPrice(BigDecimal.valueOf(1000000));
        line.setAmount(BigDecimal.valueOf(5000000));
        line.setVatRate(VatRate.TEN);
        line.setVatAmount(BigDecimal.valueOf(500000));
        line.setCompanyId(1L);
        return List.of(line);
    }

    private ARPayment createTestARPayment() {
        ARPayment payment = new ARPayment();
        payment.setId(UUID.randomUUID());
        payment.setCompanyId(1L);
        payment.setCustomerId(1L);
        payment.setReceiptNumber("RC2024-001");
        payment.setReceiptDate(LocalDate.of(2024, 12, 28));
        payment.setPayee("Nguyễn Văn A");
        payment.setAmount(BigDecimal.valueOf(10000000));
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(ReceiptStatus.DRAFT);
        payment.setIsStandalone(false);
        payment.setCreatedById(1L);
        return payment;
    }

    private List<ReceiptAllocation> createTestReceiptAllocations(UUID receiptId) {
        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.setId(UUID.randomUUID());
        allocation.setCompanyId(1L);
        allocation.setReceiptId(receiptId);
        allocation.setSalesInvoiceId(UUID.randomUUID());
        allocation.setAllocatedAmount(BigDecimal.valueOf(5000000));
        allocation.setAllocationOrder(1);
        return List.of(allocation);
    }

    private APPayment createTestAPPayment() {
        APPayment payment = new APPayment();
        payment.setId(UUID.randomUUID());
        payment.setCompanyId(1L);
        payment.setSupplierId(1L);
        payment.setPaymentNumber("PM2024-001");
        payment.setPaymentDate(LocalDate.of(2024, 12, 28));
        payment.setPayee("Trần Văn B");
        payment.setAmount(BigDecimal.valueOf(5000000));
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.DRAFT);
        payment.setIsStandalone(false);
        payment.setCreatedById(1L);
        return payment;
    }

    private List<PaymentAllocation> createTestPaymentAllocations(UUID paymentId) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setId(UUID.randomUUID());
        allocation.setCompanyId(1L);
        allocation.setPaymentId(paymentId);
        allocation.setPurchaseBillId(UUID.randomUUID());
        allocation.setAllocatedAmount(BigDecimal.valueOf(2500000));
        allocation.setAllocationOrder(1);
        return List.of(allocation);
    }
}

package com.accounting.service.impl;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.dto.embedding.EntityEmbeddingPayload;
import com.accounting.dto.embedding.EntityType;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.Supplier;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.service.EntityTextSynthesizer;

/**
 * Implementation of EntityTextSynthesizer with Vietnamese text templates.
 * Generates human-readable, searchable text optimized for RAG queries.
 */
@Service
public class EntityTextSynthesizerImpl implements EntityTextSynthesizer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ChartOfAccountsRepository chartOfAccountsRepository;

    public EntityTextSynthesizerImpl(ChartOfAccountsRepository chartOfAccountsRepository) {
        this.chartOfAccountsRepository = chartOfAccountsRepository;
    }

    @Override
    public EntityEmbeddingPayload buildVoucherPayload(Voucher voucher, List<VoucherLine> lines, EmbeddingAction action) {
        String text = synthesizeVoucherText(voucher, lines);
        Map<String, Object> metadata = buildVoucherMetadata(voucher, lines);

        return EntityEmbeddingPayload.builder()
            .entityType(EntityType.VOUCHER)
            .entityId(voucher.getId().toString())
            .companyId(voucher.getCompanyId())
            .action(action)
            .text(text)
            .metadata(metadata)
            .build();
    }

    @Override
    public EntityEmbeddingPayload buildCustomerPayload(Customer customer, EmbeddingAction action) {
        String text = synthesizeCustomerText(customer);
        Map<String, Object> metadata = buildCustomerMetadata(customer);

        return EntityEmbeddingPayload.builder()
            .entityType(EntityType.CUSTOMER)
            .entityId(customer.getId().toString())
            .companyId(customer.getCompanyId())
            .action(action)
            .text(text)
            .metadata(metadata)
            .build();
    }

    @Override
    public EntityEmbeddingPayload buildSupplierPayload(Supplier supplier, EmbeddingAction action) {
        String text = synthesizeSupplierText(supplier);
        Map<String, Object> metadata = buildSupplierMetadata(supplier);

        return EntityEmbeddingPayload.builder()
            .entityType(EntityType.SUPPLIER)
            .entityId(supplier.getId().toString())
            .companyId(supplier.getCompanyId())
            .action(action)
            .text(text)
            .metadata(metadata)
            .build();
    }

    @Override
    public EntityEmbeddingPayload buildChartOfAccountPayload(ChartOfAccount account, String parentCode, EmbeddingAction action) {
        String text = synthesizeChartOfAccountText(account, parentCode);
        Map<String, Object> metadata = buildChartOfAccountMetadata(account, parentCode);

        return EntityEmbeddingPayload.builder()
            .entityType(EntityType.CHART_OF_ACCOUNTS)
            .entityId(account.getId().toString())
            .companyId(account.getCompanyId())
            .action(action)
            .text(text)
            .metadata(metadata)
            .build();
    }

    @Override
    public EntityEmbeddingPayload buildGenericPayload(
            EntityType entityType,
            String entityId,
            Long companyId,
            String text,
            Map<String, Object> metadata,
            EmbeddingAction action) {

        return EntityEmbeddingPayload.builder()
            .entityType(entityType)
            .entityId(entityId)
            .companyId(companyId)
            .action(action)
            .text(text)
            .metadata(metadata)
            .build();
    }

    // ================== Text Synthesis Methods ==================

    /**
     * Synthesize Vietnamese text for voucher embedding.
     * Format optimized for queries like:
     * - "Bút toán nào liên quan TK 131?"
     * - "Chứng từ PC-2024-001 có nội dung gì?"
     */
    private String synthesizeVoucherText(Voucher voucher, List<VoucherLine> lines) {
        StringBuilder sb = new StringBuilder();

        sb.append("Chứng từ ghi sổ số ").append(voucher.getVoucherNumber());
        sb.append(" ngày ").append(voucher.getVoucherDate().format(DATE_FORMAT));
        sb.append(".\n");

        sb.append("Nội dung: ").append(voucher.getDescription()).append("\n");
        sb.append("Trạng thái: ").append(translateStatus(voucher.getStatus())).append("\n");

        if (!lines.isEmpty()) {
            sb.append("Bút toán:\n");
            for (VoucherLine line : lines) {
                String accountCode = getAccountCode(line.getAccountId());
                String accountName = getAccountName(line.getAccountId());

                if (line.getDebit() != null && line.getDebit().compareTo(BigDecimal.ZERO) > 0) {
                    sb.append("  - Nợ TK ").append(accountCode).append(" (").append(accountName).append("): ");
                    sb.append(formatAmount(line.getDebit())).append("\n");
                }
                if (line.getCredit() != null && line.getCredit().compareTo(BigDecimal.ZERO) > 0) {
                    sb.append("  - Có TK ").append(accountCode).append(" (").append(accountName).append("): ");
                    sb.append(formatAmount(line.getCredit())).append("\n");
                }
                if (line.getDescription() != null && !line.getDescription().isBlank()) {
                    sb.append("    Diễn giải: ").append(line.getDescription()).append("\n");
                }
            }
        }

        sb.append("Tổng cộng: Nợ ").append(formatAmount(voucher.getTotalDebit()));
        sb.append(" - Có ").append(formatAmount(voucher.getTotalCredit()));

        return sb.toString();
    }

    /**
     * Synthesize Vietnamese text for customer embedding.
     * Format optimized for queries like:
     * - "Thông tin khách hàng ABC?"
     * - "Khách hàng nào có MST 0123456789?"
     */
    private String synthesizeCustomerText(Customer customer) {
        StringBuilder sb = new StringBuilder();

        sb.append("Khách hàng: ").append(customer.getCode()).append(" - ").append(customer.getName()).append("\n");

        if (customer.getTaxCode() != null && !customer.getTaxCode().isBlank()) {
            sb.append("Mã số thuế: ").append(customer.getTaxCode()).append("\n");
        }
        if (customer.getAddress() != null && !customer.getAddress().isBlank()) {
            sb.append("Địa chỉ: ").append(customer.getAddress()).append("\n");
        }
        if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
            sb.append("Điện thoại: ").append(customer.getPhone()).append("\n");
        }
        if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            sb.append("Email: ").append(customer.getEmail()).append("\n");
        }

        sb.append("Trạng thái: ").append(Boolean.TRUE.equals(customer.getActive()) ? "Đang hoạt động" : "Ngừng hoạt động");

        return sb.toString();
    }

    /**
     * Synthesize Vietnamese text for supplier embedding.
     * Format optimized for queries like:
     * - "Thông tin nhà cung cấp XYZ?"
     * - "NCC nào có MST 9876543210?"
     */
    private String synthesizeSupplierText(Supplier supplier) {
        StringBuilder sb = new StringBuilder();

        sb.append("Nhà cung cấp: ").append(supplier.getCode()).append(" - ").append(supplier.getName()).append("\n");

        if (supplier.getTaxCode() != null && !supplier.getTaxCode().isBlank()) {
            sb.append("Mã số thuế: ").append(supplier.getTaxCode()).append("\n");
        }
        if (supplier.getAddress() != null && !supplier.getAddress().isBlank()) {
            sb.append("Địa chỉ: ").append(supplier.getAddress()).append("\n");
        }
        if (supplier.getPhone() != null && !supplier.getPhone().isBlank()) {
            sb.append("Điện thoại: ").append(supplier.getPhone()).append("\n");
        }
        if (supplier.getEmail() != null && !supplier.getEmail().isBlank()) {
            sb.append("Email: ").append(supplier.getEmail()).append("\n");
        }

        sb.append("Trạng thái: ").append(Boolean.TRUE.equals(supplier.getActive()) ? "Đang hoạt động" : "Ngừng hoạt động");

        return sb.toString();
    }

    /**
     * Synthesize Vietnamese text for chart of account embedding.
     * Format optimized for queries like:
     * - "TK 131 dùng cho nghiệp vụ nào?"
     * - "Tài khoản nào ghi nhận chi phí bán hàng?"
     */
    private String synthesizeChartOfAccountText(ChartOfAccount account, String parentCode) {
        StringBuilder sb = new StringBuilder();

        sb.append("Tài khoản ").append(account.getCode()).append(" - ").append(account.getName()).append("\n");

        if (account.getNameEnglish() != null && !account.getNameEnglish().isBlank()) {
            sb.append("Tên tiếng Anh: ").append(account.getNameEnglish()).append("\n");
        }

        sb.append("Loại tài khoản: ").append(translateAccountType(account.getType())).append("\n");
        sb.append("Chiều ghi nhận: ").append(translateNormalSide(account.getNormalSide())).append("\n");

        if (parentCode != null && !parentCode.isBlank()) {
            sb.append("Tài khoản cha: TK ").append(parentCode).append("\n");
        }

        sb.append("Có thể hạch toán: ").append(Boolean.TRUE.equals(account.getPostable()) ? "Có" : "Không").append("\n");

        if (account.getDescription() != null && !account.getDescription().isBlank()) {
            sb.append("Mô tả: ").append(account.getDescription());
        }

        return sb.toString();
    }

    // ================== Metadata Builders ==================

    private Map<String, Object> buildVoucherMetadata(Voucher voucher, List<VoucherLine> lines) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", EntityType.VOUCHER.getCode());
        metadata.put("voucherNumber", voucher.getVoucherNumber());
        metadata.put("voucherDate", voucher.getVoucherDate().toString());
        metadata.put("status", voucher.getStatus());
        metadata.put("currency", voucher.getCurrency());
        metadata.put("totalDebit", voucher.getTotalDebit().doubleValue());
        metadata.put("totalCredit", voucher.getTotalCredit().doubleValue());

        // Include account codes for filtering
        List<String> accountCodes = lines.stream()
            .map(line -> getAccountCode(line.getAccountId()))
            .distinct()
            .toList();
        metadata.put("accountCodes", String.join(",", accountCodes));

        return metadata;
    }

    private Map<String, Object> buildCustomerMetadata(Customer customer) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", EntityType.CUSTOMER.getCode());
        metadata.put("customerCode", customer.getCode());
        metadata.put("customerName", customer.getName());
        metadata.put("taxCode", customer.getTaxCode() != null ? customer.getTaxCode() : "");
        metadata.put("active", customer.getActive());
        return metadata;
    }

    private Map<String, Object> buildSupplierMetadata(Supplier supplier) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", EntityType.SUPPLIER.getCode());
        metadata.put("supplierCode", supplier.getCode());
        metadata.put("supplierName", supplier.getName());
        metadata.put("taxCode", supplier.getTaxCode() != null ? supplier.getTaxCode() : "");
        metadata.put("active", supplier.getActive());
        return metadata;
    }

    private Map<String, Object> buildChartOfAccountMetadata(ChartOfAccount account, String parentCode) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", EntityType.CHART_OF_ACCOUNTS.getCode());
        metadata.put("accountCode", account.getCode());
        metadata.put("accountName", account.getName());
        metadata.put("accountType", account.getType());
        metadata.put("normalSide", account.getNormalSide());
        metadata.put("postable", account.getPostable());
        if (parentCode != null) {
            metadata.put("parentCode", parentCode);
        }
        return metadata;
    }

    // ================== Helper Methods ==================

    private String getAccountCode(Long accountId) {
        if (accountId == null) {
            return "UNKNOWN";
        }
        return chartOfAccountsRepository.findById(accountId)
            .map(ChartOfAccount::getCode)
            .orElse("ACC-" + accountId);
    }

    private String getAccountName(Long accountId) {
        if (accountId == null) {
            return "Không xác định";
        }
        return chartOfAccountsRepository.findById(accountId)
            .map(ChartOfAccount::getName)
            .orElse("Tài khoản " + accountId);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return String.format("%,.0f VND", amount);
    }

    private String translateStatus(String status) {
        return switch (status) {
            case "draft" -> "Nháp";
            case "posted" -> "Đã ghi sổ";
            case "unposted" -> "Chưa ghi sổ";
            default -> status;
        };
    }

    private String translateAccountType(String type) {
        return switch (type.toLowerCase()) {
            case "asset" -> "Tài sản";
            case "liability" -> "Nợ phải trả";
            case "equity" -> "Vốn chủ sở hữu";
            case "revenue" -> "Doanh thu";
            case "expense" -> "Chi phí";
            default -> type;
        };
    }

    private String translateNormalSide(String normalSide) {
        return switch (normalSide.toLowerCase()) {
            case "debit" -> "Bên Nợ";
            case "credit" -> "Bên Có";
            default -> normalSide;
        };
    }

    // ================== Transaction Entity Methods ==================

    @Override
    public EntityEmbeddingPayload buildSalesInvoicePayload(
            com.accounting.entity.SalesInvoice invoice,
            List<com.accounting.entity.SalesInvoiceLine> lines,
            String customerName,
            EmbeddingAction action) {

        String text = synthesizeSalesInvoiceText(invoice, lines, customerName);
        Map<String, Object> metadata = buildSalesInvoiceMetadata(invoice, customerName);

        return EntityEmbeddingPayload.builder()
            .entityType(EntityType.SALES_INVOICE)
            .entityId(invoice.getId().toString())
            .companyId(invoice.getCompanyId())
            .action(action)
            .text(text)
            .metadata(metadata)
            .build();
    }

    @Override
    public EntityEmbeddingPayload buildPurchaseBillPayload(
            com.accounting.entity.PurchaseBill bill,
            List<com.accounting.entity.PurchaseBillLine> lines,
            String supplierName,
            EmbeddingAction action) {

        String text = synthesizePurchaseBillText(bill, lines, supplierName);
        Map<String, Object> metadata = buildPurchaseBillMetadata(bill, supplierName);

        return EntityEmbeddingPayload.builder()
            .entityType(EntityType.PURCHASE_INVOICE)
            .entityId(bill.getId().toString())
            .companyId(bill.getCompanyId())
            .action(action)
            .text(text)
            .metadata(metadata)
            .build();
    }

    @Override
    public EntityEmbeddingPayload buildARPaymentPayload(
            com.accounting.entity.ARPayment payment,
            List<com.accounting.entity.ReceiptAllocation> allocations,
            String customerName,
            String bankAccountName,
            EmbeddingAction action) {

        String text = synthesizeARPaymentText(payment, allocations, customerName, bankAccountName);
        Map<String, Object> metadata = buildARPaymentMetadata(payment, customerName);

        return EntityEmbeddingPayload.builder()
            .entityType(EntityType.AR_PAYMENT)
            .entityId(payment.getId().toString())
            .companyId(payment.getCompanyId())
            .action(action)
            .text(text)
            .metadata(metadata)
            .build();
    }

    @Override
    public EntityEmbeddingPayload buildAPPaymentPayload(
            com.accounting.entity.APPayment payment,
            List<com.accounting.entity.PaymentAllocation> allocations,
            String supplierName,
            String bankAccountName,
            EmbeddingAction action) {

        String text = synthesizeAPPaymentText(payment, allocations, supplierName, bankAccountName);
        Map<String, Object> metadata = buildAPPaymentMetadata(payment, supplierName);

        return EntityEmbeddingPayload.builder()
            .entityType(EntityType.AP_PAYMENT)
            .entityId(payment.getId().toString())
            .companyId(payment.getCompanyId())
            .action(action)
            .text(text)
            .metadata(metadata)
            .build();
    }

    // ================== Transaction Text Synthesis ==================

    /**
     * Synthesize Vietnamese text for sales invoice embedding.
     * Format optimized for queries like:
     * - "Hóa đơn bán hàng cho khách hàng ABC?"
     * - "Hóa đơn nào có giá trị lớn nhất tháng 12?"
     */
    private String synthesizeSalesInvoiceText(
            com.accounting.entity.SalesInvoice invoice,
            List<com.accounting.entity.SalesInvoiceLine> lines,
            String customerName) {

        StringBuilder sb = new StringBuilder();

        sb.append("Hóa đơn bán hàng số ").append(invoice.getInvoiceNumber());
        sb.append(" ngày ").append(invoice.getInvoiceDate().format(DATE_FORMAT));
        sb.append(".\n");

        sb.append("Khách hàng: ").append(customerName).append("\n");
        sb.append("Trạng thái: ").append(translateInvoiceStatus(invoice.getStatus())).append("\n");

        if (invoice.getDescription() != null && !invoice.getDescription().isBlank()) {
            sb.append("Nội dung: ").append(invoice.getDescription()).append("\n");
        }

        if (!lines.isEmpty()) {
            sb.append("Chi tiết:\n");
            for (com.accounting.entity.SalesInvoiceLine line : lines) {
                sb.append("  - ").append(line.getDescription());
                sb.append(": SL ").append(line.getQuantity());
                sb.append(" x ").append(formatAmount(line.getUnitPrice()));
                sb.append(" = ").append(formatAmount(line.getAmount()));
                if (line.getVatAmount() != null && line.getVatAmount().compareTo(BigDecimal.ZERO) > 0) {
                    sb.append(" (VAT: ").append(formatAmount(line.getVatAmount())).append(")");
                }
                sb.append("\n");
            }
        }

        sb.append("Tổng tiền hàng: ").append(formatAmount(invoice.getTotalAmount())).append("\n");
        sb.append("Thuế VAT: ").append(formatAmount(invoice.getVatAmount())).append("\n");
        sb.append("Đã thanh toán: ").append(formatAmount(invoice.getAmountPaid())).append("\n");
        sb.append("Còn lại: ").append(formatAmount(invoice.getRemainingBalance()));

        return sb.toString();
    }

    /**
     * Synthesize Vietnamese text for purchase bill embedding.
     * Format optimized for queries like:
     * - "Hóa đơn mua hàng từ NCC XYZ?"
     * - "Bill nào đến hạn thanh toán tuần này?"
     */
    private String synthesizePurchaseBillText(
            com.accounting.entity.PurchaseBill bill,
            List<com.accounting.entity.PurchaseBillLine> lines,
            String supplierName) {

        StringBuilder sb = new StringBuilder();

        sb.append("Hóa đơn mua hàng số ").append(bill.getBillNumber());
        sb.append(" ngày ").append(bill.getBillDate().format(DATE_FORMAT));
        sb.append(".\n");

        sb.append("Nhà cung cấp: ").append(supplierName).append("\n");
        sb.append("Trạng thái: ").append(translateBillStatus(bill.getStatus())).append("\n");
        sb.append("Hạn thanh toán: ").append(bill.getDueDate().format(DATE_FORMAT)).append("\n");

        if (bill.getDescription() != null && !bill.getDescription().isBlank()) {
            sb.append("Nội dung: ").append(bill.getDescription()).append("\n");
        }

        if (!lines.isEmpty()) {
            sb.append("Chi tiết:\n");
            for (com.accounting.entity.PurchaseBillLine line : lines) {
                sb.append("  - ").append(line.getDescription());
                sb.append(": SL ").append(line.getQuantity());
                sb.append(" x ").append(formatAmount(line.getUnitPrice()));
                sb.append(" = ").append(formatAmount(line.getAmount()));
                if (line.getVatAmount() != null && line.getVatAmount().compareTo(BigDecimal.ZERO) > 0) {
                    sb.append(" (VAT: ").append(formatAmount(line.getVatAmount())).append(")");
                }
                sb.append("\n");
            }
        }

        sb.append("Tổng tiền hàng: ").append(formatAmount(bill.getTotalAmount())).append("\n");
        sb.append("Thuế VAT: ").append(formatAmount(bill.getVatAmount())).append("\n");
        sb.append("Đã thanh toán: ").append(formatAmount(bill.getAmountPaid())).append("\n");
        sb.append("Còn lại: ").append(formatAmount(bill.getRemainingBalance()));

        return sb.toString();
    }

    /**
     * Synthesize Vietnamese text for AR payment (receipt) embedding.
     * Format optimized for queries like:
     * - "Phiếu thu từ khách hàng ABC?"
     * - "Khách hàng nào thanh toán hôm nay?"
     */
    private String synthesizeARPaymentText(
            com.accounting.entity.ARPayment payment,
            List<com.accounting.entity.ReceiptAllocation> allocations,
            String customerName,
            String bankAccountName) {

        StringBuilder sb = new StringBuilder();

        sb.append("Phiếu thu số ").append(payment.getReceiptNumber());
        sb.append(" ngày ").append(payment.getReceiptDate().format(DATE_FORMAT));
        sb.append(".\n");

        sb.append("Khách hàng: ").append(customerName).append("\n");
        sb.append("Người nộp: ").append(payment.getPayee()).append("\n");
        sb.append("Trạng thái: ").append(translateReceiptStatus(payment.getStatus())).append("\n");
        sb.append("Phương thức: ").append(translatePaymentMethod(payment.getPaymentMethod())).append("\n");

        if (bankAccountName != null && !bankAccountName.isBlank()) {
            sb.append("Tài khoản: ").append(bankAccountName).append("\n");
        }

        sb.append("Số tiền: ").append(formatAmount(payment.getAmount())).append("\n");

        if (payment.getReference() != null && !payment.getReference().isBlank()) {
            sb.append("Tham chiếu: ").append(payment.getReference()).append("\n");
        }

        if (!allocations.isEmpty()) {
            sb.append("Phân bổ cho hóa đơn:\n");
            for (com.accounting.entity.ReceiptAllocation alloc : allocations) {
                sb.append("  - ").append(formatAmount(alloc.getAllocatedAmount())).append("\n");
            }
        } else if (Boolean.TRUE.equals(payment.getIsStandalone())) {
            sb.append("Loại: Thanh toán trước (chưa gắn hóa đơn)\n");
        }

        return sb.toString();
    }

    /**
     * Synthesize Vietnamese text for AP payment embedding.
     * Format optimized for queries like:
     * - "Phiếu chi cho NCC XYZ?"
     * - "Thanh toán nào được duyệt tuần này?"
     */
    private String synthesizeAPPaymentText(
            com.accounting.entity.APPayment payment,
            List<com.accounting.entity.PaymentAllocation> allocations,
            String supplierName,
            String bankAccountName) {

        StringBuilder sb = new StringBuilder();

        sb.append("Phiếu chi số ").append(payment.getPaymentNumber());
        sb.append(" ngày ").append(payment.getPaymentDate().format(DATE_FORMAT));
        sb.append(".\n");

        sb.append("Nhà cung cấp: ").append(supplierName).append("\n");
        sb.append("Người nhận: ").append(payment.getPayee()).append("\n");
        sb.append("Trạng thái: ").append(translatePaymentStatus(payment.getStatus())).append("\n");
        sb.append("Phương thức: ").append(translatePaymentMethod(payment.getPaymentMethod())).append("\n");

        if (bankAccountName != null && !bankAccountName.isBlank()) {
            sb.append("Tài khoản: ").append(bankAccountName).append("\n");
        }

        sb.append("Số tiền: ").append(formatAmount(payment.getAmount())).append("\n");

        if (payment.getReference() != null && !payment.getReference().isBlank()) {
            sb.append("Tham chiếu: ").append(payment.getReference()).append("\n");
        }

        if (!allocations.isEmpty()) {
            sb.append("Phân bổ cho hóa đơn mua:\n");
            for (com.accounting.entity.PaymentAllocation alloc : allocations) {
                sb.append("  - ").append(formatAmount(alloc.getAllocatedAmount())).append("\n");
            }
        } else if (Boolean.TRUE.equals(payment.getIsStandalone())) {
            sb.append("Loại: Thanh toán trước (chưa gắn hóa đơn)\n");
        }

        return sb.toString();
    }

    // ================== Transaction Metadata Builders ==================

    private Map<String, Object> buildSalesInvoiceMetadata(
            com.accounting.entity.SalesInvoice invoice, String customerName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", EntityType.SALES_INVOICE.getCode());
        metadata.put("invoiceNumber", invoice.getInvoiceNumber());
        metadata.put("invoiceDate", invoice.getInvoiceDate().toString());
        metadata.put("dueDate", invoice.getDueDate().toString());
        metadata.put("customerId", invoice.getCustomerId());
        metadata.put("customerName", customerName != null ? customerName : "");
        metadata.put("status", invoice.getStatus().name());
        metadata.put("totalAmount", invoice.getTotalAmount().doubleValue());
        metadata.put("vatAmount", invoice.getVatAmount().doubleValue());
        metadata.put("amountPaid", invoice.getAmountPaid().doubleValue());
        metadata.put("remainingBalance", invoice.getRemainingBalance().doubleValue());
        return metadata;
    }

    private Map<String, Object> buildPurchaseBillMetadata(
            com.accounting.entity.PurchaseBill bill, String supplierName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", EntityType.PURCHASE_INVOICE.getCode());
        metadata.put("billNumber", bill.getBillNumber());
        metadata.put("billDate", bill.getBillDate().toString());
        metadata.put("dueDate", bill.getDueDate().toString());
        metadata.put("supplierId", bill.getSupplierId());
        metadata.put("supplierName", supplierName != null ? supplierName : "");
        metadata.put("status", bill.getStatus().name());
        metadata.put("totalAmount", bill.getTotalAmount().doubleValue());
        metadata.put("vatAmount", bill.getVatAmount().doubleValue());
        metadata.put("amountPaid", bill.getAmountPaid().doubleValue());
        metadata.put("remainingBalance", bill.getRemainingBalance().doubleValue());
        return metadata;
    }

    private Map<String, Object> buildARPaymentMetadata(
            com.accounting.entity.ARPayment payment, String customerName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", EntityType.AR_PAYMENT.getCode());
        metadata.put("receiptNumber", payment.getReceiptNumber());
        metadata.put("receiptDate", payment.getReceiptDate().toString());
        metadata.put("customerId", payment.getCustomerId());
        metadata.put("customerName", customerName != null ? customerName : "");
        metadata.put("status", payment.getStatus().name());
        metadata.put("paymentMethod", payment.getPaymentMethod().name());
        metadata.put("amount", payment.getAmount().doubleValue());
        metadata.put("isStandalone", payment.getIsStandalone());
        return metadata;
    }

    private Map<String, Object> buildAPPaymentMetadata(
            com.accounting.entity.APPayment payment, String supplierName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entityType", EntityType.AP_PAYMENT.getCode());
        metadata.put("paymentNumber", payment.getPaymentNumber());
        metadata.put("paymentDate", payment.getPaymentDate().toString());
        metadata.put("supplierId", payment.getSupplierId());
        metadata.put("supplierName", supplierName != null ? supplierName : "");
        metadata.put("status", payment.getStatus().name());
        metadata.put("paymentMethod", payment.getPaymentMethod().name());
        metadata.put("amount", payment.getAmount().doubleValue());
        metadata.put("isStandalone", payment.getIsStandalone());
        return metadata;
    }

    // ================== Transaction Status Translators ==================

    private String translateInvoiceStatus(com.accounting.entity.SalesInvoiceStatus status) {
        return switch (status) {
            case DRAFT -> "Nháp";
            case PENDING_APPROVAL -> "Chờ duyệt";
            case POSTED -> "Đã ghi sổ";
            case REJECTED -> "Từ chối";
            case PAID -> "Đã thanh toán";
            case PARTIALLY_PAID -> "Thanh toán một phần";
        };
    }

    private String translateBillStatus(com.accounting.entity.PurchaseBillStatus status) {
        return switch (status) {
            case DRAFT -> "Nháp";
            case PENDING_APPROVAL -> "Chờ duyệt";
            case POSTED -> "Đã ghi sổ";
            case REJECTED -> "Từ chối";
            case PAID -> "Đã thanh toán";
            case PARTIALLY_PAID -> "Thanh toán một phần";
        };
    }

    private String translateReceiptStatus(com.accounting.entity.ReceiptStatus status) {
        return switch (status) {
            case DRAFT -> "Nháp";
            case PENDING_APPROVAL -> "Chờ duyệt";
            case POSTED -> "Đã ghi sổ";
            case REVERSED -> "Đã hủy bỏ";
        };
    }

    private String translatePaymentStatus(com.accounting.entity.PaymentStatus status) {
        return switch (status) {
            case DRAFT -> "Nháp";
            case PENDING_APPROVAL -> "Chờ duyệt";
            case POSTED -> "Đã ghi sổ";
            case CANCELLED -> "Đã hủy";
            case REJECTED -> "Từ chối";
            case REVERSED -> "Đã hủy bỏ";
        };
    }

    private String translatePaymentMethod(com.accounting.entity.PaymentMethod method) {
        return switch (method) {
            case CASH -> "Tiền mặt";
            case BANK_TRANSFER -> "Chuyển khoản";
            case CHECK -> "Séc";
            case OTHER -> "Khác";
        };
    }
}

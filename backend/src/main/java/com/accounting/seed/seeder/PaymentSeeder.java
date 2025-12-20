package com.accounting.seed.seeder;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.APPayment;
import com.accounting.entity.ARPayment;
import com.accounting.entity.PaymentMethod;
import com.accounting.entity.PaymentStatus;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.ReceiptStatus;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.seed.MoneyHelper;
import com.accounting.seed.SeedProperties;
import com.accounting.seed.TenantContext;
import com.accounting.seed.UniqueGenerator;
import com.accounting.seed.VietnameseFaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentSeeder {

    private final JdbcTemplate jdbcTemplate;
    private final SeedProperties seedProperties;

    private static final String INSERT_AP_PAYMENT_SQL =
            "INSERT INTO ap_payments (id, company_id, supplier_id, payment_number, payment_date, due_date, "
                    + "cash_account_id, bank_account_id, payee, amount, reference, payment_method, "
                    + "payment_proof_url, is_standalone, status, created_by_id, approved_by_id, "
                    + "linked_voucher_id, created_at, updated_at, posted_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_AR_PAYMENT_SQL =
            "INSERT INTO ar_payments (id, company_id, customer_id, receipt_number, receipt_date, "
                    + "cash_account_id, bank_account_id, payee, amount, reference, payment_method, "
                    + "receipt_proof_url, is_standalone, status, created_by_id, posted_by_id, "
                    + "linked_voucher_id, reversal_reason, original_receipt_id, reversing_receipt_id, "
                    + "created_at, updated_at, posted_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_PAYMENT_ALLOCATION_SQL =
            "INSERT INTO transaction_allocations (id, company_id, transaction_type, transaction_id, "
                    + "document_type, document_id, allocated_amount, allocation_order) "
                    + "VALUES (?, ?, 'PAYMENT', ?, 'PURCHASE_BILL', ?, ?, ?)";

    private static final String INSERT_RECEIPT_ALLOCATION_SQL =
            "INSERT INTO transaction_allocations (id, company_id, transaction_type, transaction_id, "
                    + "document_type, document_id, allocated_amount, allocation_order) "
                    + "VALUES (?, ?, 'RECEIPT', ?, 'SALES_INVOICE', ?, ?, ?)";

    private static final String UPDATE_PURCHASE_BILL_SQL =
            "UPDATE purchase_bills SET amount_paid = ?, remaining_balance = ?, status = ? WHERE id = ?";

    private static final String UPDATE_SALES_INVOICE_SQL =
            "UPDATE sales_invoices SET amount_paid = ?, remaining_balance = ?, status = ? WHERE id = ?";

    @Transactional
    public List<APPayment> seedAPPayments(
            TenantContext ctx,
            Map<UUID, PurchaseBillSeeder.DocumentInfo> billInfo,
            int count,
            VietnameseFaker faker,
            UniqueGenerator generator,
            Random random) {
        validatePreconditions(ctx);
        log.info("Seeding {} AP payments for company ID: {}", count, ctx.getCompanyId());

        LocalDate startDate = seedProperties.getStartDate();
        LocalDate endDate = seedProperties.getEndDate();
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        int year = endDate.getYear();
        Instant now = Instant.now();

        List<APPaymentData> payments = new ArrayList<>(count);
        List<APPayment> result = new ArrayList<>(count);
        List<PaymentAllocationData> allocations = new ArrayList<>();
        Map<UUID, BillUpdateData> billUpdates = new HashMap<>();

        List<UUID> availableBillIds = new ArrayList<>(billInfo.keySet());
        if (availableBillIds.isEmpty()) {
            log.warn("No POSTED bills available for AP payments, skipping");
            return result;
        }

        int actualCount = Math.min(count, availableBillIds.size());

        for (int i = 0; i < actualCount; i++) {
            UUID billId = availableBillIds.get(i);
            PurchaseBillSeeder.DocumentInfo doc = billInfo.get(billId);

            if (doc.remainingBalance().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            UUID paymentId = UUID.randomUUID();
            Long companyId = ctx.getCompanyId();
            Long supplierId = doc.supplierId();
            String paymentNumber = generator.nextPaymentNumber(companyId, year, "PC");
            
            // Payment date must be >= bill date
            LocalDate billDate = doc.billDate();
            long daysFromBillToEnd = ChronoUnit.DAYS.between(billDate, endDate);
            LocalDate paymentDate = daysFromBillToEnd > 0 
                    ? billDate.plusDays(random.nextLong(daysFromBillToEnd + 1))
                    : billDate;
            LocalDate dueDate = paymentDate.plusDays(random.nextInt(31));
            PaymentMethod paymentMethod = randomAPPaymentMethod(random);
            Long cashAccountId = null;
            Long bankAccountId = null;
            if (paymentMethod == PaymentMethod.CASH) {
                cashAccountId = randomElement(ctx.getCashBankAccountIds(), random);
            } else {
                bankAccountId = randomElement(ctx.getBankAccountIds(), random);
            }
            String payee = faker.companyName();

            double payPercent = 0.30 + random.nextDouble() * 0.70;
            BigDecimal paymentAmount =
                    MoneyHelper.roundVnd(doc.remainingBalance().multiply(BigDecimal.valueOf(payPercent)));
            if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                paymentAmount = doc.remainingBalance();
            }

            String reference = "Thanh toán NCC " + String.format("%06d", random.nextInt(1_000_000));
            PaymentStatus status = PaymentStatus.POSTED;
            Long createdById = randomElement(ctx.getUserIds(), random);
            Long approvedById = randomOther(ctx.getUserIds(), createdById, random);
            Instant postedAt = paymentDate.atStartOfDay().toInstant(ZoneOffset.UTC);

            APPaymentData data = new APPaymentData(
                    paymentId,
                    companyId,
                    supplierId,
                    paymentNumber,
                    paymentDate,
                    dueDate,
                    cashAccountId,
                    bankAccountId,
                    payee,
                    paymentAmount,
                    reference,
                    paymentMethod,
                    false,
                    status,
                    createdById,
                    approvedById,
                    postedAt,
                    now);

            payments.add(data);

            allocations.add(new PaymentAllocationData(
                    UUID.randomUUID(), companyId, paymentId, billId, paymentAmount, 1));

            BigDecimal newAmountPaid = paymentAmount;
            BigDecimal newRemainingBalance = doc.totalAmount().subtract(newAmountPaid);
            PurchaseBillStatus newStatus;
            if (newRemainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
                newStatus = PurchaseBillStatus.PAID;
                newRemainingBalance = BigDecimal.ZERO;
            } else {
                newStatus = PurchaseBillStatus.PARTIALLY_PAID;
            }
            billUpdates.put(billId, new BillUpdateData(billId, newAmountPaid, newRemainingBalance, newStatus));

            APPayment entity = new APPayment();
            entity.setId(paymentId);
            entity.setCompanyId(companyId);
            entity.setSupplierId(supplierId);
            entity.setPaymentNumber(paymentNumber);
            entity.setPaymentDate(paymentDate);
            entity.setDueDate(dueDate);
            entity.setCashAccountId(cashAccountId);
            entity.setBankAccountId(bankAccountId);
            entity.setPayee(payee);
            entity.setAmount(paymentAmount);
            entity.setReference(reference);
            entity.setPaymentMethod(paymentMethod);
            entity.setIsStandalone(false);
            entity.setStatus(status);
            entity.setCreatedById(createdById);
            entity.setApprovedById(approvedById);
            entity.setPostedAt(postedAt);
            result.add(entity);
        }

        batchInsertAPPayments(payments);
        batchInsertPaymentAllocations(allocations);
        batchUpdatePurchaseBills(new ArrayList<>(billUpdates.values()));

        log.info(
                "Successfully seeded {} AP payments with {} allocations for company ID: {}",
                payments.size(),
                allocations.size(),
                ctx.getCompanyId());
        return result;
    }

    @Transactional
    public List<ARPayment> seedARPayments(
            TenantContext ctx,
            Map<UUID, SalesInvoiceSeeder.DocumentInfo> invoiceInfo,
            int count,
            VietnameseFaker faker,
            UniqueGenerator generator,
            Random random) {
        log.info("Seeding {} AR payments for company ID: {}", count, ctx.getCompanyId());

        LocalDate startDate = seedProperties.getStartDate();
        LocalDate endDate = seedProperties.getEndDate();
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        int year = endDate.getYear();
        Instant now = Instant.now();

        List<ARPaymentData> payments = new ArrayList<>(count);
        List<ARPayment> result = new ArrayList<>(count);
        List<ReceiptAllocationData> allocations = new ArrayList<>();
        Map<UUID, InvoiceUpdateData> invoiceUpdates = new HashMap<>();

        List<UUID> availableInvoiceIds = new ArrayList<>(invoiceInfo.keySet());
        if (availableInvoiceIds.isEmpty()) {
            log.warn("No POSTED invoices available for AR payments, skipping");
            return result;
        }

        int actualCount = Math.min(count, availableInvoiceIds.size());

        for (int i = 0; i < actualCount; i++) {
            UUID invoiceId = availableInvoiceIds.get(i);
            SalesInvoiceSeeder.DocumentInfo doc = invoiceInfo.get(invoiceId);

            if (doc.remainingBalance().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            UUID receiptId = UUID.randomUUID();
            Long companyId = ctx.getCompanyId();
            Long customerId = doc.customerId();
            String receiptNumber = generator.nextPaymentNumber(companyId, year, "PT");
            
            // Receipt date must be >= invoice date
            LocalDate invoiceDate = doc.invoiceDate();
            long daysFromInvoiceToEnd = ChronoUnit.DAYS.between(invoiceDate, endDate);
            LocalDate receiptDate = daysFromInvoiceToEnd > 0
                    ? invoiceDate.plusDays(random.nextLong(daysFromInvoiceToEnd + 1))
                    : invoiceDate;
            PaymentMethod paymentMethod = randomARPaymentMethod(random);
            Long cashAccountId = null;
            Long bankAccountId = null;
            if (paymentMethod == PaymentMethod.CASH) {
                cashAccountId = randomElement(ctx.getCashBankAccountIds(), random);
            } else {
                bankAccountId = randomElement(ctx.getBankAccountIds(), random);
            }
            String payee = faker.personName();

            double payPercent = 0.30 + random.nextDouble() * 0.70;
            BigDecimal receiptAmount =
                    MoneyHelper.roundVnd(doc.remainingBalance().multiply(BigDecimal.valueOf(payPercent)));
            if (receiptAmount.compareTo(BigDecimal.ZERO) <= 0) {
                receiptAmount = doc.remainingBalance();
            }

            String reference = "Thu tiền KH " + String.format("%06d", random.nextInt(1_000_000));
            ReceiptStatus status = ReceiptStatus.POSTED;
            Long createdById = randomElement(ctx.getUserIds(), random);
            Long postedById = randomElement(ctx.getUserIds(), random);
            Instant postedAt = receiptDate.atStartOfDay().toInstant(ZoneOffset.UTC);

            ARPaymentData data = new ARPaymentData(
                    receiptId,
                    companyId,
                    customerId,
                    receiptNumber,
                    receiptDate,
                    cashAccountId,
                    bankAccountId,
                    payee,
                    receiptAmount,
                    reference,
                    paymentMethod,
                    false,
                    status,
                    createdById,
                    postedById,
                    postedAt,
                    now,
                    null);

            payments.add(data);

            allocations.add(new ReceiptAllocationData(
                    UUID.randomUUID(), companyId, receiptId, invoiceId, receiptAmount, 1));

            BigDecimal newAmountPaid = receiptAmount;
            BigDecimal newRemainingBalance = doc.totalAmount().subtract(newAmountPaid);
            SalesInvoiceStatus newStatus;
            if (newRemainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
                newStatus = SalesInvoiceStatus.PAID;
                newRemainingBalance = BigDecimal.ZERO;
            } else {
                newStatus = SalesInvoiceStatus.PARTIALLY_PAID;
            }
            invoiceUpdates.put(
                    invoiceId, new InvoiceUpdateData(invoiceId, newAmountPaid, newRemainingBalance, newStatus));

            ARPayment entity = new ARPayment();
            entity.setId(receiptId);
            entity.setCompanyId(companyId);
            entity.setCustomerId(customerId);
            entity.setReceiptNumber(receiptNumber);
            entity.setReceiptDate(receiptDate);
            entity.setCashAccountId(cashAccountId);
            entity.setBankAccountId(bankAccountId);
            entity.setPayee(payee);
            entity.setAmount(receiptAmount);
            entity.setReference(reference);
            entity.setPaymentMethod(paymentMethod);
            entity.setIsStandalone(false);
            entity.setStatus(status);
            entity.setCreatedById(createdById);
            entity.setPostedById(postedById);
            entity.setPostedAt(postedAt);
            result.add(entity);
        }

        batchInsertARPayments(payments);
        batchInsertReceiptAllocations(allocations);
        batchUpdateSalesInvoices(new ArrayList<>(invoiceUpdates.values()));

        log.info(
                "Successfully seeded {} AR payments with {} allocations for company ID: {}",
                payments.size(),
                allocations.size(),
                ctx.getCompanyId());
        return result;
    }

    private PaymentMethod randomAPPaymentMethod(Random random) {
        int roll = random.nextInt(100);
        if (roll < 30) return PaymentMethod.CASH;
        if (roll < 90) return PaymentMethod.BANK_TRANSFER;
        return PaymentMethod.CHECK;
    }

    private PaymentMethod randomARPaymentMethod(Random random) {
        int roll = random.nextInt(100);
        if (roll < 40) return PaymentMethod.CASH;
        if (roll < 95) return PaymentMethod.BANK_TRANSFER;
        return PaymentMethod.CHECK;
    }

    private <T> T randomElement(List<T> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }

    private Long randomOther(List<Long> list, Long exclude, Random random) {
        if (list.size() <= 1) {
            return list.get(0);
        }
        Long result;
        do {
            result = randomElement(list, random);
        } while (result.equals(exclude));
        return result;
    }

    private void batchInsertAPPayments(List<APPaymentData> payments) {
        if (payments.isEmpty()) return;
        jdbcTemplate.batchUpdate(
                INSERT_AP_PAYMENT_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        APPaymentData p = payments.get(i);
                        ps.setObject(1, p.id);
                        ps.setLong(2, p.companyId);
                        ps.setLong(3, p.supplierId);
                        ps.setString(4, p.paymentNumber);
                        ps.setDate(5, Date.valueOf(p.paymentDate));
                        ps.setDate(6, Date.valueOf(p.dueDate));
                        if (p.cashAccountId != null) {
                            ps.setLong(7, p.cashAccountId);
                        } else {
                            ps.setNull(7, Types.BIGINT);
                        }
                        if (p.bankAccountId != null) {
                            ps.setLong(8, p.bankAccountId);
                        } else {
                            ps.setNull(8, Types.BIGINT);
                        }
                        ps.setString(9, p.payee);
                        ps.setBigDecimal(10, p.amount);
                        ps.setString(11, p.reference);
                        ps.setString(12, p.paymentMethod.name());
                        ps.setNull(13, Types.VARCHAR);
                        ps.setBoolean(14, p.isStandalone);
                        ps.setString(15, p.status.name());
                        ps.setLong(16, p.createdById);
                        if (p.approvedById != null) {
                            ps.setLong(17, p.approvedById);
                        } else {
                            ps.setNull(17, Types.BIGINT);
                        }
                        ps.setNull(18, Types.OTHER);
                        ps.setTimestamp(19, Timestamp.from(p.createdAt));
                        ps.setTimestamp(20, Timestamp.from(p.createdAt));
                        if (p.postedAt != null) {
                            ps.setTimestamp(21, Timestamp.from(p.postedAt));
                        } else {
                            ps.setNull(21, Types.TIMESTAMP);
                        }
                    }

                    @Override
                    public int getBatchSize() {
                        return payments.size();
                    }
                });
    }

    private void batchInsertARPayments(List<ARPaymentData> payments) {
        if (payments.isEmpty()) return;
        jdbcTemplate.batchUpdate(
                INSERT_AR_PAYMENT_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ARPaymentData p = payments.get(i);
                        ps.setObject(1, p.id);
                        ps.setLong(2, p.companyId);
                        ps.setLong(3, p.customerId);
                        ps.setString(4, p.receiptNumber);
                        ps.setDate(5, Date.valueOf(p.receiptDate));
                        if (p.cashAccountId != null) {
                            ps.setLong(6, p.cashAccountId);
                        } else {
                            ps.setNull(6, Types.BIGINT);
                        }
                        if (p.bankAccountId != null) {
                            ps.setLong(7, p.bankAccountId);
                        } else {
                            ps.setNull(7, Types.BIGINT);
                        }
                        ps.setString(8, p.payee);
                        ps.setBigDecimal(9, p.amount);
                        ps.setString(10, p.reference);
                        ps.setString(11, p.paymentMethod.name());
                        ps.setNull(12, Types.VARCHAR);
                        ps.setBoolean(13, p.isStandalone);
                        ps.setString(14, p.status.name());
                        ps.setLong(15, p.createdById);
                        if (p.postedById != null) {
                            ps.setLong(16, p.postedById);
                        } else {
                            ps.setNull(16, Types.BIGINT);
                        }
                        ps.setNull(17, Types.OTHER);
                        if (p.reversalReason != null) {
                            ps.setString(18, p.reversalReason);
                        } else {
                            ps.setNull(18, Types.VARCHAR);
                        }
                        ps.setNull(19, Types.OTHER);
                        ps.setNull(20, Types.OTHER);
                        ps.setTimestamp(21, Timestamp.from(p.createdAt));
                        ps.setTimestamp(22, Timestamp.from(p.createdAt));
                        if (p.postedAt != null) {
                            ps.setTimestamp(23, Timestamp.from(p.postedAt));
                        } else {
                            ps.setNull(23, Types.TIMESTAMP);
                        }
                    }

                    @Override
                    public int getBatchSize() {
                        return payments.size();
                    }
                });
    }

    private void batchInsertPaymentAllocations(List<PaymentAllocationData> allocations) {
        if (allocations.isEmpty()) return;
        jdbcTemplate.batchUpdate(
                INSERT_PAYMENT_ALLOCATION_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        PaymentAllocationData a = allocations.get(i);
                        ps.setObject(1, a.id);
                        ps.setLong(2, a.companyId);
                        ps.setObject(3, a.paymentId);
                        ps.setObject(4, a.purchaseBillId);
                        ps.setBigDecimal(5, a.allocatedAmount);
                        ps.setInt(6, a.allocationOrder);
                    }

                    @Override
                    public int getBatchSize() {
                        return allocations.size();
                    }
                });
    }

    private void batchInsertReceiptAllocations(List<ReceiptAllocationData> allocations) {
        if (allocations.isEmpty()) return;
        jdbcTemplate.batchUpdate(
                INSERT_RECEIPT_ALLOCATION_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ReceiptAllocationData a = allocations.get(i);
                        ps.setObject(1, a.id);
                        ps.setLong(2, a.companyId);
                        ps.setObject(3, a.receiptId);
                        ps.setObject(4, a.salesInvoiceId);
                        ps.setBigDecimal(5, a.allocatedAmount);
                        ps.setInt(6, a.allocationOrder);
                    }

                    @Override
                    public int getBatchSize() {
                        return allocations.size();
                    }
                });
    }

    private void batchUpdatePurchaseBills(List<BillUpdateData> updates) {
        if (updates.isEmpty()) return;
        jdbcTemplate.batchUpdate(
                UPDATE_PURCHASE_BILL_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        BillUpdateData u = updates.get(i);
                        ps.setBigDecimal(1, u.amountPaid);
                        ps.setBigDecimal(2, u.remainingBalance);
                        ps.setString(3, u.status.name());
                        ps.setObject(4, u.billId);
                    }

                    @Override
                    public int getBatchSize() {
                        return updates.size();
                    }
                });
    }

    private void batchUpdateSalesInvoices(List<InvoiceUpdateData> updates) {
        if (updates.isEmpty()) return;
        jdbcTemplate.batchUpdate(
                UPDATE_SALES_INVOICE_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        InvoiceUpdateData u = updates.get(i);
                        ps.setBigDecimal(1, u.amountPaid);
                        ps.setBigDecimal(2, u.remainingBalance);
                        ps.setString(3, u.status.name());
                        ps.setObject(4, u.invoiceId);
                    }

                    @Override
                    public int getBatchSize() {
                        return updates.size();
                    }
                });
    }

    private record APPaymentData(
            UUID id,
            Long companyId,
            Long supplierId,
            String paymentNumber,
            LocalDate paymentDate,
            LocalDate dueDate,
            Long cashAccountId,
            Long bankAccountId,
            String payee,
            BigDecimal amount,
            String reference,
            PaymentMethod paymentMethod,
            boolean isStandalone,
            PaymentStatus status,
            Long createdById,
            Long approvedById,
            Instant postedAt,
            Instant createdAt) {}

    private record ARPaymentData(
            UUID id,
            Long companyId,
            Long customerId,
            String receiptNumber,
            LocalDate receiptDate,
            Long cashAccountId,
            Long bankAccountId,
            String payee,
            BigDecimal amount,
            String reference,
            PaymentMethod paymentMethod,
            boolean isStandalone,
            ReceiptStatus status,
            Long createdById,
            Long postedById,
            Instant postedAt,
            Instant createdAt,
            String reversalReason) {}

    private record PaymentAllocationData(
            UUID id, Long companyId, UUID paymentId, UUID purchaseBillId, BigDecimal allocatedAmount, int allocationOrder) {}

    private record ReceiptAllocationData(
            UUID id, Long companyId, UUID receiptId, UUID salesInvoiceId, BigDecimal allocatedAmount, int allocationOrder) {}

    private record BillUpdateData(
            UUID billId, BigDecimal amountPaid, BigDecimal remainingBalance, PurchaseBillStatus status) {}

    private record InvoiceUpdateData(
            UUID invoiceId, BigDecimal amountPaid, BigDecimal remainingBalance, SalesInvoiceStatus status) {}

    private void validatePreconditions(TenantContext ctx) {
        if (ctx.getSupplierIds() == null || ctx.getSupplierIds().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot seed Payments: supplierIds is empty. Seed suppliers first.");
        }
        if (ctx.getCustomerIds() == null || ctx.getCustomerIds().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot seed Payments: customerIds is empty. Seed customers first.");
        }
        if (ctx.getBankAccountIds() == null || ctx.getBankAccountIds().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot seed Payments: bankAccountIds is empty. Seed bank accounts first.");
        }
        if (ctx.getUserIds() == null || ctx.getUserIds().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot seed Payments: userIds is empty. Seed users first.");
        }
    }
}

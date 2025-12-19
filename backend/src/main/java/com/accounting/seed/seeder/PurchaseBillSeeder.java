package com.accounting.seed.seeder;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
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

import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.VatRate;
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
public class PurchaseBillSeeder {

    private final JdbcTemplate jdbcTemplate;
    private final SeedProperties seedProperties;

    private static final String INSERT_BILL_SQL =
            "INSERT INTO purchase_bills (id, company_id, supplier_id, bill_number, bill_date, due_date, "
                    + "reference, description, status, total_amount, vat_amount, amount_paid, remaining_balance, "
                    + "created_by_id, approved_by_id, posted_voucher_id, is_sensitive, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_LINE_SQL =
            "INSERT INTO purchase_bill_lines (id, purchase_bill_id, line_number, account_id, description, "
                    + "quantity, unit_price, amount, vat_rate, vat_amount, cost_center_id, item_id, company_id, "
                    + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final List<String> PURCHASE_DESCRIPTIONS = List.of(
            "Mua hàng hóa",
            "Mua nguyên vật liệu",
            "Mua công cụ dụng cụ",
            "Mua vật tư văn phòng",
            "Mua thiết bị",
            "Chi phí dịch vụ mua ngoài",
            "Chi phí vận chuyển",
            "Chi phí sửa chữa bảo trì");

    private static final List<String> EXPENSE_ACCOUNT_CODES = List.of("642", "156", "152", "153");

    private static final int[] DUE_DAYS = {15, 30, 45, 60};

    public record DocumentInfo(
            UUID id, Long supplierId, BigDecimal totalAmount, BigDecimal remainingBalance, PurchaseBillStatus status, LocalDate billDate) {}

    public record PurchaseBillResult(
            List<UUID> billIds, Map<UUID, Long> billToSupplier, Map<UUID, DocumentInfo> billInfo) {}

    @Transactional
    public PurchaseBillResult seedPurchaseBills(
            TenantContext ctx, int count, VietnameseFaker faker, UniqueGenerator generator, Random random) {
        validatePreconditions(ctx);
        log.info("Seeding {} purchase bills for company ID: {}", count, ctx.getCompanyId());

        LocalDate startDate = seedProperties.getStartDate();
        LocalDate endDate = seedProperties.getEndDate();

        List<BillData> bills = new ArrayList<>(count);
        List<LineData> allLines = new ArrayList<>();
        List<UUID> billIds = new ArrayList<>(count);
        Map<UUID, Long> billToSupplier = new HashMap<>();
        Map<UUID, DocumentInfo> billInfo = new HashMap<>();

        int year = endDate.getYear();
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        Instant now = Instant.now();

        for (int i = 0; i < count; i++) {
            UUID billId = UUID.randomUUID();
            Long companyId = ctx.getCompanyId();
            Long supplierId = randomElement(ctx.getSupplierIds(), random);
            String billNumber = generator.nextBillNumber(companyId, supplierId, year);
            LocalDate billDate = startDate.plusDays(random.nextLong(daysBetween + 1));
            LocalDate dueDate = billDate.plusDays(DUE_DAYS[random.nextInt(DUE_DAYS.length)]);
            String reference = "HD-" + String.format("%06d", random.nextInt(1_000_000));
            String description = randomElement(PURCHASE_DESCRIPTIONS, random);
            PurchaseBillStatus status = randomStatus(random);
            Long createdById = randomElement(ctx.getUserIds(), random);
            Long approvedById = needsApprover(status) ? randomOther(ctx.getUserIds(), createdById, random) : null;
            boolean isSensitive = random.nextDouble() < 0.05;

            List<LineData> lines = generateLines(billId, companyId, ctx, faker, random, now);
            BigDecimal totalAmount = lines.stream().map(l -> l.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal vatAmount = lines.stream().map(l -> l.vatAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal grandTotal = totalAmount.add(vatAmount);

            BillData bill = new BillData(
                    billId,
                    companyId,
                    supplierId,
                    billNumber,
                    billDate,
                    dueDate,
                    reference,
                    description,
                    status,
                    grandTotal,
                    vatAmount,
                    BigDecimal.ZERO,
                    grandTotal,
                    createdById,
                    approvedById,
                    isSensitive,
                    now);

            bills.add(bill);
            allLines.addAll(lines);
            billIds.add(billId);
            billToSupplier.put(billId, supplierId);
            if (status == PurchaseBillStatus.POSTED) {
                billInfo.put(billId, new DocumentInfo(billId, supplierId, grandTotal, grandTotal, status, billDate));
            }
        }

        batchInsertBills(bills);
        batchInsertLines(allLines);

        log.info(
                "Successfully seeded {} purchase bills with {} lines for company ID: {}",
                bills.size(),
                allLines.size(),
                ctx.getCompanyId());

        return new PurchaseBillResult(billIds, billToSupplier, billInfo);
    }

    private List<LineData> generateLines(
            UUID billId, Long companyId, TenantContext ctx, VietnameseFaker faker, Random random, Instant now) {
        int lineCount = 1 + random.nextInt(5);
        List<LineData> lines = new ArrayList<>(lineCount);

        for (int i = 0; i < lineCount; i++) {
            UUID lineId = UUID.randomUUID();
            int lineNumber = i + 1;
            Long accountId = getRandomExpenseAccount(ctx, random);
            String description = faker.productDescription();
            BigDecimal quantity = new BigDecimal(1 + random.nextInt(100));
            BigDecimal unitPrice = MoneyHelper.niceVndAmount(random, 10_000, 10_000_000);
            BigDecimal amount = quantity.multiply(unitPrice);
            VatRate vatRate = MoneyHelper.randomVatRate(random);
            BigDecimal vatAmount = MoneyHelper.calculateVat(amount, vatRate);

            lines.add(new LineData(
                    lineId, billId, lineNumber, accountId, description, quantity, unitPrice, amount, vatRate, vatAmount,
                    companyId, now));
        }

        return lines;
    }

    private Long getRandomExpenseAccount(TenantContext ctx, Random random) {
        String code = randomElement(EXPENSE_ACCOUNT_CODES, random);
        Long accountId = ctx.getAccountsByCode().get(code);
        if (accountId != null) {
            return accountId;
        }
        List<Long> accounts = ctx.getAccountsByPrefix().get(code.substring(0, 2));
        if (accounts != null && !accounts.isEmpty()) {
            return randomElement(accounts, random);
        }
        return ctx.getAccountsByCode().values().iterator().next();
    }

    private PurchaseBillStatus randomStatus(Random random) {
        // Only create statuses that don't require actual payments
        // PAID and PARTIALLY_PAID will be set by PaymentSeeder when payments are created
        int roll = random.nextInt(100);
        if (roll < 15) return PurchaseBillStatus.DRAFT;
        if (roll < 25) return PurchaseBillStatus.PENDING_APPROVAL;
        if (roll < 90) return PurchaseBillStatus.POSTED;
        return PurchaseBillStatus.REJECTED;
    }

    private boolean needsApprover(PurchaseBillStatus status) {
        return status == PurchaseBillStatus.POSTED;
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

    private void batchInsertBills(List<BillData> bills) {
        jdbcTemplate.batchUpdate(
                INSERT_BILL_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        BillData bill = bills.get(i);
                        ps.setObject(1, bill.id);
                        ps.setLong(2, bill.companyId);
                        ps.setLong(3, bill.supplierId);
                        ps.setString(4, bill.billNumber);
                        ps.setDate(5, Date.valueOf(bill.billDate));
                        ps.setDate(6, Date.valueOf(bill.dueDate));
                        ps.setString(7, bill.reference);
                        ps.setString(8, bill.description);
                        ps.setString(9, bill.status.name());
                        ps.setBigDecimal(10, bill.totalAmount);
                        ps.setBigDecimal(11, bill.vatAmount);
                        ps.setBigDecimal(12, bill.amountPaid);
                        ps.setBigDecimal(13, bill.remainingBalance);
                        ps.setLong(14, bill.createdById);
                        if (bill.approvedById != null) {
                            ps.setLong(15, bill.approvedById);
                        } else {
                            ps.setNull(15, Types.BIGINT);
                        }
                        ps.setNull(16, Types.OTHER);
                        ps.setBoolean(17, bill.isSensitive);
                        ps.setTimestamp(18, Timestamp.from(bill.createdAt));
                        ps.setTimestamp(19, Timestamp.from(bill.createdAt));
                    }

                    @Override
                    public int getBatchSize() {
                        return bills.size();
                    }
                });
    }

    private void batchInsertLines(List<LineData> lines) {
        jdbcTemplate.batchUpdate(
                INSERT_LINE_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        LineData line = lines.get(i);
                        ps.setObject(1, line.id);
                        ps.setObject(2, line.purchaseBillId);
                        ps.setInt(3, line.lineNumber);
                        ps.setLong(4, line.accountId);
                        ps.setString(5, line.description);
                        ps.setBigDecimal(6, line.quantity);
                        ps.setBigDecimal(7, line.unitPrice);
                        ps.setBigDecimal(8, line.amount);
                        ps.setString(9, line.vatRate.name());
                        ps.setBigDecimal(10, line.vatAmount);
                        ps.setNull(11, Types.BIGINT);
                        ps.setNull(12, Types.BIGINT);
                        ps.setLong(13, line.companyId);
                        ps.setTimestamp(14, Timestamp.from(line.createdAt));
                        ps.setTimestamp(15, Timestamp.from(line.createdAt));
                    }

                    @Override
                    public int getBatchSize() {
                        return lines.size();
                    }
                });
    }

    private record BillData(
            UUID id,
            Long companyId,
            Long supplierId,
            String billNumber,
            LocalDate billDate,
            LocalDate dueDate,
            String reference,
            String description,
            PurchaseBillStatus status,
            BigDecimal totalAmount,
            BigDecimal vatAmount,
            BigDecimal amountPaid,
            BigDecimal remainingBalance,
            Long createdById,
            Long approvedById,
            boolean isSensitive,
            Instant createdAt) {}

    private record LineData(
            UUID id,
            UUID purchaseBillId,
            int lineNumber,
            Long accountId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            VatRate vatRate,
            BigDecimal vatAmount,
            Long companyId,
            Instant createdAt) {}

    private void validatePreconditions(TenantContext ctx) {
        if (ctx.getSupplierIds() == null || ctx.getSupplierIds().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot seed PurchaseBills: supplierIds is empty. Seed suppliers first.");
        }
        if (ctx.getUserIds() == null || ctx.getUserIds().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot seed PurchaseBills: userIds is empty. Seed users first.");
        }
    }
}

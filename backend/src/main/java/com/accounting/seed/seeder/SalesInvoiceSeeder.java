package com.accounting.seed.seeder;

import java.math.BigDecimal;
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

import com.accounting.entity.SalesInvoiceStatus;
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
public class SalesInvoiceSeeder {

  private static final String INSERT_INVOICE_SQL =
      "INSERT INTO sales_invoices (id, company_id, customer_id, invoice_number, invoice_date, "
          + "due_date, reference, description, status, total_amount, vat_amount, amount_paid, "
          + "remaining_balance, created_by_id, approved_by_id, is_sensitive, is_deleted, "
          + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  private static final String INSERT_LINE_SQL =
      "INSERT INTO sales_invoice_lines (id, sales_invoice_id, line_number, account_id, "
          + "description, quantity, unit_price, amount, vat_rate, vat_amount, company_id, "
          + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

  // Removed static DESCRIPTIONS - now using faker.salesInvoiceDescription() for diverse descriptions

  private static final int[] DUE_DATE_DAYS = {15, 30, 45, 60};

  private final JdbcTemplate jdbcTemplate;
  private final SeedProperties seedProperties;

  public record DocumentInfo(
      UUID id,
      Long customerId,
      BigDecimal totalAmount,
      BigDecimal remainingBalance,
      SalesInvoiceStatus status,
      LocalDate invoiceDate) {}

  public record SalesInvoiceResult(
      List<UUID> invoiceIds, Map<UUID, Long> invoiceToCustomer, Map<UUID, DocumentInfo> invoiceInfo) {}

  @Transactional
  public SalesInvoiceResult seedSalesInvoices(
      TenantContext ctx,
      int count,
      VietnameseFaker faker,
      UniqueGenerator generator,
      Random random) {
    validatePreconditions(ctx);
    log.info("Seeding {} sales invoices for company ID: {}", count, ctx.getCompanyId());

    LocalDate startDate = seedProperties.getStartDate();
    LocalDate endDate = seedProperties.getEndDate();

    List<InvoiceData> invoices = new ArrayList<>(count);
    List<LineData> allLines = new ArrayList<>();
    List<UUID> invoiceIds = new ArrayList<>(count);
    Map<UUID, Long> invoiceToCustomer = new HashMap<>();
    Map<UUID, DocumentInfo> invoiceInfo = new HashMap<>();
    Instant now = Instant.now();
    long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);

    for (int i = 0; i < count; i++) {
      UUID invoiceId = UUID.randomUUID();
      Long customerId = ctx.getCustomerIds().get(random.nextInt(ctx.getCustomerIds().size()));
      int year = startDate.getYear();
      LocalDate invoiceDate =
          startDate.plusDays(random.nextLong(daysBetween + 1));
      LocalDate dueDate =
          invoiceDate.plusDays(DUE_DATE_DAYS[random.nextInt(DUE_DATE_DAYS.length)]);
      String invoiceNumber = generator.nextInvoiceNumber(ctx.getCompanyId(), customerId, year);
      String reference = String.format("HDB-%06d", random.nextInt(1_000_000));
      String description = faker.salesInvoiceDescription("KH-" + customerId, invoiceDate.getYear());
      SalesInvoiceStatus status = randomStatus(random);
      Long createdById = ctx.getUserIds().get(random.nextInt(ctx.getUserIds().size()));
      Long approvedById = null;
      if (status == SalesInvoiceStatus.POSTED) {
        List<Long> otherUsers =
            ctx.getUserIds().stream().filter(id -> !id.equals(createdById)).toList();
        if (!otherUsers.isEmpty()) {
          approvedById = otherUsers.get(random.nextInt(otherUsers.size()));
        } else {
          approvedById = createdById;
        }
      }
      boolean isSensitive = random.nextDouble() < 0.05;

      int lineCount = random.nextInt(5) + 1;
      BigDecimal invoiceTotalAmount = BigDecimal.ZERO;
      BigDecimal invoiceVatAmount = BigDecimal.ZERO;

      for (int lineNum = 1; lineNum <= lineCount; lineNum++) {
        UUID lineId = UUID.randomUUID();
        Long accountId = getRevenueAccountId(ctx, random);
        String lineDescription = faker.lineItemDescription();
        BigDecimal quantity = BigDecimal.valueOf(random.nextInt(100) + 1);
        BigDecimal unitPrice = randomVndPrice(random);
        BigDecimal amount = quantity.multiply(unitPrice);
        VatRate vatRate = randomVatRate(random);
        BigDecimal vatAmount = MoneyHelper.roundVnd(amount.multiply(vatRate.getRate()));

        invoiceTotalAmount = invoiceTotalAmount.add(amount).add(vatAmount);
        invoiceVatAmount = invoiceVatAmount.add(vatAmount);

        allLines.add(
            new LineData(
                lineId,
                invoiceId,
                lineNum,
                accountId,
                lineDescription,
                quantity,
                unitPrice,
                amount,
                vatRate,
                vatAmount,
                ctx.getCompanyId(),
                now));
      }

      // Always set amount_paid=0 initially - PaymentSeeder will create actual payments
      BigDecimal amountPaid = BigDecimal.ZERO;
      BigDecimal remainingBalance = invoiceTotalAmount;

      invoices.add(
          new InvoiceData(
              invoiceId,
              ctx.getCompanyId(),
              customerId,
              invoiceNumber,
              invoiceDate,
              dueDate,
              reference,
              description,
              status,
              invoiceTotalAmount,
              invoiceVatAmount,
              amountPaid,
              remainingBalance,
              createdById,
              approvedById,
              isSensitive,
              now));

      invoiceIds.add(invoiceId);
      invoiceToCustomer.put(invoiceId, customerId);
      if (status == SalesInvoiceStatus.POSTED) {
        invoiceInfo.put(
            invoiceId, new DocumentInfo(invoiceId, customerId, invoiceTotalAmount, invoiceTotalAmount, status, invoiceDate));
      }
    }

    batchInsertInvoices(invoices);
    batchInsertLines(allLines);

    log.info(
        "Successfully seeded {} sales invoices with {} lines for company ID: {}",
        invoices.size(),
        allLines.size(),
        ctx.getCompanyId());

    return new SalesInvoiceResult(invoiceIds, invoiceToCustomer, invoiceInfo);
  }

  private SalesInvoiceStatus randomStatus(Random random) {
    // Only create statuses that don't require actual payments
    // PAID and PARTIALLY_PAID will be set by PaymentSeeder when payments are created
    double r = random.nextDouble();
    if (r < 0.15) return SalesInvoiceStatus.DRAFT;
    if (r < 0.25) return SalesInvoiceStatus.PENDING_APPROVAL;
    if (r < 0.90) return SalesInvoiceStatus.POSTED;
    return SalesInvoiceStatus.REJECTED;
  }

  private VatRate randomVatRate(Random random) {
    double r = random.nextDouble();
    if (r < 0.60) return VatRate.TEN;
    if (r < 0.75) return VatRate.FIVE;
    if (r < 0.90) return VatRate.ZERO;
    return VatRate.EXEMPT;
  }

  private Long getRevenueAccountId(TenantContext ctx, Random random) {
    Long account511 = ctx.getAccountsByCode().get("511");
    Long account512 = ctx.getAccountsByCode().get("512");
    if (account511 != null && account512 != null) {
      return random.nextBoolean() ? account511 : account512;
    }
    if (account511 != null) return account511;
    if (account512 != null) return account512;
    List<Long> revenueAccounts = ctx.getAccountsByPrefix().get("51");
    if (revenueAccounts != null && !revenueAccounts.isEmpty()) {
      return revenueAccounts.get(random.nextInt(revenueAccounts.size()));
    }
    return ctx.getAccountsByCode().values().iterator().next();
  }

  private BigDecimal randomVndPrice(Random random) {
    long minThousands = 50;
    long maxThousands = 50_000;
    long thousands = minThousands + random.nextLong(maxThousands - minThousands + 1);
    return BigDecimal.valueOf(thousands * 1000);
  }

  private void batchInsertInvoices(List<InvoiceData> invoices) {
    jdbcTemplate.batchUpdate(
        INSERT_INVOICE_SQL,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            InvoiceData inv = invoices.get(i);
            ps.setObject(1, inv.id);
            ps.setLong(2, inv.companyId);
            ps.setLong(3, inv.customerId);
            ps.setString(4, inv.invoiceNumber);
            ps.setObject(5, inv.invoiceDate);
            ps.setObject(6, inv.dueDate);
            ps.setString(7, inv.reference);
            ps.setString(8, inv.description);
            ps.setString(9, inv.status.name());
            ps.setBigDecimal(10, inv.totalAmount);
            ps.setBigDecimal(11, inv.vatAmount);
            ps.setBigDecimal(12, inv.amountPaid);
            ps.setBigDecimal(13, inv.remainingBalance);
            ps.setLong(14, inv.createdById);
            if (inv.approvedById != null) {
              ps.setLong(15, inv.approvedById);
            } else {
              ps.setNull(15, Types.BIGINT);
            }
            ps.setBoolean(16, inv.isSensitive);
            ps.setBoolean(17, false);
            ps.setTimestamp(18, Timestamp.from(inv.createdAt));
            ps.setTimestamp(19, Timestamp.from(inv.createdAt));
          }

          @Override
          public int getBatchSize() {
            return invoices.size();
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
            ps.setObject(2, line.salesInvoiceId);
            ps.setInt(3, line.lineNumber);
            ps.setLong(4, line.accountId);
            ps.setString(5, line.description);
            ps.setBigDecimal(6, line.quantity);
            ps.setBigDecimal(7, line.unitPrice);
            ps.setBigDecimal(8, line.amount);
            ps.setString(9, line.vatRate.name());
            ps.setBigDecimal(10, line.vatAmount);
            ps.setLong(11, line.companyId);
            ps.setTimestamp(12, Timestamp.from(line.createdAt));
            ps.setTimestamp(13, Timestamp.from(line.createdAt));
          }

          @Override
          public int getBatchSize() {
            return lines.size();
          }
        });
  }

  private record InvoiceData(
      UUID id,
      Long companyId,
      Long customerId,
      String invoiceNumber,
      LocalDate invoiceDate,
      LocalDate dueDate,
      String reference,
      String description,
      SalesInvoiceStatus status,
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
      UUID salesInvoiceId,
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
    if (ctx.getCustomerIds() == null || ctx.getCustomerIds().isEmpty()) {
      throw new IllegalStateException(
          "Cannot seed SalesInvoices: customerIds is empty. Seed customers first.");
    }
    if (ctx.getUserIds() == null || ctx.getUserIds().isEmpty()) {
      throw new IllegalStateException(
          "Cannot seed SalesInvoices: userIds is empty. Seed users first.");
    }
  }
}

package com.accounting.seed.seeder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.VatRate;
import com.accounting.seed.MoneyHelper;
import com.accounting.seed.TenantContext;
import com.accounting.seed.UniqueGenerator;
import com.accounting.seed.VietnameseFaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class VoucherSeeder {

  public record VoucherResult(
      List<UUID> voucherIds, List<UUID> draftVoucherIds, List<UUID> postedVoucherIds) {}

  private record VoucherData(
      UUID id,
      Long companyId,
      String voucherNumber,
      LocalDate voucherDate,
      UUID periodId,
      String description,
      String status,
      String currency,
      BigDecimal totalDebit,
      BigDecimal totalCredit,
      Long enteredBy,
      Long postedBy,
      Instant postedAt,
      Instant createdAt,
      Instant updatedAt,
      Long version,
      Boolean isLocked) {}

  private record VoucherLineData(
      UUID id,
      UUID voucherId,
      Integer lineNumber,
      Long accountId,
      BigDecimal debit,
      BigDecimal credit,
      String description,
      Long customerId,
      Long supplierId,
      Long itemId,
      Long bankAccountId,
      Long companyId) {}

  private enum VoucherType {
    CASH_RECEIPT("PT", "Thu tiền mặt"),
    CASH_PAYMENT("PC", "Chi tiền mặt"),
    BANK_RECEIPT("BC", "Thu tiền ngân hàng"),
    BANK_PAYMENT("UNC", "Chi tiền ngân hàng"),
    SALES_POSTING("HD", "Hạch toán doanh thu"),
    PURCHASE_POSTING("NK", "Hạch toán mua hàng"),
    GENERAL_JOURNAL("JV", "Phiếu kế toán");

    final String prefix;
    final String baseDescription;

    VoucherType(String prefix, String baseDescription) {
      this.prefix = prefix;
      this.baseDescription = baseDescription;
    }
  }

  private static final String VOUCHER_INSERT_SQL =
      """
      INSERT INTO vouchers (id, company_id, voucher_number, voucher_date, period_id, description,
          status, currency, total_debit, total_credit, entered_by, posted_by, posted_at,
          created_at, updated_at, version, is_locked)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private static final String VOUCHER_LINE_INSERT_SQL =
      """
      INSERT INTO voucher_lines (id, voucher_id, line_number, account_id, debit, credit,
          description, customer_id, supplier_id, item_id, bank_account_id, company_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private final JdbcTemplate jdbcTemplate;

  private static final List<String> REQUIRED_TT200_ACCOUNTS =
      Arrays.asList("111", "112", "131", "331", "511", "1331", "3331", "156", "152", "642");

  @Transactional
  public VoucherResult seedVouchers(
      TenantContext ctx,
      int count,
      VietnameseFaker faker,
      UniqueGenerator generator,
      List<UUID> periodIds,
      Random random) {
    log.info("Seeding {} vouchers for company ID: {}", count, ctx.getCompanyId());
    validateRequiredAccounts(ctx);

    List<VoucherData> vouchers = new ArrayList<>(count);
    List<VoucherLineData> allLines = new ArrayList<>();
    List<UUID> voucherIds = new ArrayList<>(count);
    List<UUID> draftVoucherIds = new ArrayList<>();
    List<UUID> postedVoucherIds = new ArrayList<>();

    Map<UUID, LocalDate[]> periodDateRanges = fetchPeriodDateRanges(periodIds);

    for (int i = 0; i < count; i++) {
      VoucherType type = selectVoucherType(random);
      UUID periodId = periodIds.get(random.nextInt(periodIds.size()));
      LocalDate[] dateRange = periodDateRanges.get(periodId);
      LocalDate voucherDate = randomDateInRange(dateRange[0], dateRange[1], random);
      int year = voucherDate.getYear();

      String status = determineStatus(random);
      Long enteredBy = randomFromList(ctx.getUserIds(), random);
      Long postedBy = null;
      Instant postedAt = null;

      if ("posted".equals(status)) {
        postedBy = selectDifferentUser(ctx.getUserIds(), enteredBy, random);
        postedAt =
            voucherDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .plus(random.nextInt(24), ChronoUnit.HOURS);
      }

      UUID voucherId = UUID.randomUUID();
      Instant now = Instant.now();

      BigDecimal totalAmount = generateAmount(random);
      List<VoucherLineData> lines =
          generateBalancedLines(ctx, voucherId, type, totalAmount, faker, random);

      BigDecimal totalDebit =
          lines.stream().map(VoucherLineData::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal totalCredit =
          lines.stream().map(VoucherLineData::credit).reduce(BigDecimal.ZERO, BigDecimal::add);

      VoucherData voucher =
          new VoucherData(
              voucherId,
              ctx.getCompanyId(),
              generator.nextVoucherNumber(ctx.getCompanyId(), year, type.prefix),
              voucherDate,
              periodId,
              faker.voucherDescription(type.prefix, "Đối tác " + (i + 1), year),
              status,
              "VND",
              totalDebit,
              totalCredit,
              enteredBy,
              postedBy,
              postedAt,
              now,
              now,
              0L,
              false);

      vouchers.add(voucher);
      allLines.addAll(lines);
      voucherIds.add(voucherId);

      if ("draft".equals(status)) {
        draftVoucherIds.add(voucherId);
      } else if ("posted".equals(status)) {
        postedVoucherIds.add(voucherId);
      }
    }

    batchInsertVouchers(vouchers);
    batchInsertVoucherLines(allLines);

    log.info(
        "Successfully seeded {} vouchers ({} posted, {} draft) for company ID: {}",
        vouchers.size(),
        postedVoucherIds.size(),
        draftVoucherIds.size(),
        ctx.getCompanyId());

    return new VoucherResult(voucherIds, draftVoucherIds, postedVoucherIds);
  }

  private Map<UUID, LocalDate[]> fetchPeriodDateRanges(List<UUID> periodIds) {
    String sql =
        "SELECT id, start_date, end_date FROM accounting_periods WHERE id = ANY(?)";
    UUID[] idArray = periodIds.toArray(new UUID[0]);

    return jdbcTemplate.query(
        sql,
        ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", idArray)),
        rs -> {
          java.util.HashMap<UUID, LocalDate[]> map = new java.util.HashMap<>();
          while (rs.next()) {
            UUID id = UUID.fromString(rs.getString("id"));
            LocalDate start = rs.getDate("start_date").toLocalDate();
            LocalDate end = rs.getDate("end_date").toLocalDate();
            map.put(id, new LocalDate[] {start, end});
          }
          return map;
        });
  }

  private VoucherType selectVoucherType(Random random) {
    VoucherType[] types = VoucherType.values();
    return types[random.nextInt(types.length)];
  }

  private String determineStatus(Random random) {
    double rand = random.nextDouble();
    if (rand < 0.70) {
      return "posted";
    } else if (rand < 0.85) {
      return "draft";
    } else {
      return "unposted";
    }
  }

  private LocalDate randomDateInRange(LocalDate start, LocalDate end, Random random) {
    long daysBetween = ChronoUnit.DAYS.between(start, end);
    if (daysBetween <= 0) {
      return start;
    }
    return start.plusDays(random.nextInt((int) daysBetween + 1));
  }

  private <T> T randomFromList(List<T> list, Random random) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    return list.get(random.nextInt(list.size()));
  }

  private Long selectDifferentUser(List<Long> userIds, Long excludeUserId, Random random) {
    if (userIds == null || userIds.isEmpty()) {
      return null;
    }
    if (userIds.size() == 1) {
      return userIds.get(0);
    }
    List<Long> candidates = userIds.stream().filter(id -> !id.equals(excludeUserId)).toList();
    if (candidates.isEmpty()) {
      return userIds.get(random.nextInt(userIds.size()));
    }
    return candidates.get(random.nextInt(candidates.size()));
  }

  private BigDecimal generateAmount(Random random) {
    long minAmount = 1_000_000L;
    long maxAmount = 100_000_000L;
    long amount = minAmount + (long) (random.nextDouble() * (maxAmount - minAmount));
    amount = (amount / 1000) * 1000;
    return BigDecimal.valueOf(amount);
  }

  private String generateDescription(VoucherType type, VietnameseFaker faker) {
    return switch (type) {
      case CASH_RECEIPT -> pickRandom(
          faker.getFaker().options(),
          "Thu tiền khách hàng",
          "Thu tiền bán hàng",
          "Thu tiền thanh toán công nợ",
          "Thu tiền tạm ứng thừa");
      case CASH_PAYMENT -> pickRandom(
          faker.getFaker().options(),
          "Chi tiền mua hàng",
          "Chi tiền thanh toán nhà cung cấp",
          "Chi tiền tạm ứng",
          "Chi phí văn phòng");
      case BANK_RECEIPT -> pickRandom(
          faker.getFaker().options(),
          "Thu tiền chuyển khoản khách hàng",
          "Thu tiền thanh toán qua ngân hàng",
          "Nhận tiền từ khách hàng");
      case BANK_PAYMENT -> pickRandom(
          faker.getFaker().options(),
          "Chuyển khoản thanh toán nhà cung cấp",
          "Thanh toán qua ngân hàng",
          "Chi phí qua chuyển khoản");
      case SALES_POSTING -> pickRandom(
          faker.getFaker().options(),
          "Hạch toán doanh thu bán hàng",
          "Ghi nhận doanh thu",
          "Hóa đơn bán hàng");
      case PURCHASE_POSTING -> pickRandom(
          faker.getFaker().options(),
          "Hạch toán mua hàng hóa",
          "Nhập kho hàng hóa",
          "Mua nguyên vật liệu",
          "Hóa đơn mua hàng");
      case GENERAL_JOURNAL -> pickRandom(
          faker.getFaker().options(),
          "Bút toán điều chỉnh",
          "Phân bổ chi phí",
          "Kết chuyển cuối kỳ",
          "Điều chỉnh số dư");
    };
  }

  private String pickRandom(net.datafaker.providers.base.Options options, String... choices) {
    return options.option(choices);
  }

  private List<VoucherLineData> generateBalancedLines(
      TenantContext ctx,
      UUID voucherId,
      VoucherType type,
      BigDecimal totalAmount,
      VietnameseFaker faker,
      Random random) {
    List<VoucherLineData> lines = new ArrayList<>();
    Map<String, Long> accounts = ctx.getAccountsByCode();

    switch (type) {
      case CASH_RECEIPT -> {
        Long customerId = randomFromList(ctx.getCustomerIds(), random);
        Long bankAccountId = randomFromList(ctx.getBankAccountIds(), random);
        boolean fromReceivables = random.nextBoolean();

        lines.add(
            createLine(
                voucherId,
                1,
                getAccountId(accounts, "111", ctx.getCompanyId()),
                totalAmount,
                BigDecimal.ZERO,
                "Thu tiền mặt",
                null,
                null,
                bankAccountId,
                ctx.getCompanyId()));

        if (fromReceivables) {
          lines.add(
              createLine(
                  voucherId,
                  2,
                  getAccountId(accounts, "131", ctx.getCompanyId()),
                  BigDecimal.ZERO,
                  totalAmount,
                  "Công nợ khách hàng",
                  customerId,
                  null,
                  null,
                  ctx.getCompanyId()));
        } else {
          lines.add(
              createLine(
                  voucherId,
                  2,
                  getAccountId(accounts, "511", ctx.getCompanyId()),
                  BigDecimal.ZERO,
                  totalAmount,
                  "Doanh thu bán hàng",
                  customerId,
                  null,
                  null,
                  ctx.getCompanyId()));
        }
      }

      case CASH_PAYMENT -> {
        Long supplierId = randomFromList(ctx.getSupplierIds(), random);
        Long bankAccountId = randomFromList(ctx.getBankAccountIds(), random);
        boolean toPayables = random.nextBoolean();

        if (toPayables) {
          lines.add(
              createLine(
                  voucherId,
                  1,
                  getAccountId(accounts, "331", ctx.getCompanyId()),
                  totalAmount,
                  BigDecimal.ZERO,
                  "Thanh toán nhà cung cấp",
                  null,
                  supplierId,
                  null,
                  ctx.getCompanyId()));
        } else {
          lines.add(
              createLine(
                  voucherId,
                  1,
                  getAccountId(accounts, "642", ctx.getCompanyId()),
                  totalAmount,
                  BigDecimal.ZERO,
                  "Chi phí quản lý",
                  null,
                  null,
                  null,
                  ctx.getCompanyId()));
        }

        lines.add(
            createLine(
                voucherId,
                2,
                getAccountId(accounts, "111", ctx.getCompanyId()),
                BigDecimal.ZERO,
                totalAmount,
                "Chi tiền mặt",
                null,
                null,
                bankAccountId,
                ctx.getCompanyId()));
      }

      case BANK_RECEIPT -> {
        Long customerId = randomFromList(ctx.getCustomerIds(), random);
        Long bankAccountId = randomFromList(ctx.getBankAccountIds(), random);
        boolean fromReceivables = random.nextBoolean();

        lines.add(
            createLine(
                voucherId,
                1,
                getAccountId(accounts, "112", ctx.getCompanyId()),
                totalAmount,
                BigDecimal.ZERO,
                "Thu tiền ngân hàng",
                null,
                null,
                bankAccountId,
                ctx.getCompanyId()));

        if (fromReceivables) {
          lines.add(
              createLine(
                  voucherId,
                  2,
                  getAccountId(accounts, "131", ctx.getCompanyId()),
                  BigDecimal.ZERO,
                  totalAmount,
                  "Công nợ khách hàng",
                  customerId,
                  null,
                  null,
                  ctx.getCompanyId()));
        } else {
          lines.add(
              createLine(
                  voucherId,
                  2,
                  getAccountId(accounts, "511", ctx.getCompanyId()),
                  BigDecimal.ZERO,
                  totalAmount,
                  "Doanh thu bán hàng",
                  customerId,
                  null,
                  null,
                  ctx.getCompanyId()));
        }
      }

      case BANK_PAYMENT -> {
        Long supplierId = randomFromList(ctx.getSupplierIds(), random);
        Long bankAccountId = randomFromList(ctx.getBankAccountIds(), random);
        boolean toPayables = random.nextBoolean();

        if (toPayables) {
          lines.add(
              createLine(
                  voucherId,
                  1,
                  getAccountId(accounts, "331", ctx.getCompanyId()),
                  totalAmount,
                  BigDecimal.ZERO,
                  "Thanh toán nhà cung cấp",
                  null,
                  supplierId,
                  null,
                  ctx.getCompanyId()));
        } else {
          lines.add(
              createLine(
                  voucherId,
                  1,
                  getAccountId(accounts, "642", ctx.getCompanyId()),
                  totalAmount,
                  BigDecimal.ZERO,
                  "Chi phí quản lý",
                  null,
                  null,
                  null,
                  ctx.getCompanyId()));
        }

        lines.add(
            createLine(
                voucherId,
                2,
                getAccountId(accounts, "112", ctx.getCompanyId()),
                BigDecimal.ZERO,
                totalAmount,
                "Chi tiền ngân hàng",
                null,
                null,
                bankAccountId,
                ctx.getCompanyId()));
      }

      case SALES_POSTING -> {
        Long customerId = randomFromList(ctx.getCustomerIds(), random);
        VatRate vatRate = MoneyHelper.randomVatRate(random);
        while (vatRate == VatRate.ZERO || vatRate == VatRate.EXEMPT) {
          vatRate = MoneyHelper.randomVatRate(random);
        }
        BigDecimal netAmount = totalAmount.divide(BigDecimal.ONE.add(vatRate.getRate()), 0, RoundingMode.HALF_UP);
        BigDecimal vatAmount = totalAmount.subtract(netAmount);

        lines.add(
            createLine(
                voucherId,
                1,
                getAccountId(accounts, "131", ctx.getCompanyId()),
                totalAmount,
                BigDecimal.ZERO,
                "Công nợ khách hàng",
                customerId,
                null,
                null,
                ctx.getCompanyId()));

        lines.add(
            createLine(
                voucherId,
                2,
                getAccountId(accounts, "511", ctx.getCompanyId()),
                BigDecimal.ZERO,
                netAmount,
                "Doanh thu bán hàng",
                null,
                null,
                null,
                ctx.getCompanyId()));

        lines.add(
            createLine(
                voucherId,
                3,
                getAccountId(accounts, "3331", ctx.getCompanyId()),
                BigDecimal.ZERO,
                vatAmount,
                "Thuế GTGT đầu ra",
                null,
                null,
                null,
                ctx.getCompanyId()));
      }

      case PURCHASE_POSTING -> {
        Long supplierId = randomFromList(ctx.getSupplierIds(), random);
        VatRate vatRate = MoneyHelper.randomVatRate(random);
        while (vatRate == VatRate.ZERO || vatRate == VatRate.EXEMPT) {
          vatRate = MoneyHelper.randomVatRate(random);
        }
        BigDecimal netAmount = totalAmount.divide(BigDecimal.ONE.add(vatRate.getRate()), 0, RoundingMode.HALF_UP);
        BigDecimal vatAmount = totalAmount.subtract(netAmount);

        String[] purchaseAccounts = {"156", "152", "642"};
        String purchaseAccount = purchaseAccounts[random.nextInt(purchaseAccounts.length)];
        String purchaseDesc =
            switch (purchaseAccount) {
              case "156" -> "Hàng hóa";
              case "152" -> "Nguyên vật liệu";
              default -> "Chi phí quản lý";
            };

        lines.add(
            createLine(
                voucherId,
                1,
                getAccountId(accounts, purchaseAccount, ctx.getCompanyId()),
                netAmount,
                BigDecimal.ZERO,
                purchaseDesc,
                null,
                null,
                null,
                ctx.getCompanyId()));

        lines.add(
            createLine(
                voucherId,
                2,
                getAccountId(accounts, "1331", ctx.getCompanyId()),
                vatAmount,
                BigDecimal.ZERO,
                "Thuế GTGT đầu vào",
                null,
                null,
                null,
                ctx.getCompanyId()));

        lines.add(
            createLine(
                voucherId,
                3,
                getAccountId(accounts, "331", ctx.getCompanyId()),
                BigDecimal.ZERO,
                totalAmount,
                "Phải trả nhà cung cấp",
                null,
                supplierId,
                null,
                ctx.getCompanyId()));
      }

      case GENERAL_JOURNAL -> {
        int lineCount = 2 + random.nextInt(3);
        List<BigDecimal> amounts = splitAmount(totalAmount, lineCount, random);

        String[] debitAccounts = {"641", "642", "627", "154"};
        String[] creditAccounts = {"334", "338", "214", "335"};

        BigDecimal runningDebit = BigDecimal.ZERO;
        for (int i = 0; i < lineCount - 1; i++) {
          String accountCode = debitAccounts[random.nextInt(debitAccounts.length)];
          lines.add(
              createLine(
                  voucherId,
                  i + 1,
                  getAccountId(accounts, accountCode, ctx.getCompanyId()),
                  amounts.get(i),
                  BigDecimal.ZERO,
                  "Chi phí phân bổ",
                  null,
                  null,
                  null,
                  ctx.getCompanyId()));
          runningDebit = runningDebit.add(amounts.get(i));
        }

        String creditAccountCode = creditAccounts[random.nextInt(creditAccounts.length)];
        lines.add(
            createLine(
                voucherId,
                lineCount,
                getAccountId(accounts, creditAccountCode, ctx.getCompanyId()),
                BigDecimal.ZERO,
                runningDebit,
                "Đối ứng",
                null,
                null,
                null,
                ctx.getCompanyId()));
      }
    }

    return lines;
  }

  private Long getAccountId(Map<String, Long> accounts, String code, Long companyId) {
    Long accountId = accounts.get(code);
    if (accountId == null) {
      for (String key : accounts.keySet()) {
        if (key.startsWith(code)) {
          return accounts.get(key);
        }
      }
      throw new IllegalStateException(
          String.format(
              "Required account not found: code='%s', companyId=%d. "
                  + "Ensure TT200 chart of accounts is seeded before vouchers.",
              code, companyId));
    }
    return accountId;
  }

  private void validateRequiredAccounts(TenantContext ctx) {
    Map<String, Long> accounts = ctx.getAccountsByCode();
    List<String> missingAccounts = new ArrayList<>();

    for (String requiredCode : REQUIRED_TT200_ACCOUNTS) {
      boolean found = accounts.containsKey(requiredCode);
      if (!found) {
        for (String key : accounts.keySet()) {
          if (key.startsWith(requiredCode)) {
            found = true;
            break;
          }
        }
      }
      if (!found) {
        missingAccounts.add(requiredCode);
      }
    }

    if (!missingAccounts.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "Missing required TT200 accounts for companyId=%d: %s. "
                  + "Ensure chart of accounts is seeded before vouchers.",
              ctx.getCompanyId(), missingAccounts));
    }
  }

  private List<BigDecimal> splitAmount(BigDecimal total, int parts, Random random) {
    List<BigDecimal> amounts = new ArrayList<>();
    BigDecimal remaining = total;

    for (int i = 0; i < parts - 1; i++) {
      double ratio = 0.1 + random.nextDouble() * 0.4;
      BigDecimal amount = total.multiply(BigDecimal.valueOf(ratio)).setScale(0, RoundingMode.HALF_UP);
      amount = amount.divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(1000));
      if (amount.compareTo(remaining) >= 0) {
        amount = remaining.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
      }
      amounts.add(amount);
      remaining = remaining.subtract(amount);
    }
    amounts.add(remaining);

    return amounts;
  }

  private VoucherLineData createLine(
      UUID voucherId,
      int lineNumber,
      Long accountId,
      BigDecimal debit,
      BigDecimal credit,
      String description,
      Long customerId,
      Long supplierId,
      Long bankAccountId,
      Long companyId) {
    return new VoucherLineData(
        UUID.randomUUID(),
        voucherId,
        lineNumber,
        accountId,
        debit,
        credit,
        description,
        customerId,
        supplierId,
        null,
        bankAccountId,
        companyId);
  }

  private void batchInsertVouchers(List<VoucherData> vouchers) {
    List<Object[]> batchArgs =
        vouchers.stream()
            .map(
                v ->
                    new Object[] {
                      v.id(),
                      v.companyId(),
                      v.voucherNumber(),
                      java.sql.Date.valueOf(v.voucherDate()),
                      v.periodId(),
                      v.description(),
                      v.status(),
                      v.currency(),
                      v.totalDebit(),
                      v.totalCredit(),
                      v.enteredBy(),
                      v.postedBy(),
                      v.postedAt() != null ? Timestamp.from(v.postedAt()) : null,
                      Timestamp.from(v.createdAt()),
                      Timestamp.from(v.updatedAt()),
                      v.version(),
                      v.isLocked()
                    })
            .toList();

    jdbcTemplate.batchUpdate(
        VOUCHER_INSERT_SQL,
        batchArgs,
        new int[] {
          Types.OTHER,
          Types.BIGINT,
          Types.VARCHAR,
          Types.DATE,
          Types.OTHER,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.VARCHAR,
          Types.NUMERIC,
          Types.NUMERIC,
          Types.BIGINT,
          Types.BIGINT,
          Types.TIMESTAMP,
          Types.TIMESTAMP,
          Types.TIMESTAMP,
          Types.BIGINT,
          Types.BOOLEAN
        });
  }

  private void batchInsertVoucherLines(List<VoucherLineData> lines) {
    List<Object[]> batchArgs =
        lines.stream()
            .map(
                l ->
                    new Object[] {
                      l.id(),
                      l.voucherId(),
                      l.lineNumber(),
                      l.accountId(),
                      l.debit(),
                      l.credit(),
                      l.description(),
                      l.customerId(),
                      l.supplierId(),
                      l.itemId(),
                      l.bankAccountId(),
                      l.companyId()
                    })
            .toList();

    jdbcTemplate.batchUpdate(
        VOUCHER_LINE_INSERT_SQL,
        batchArgs,
        new int[] {
          Types.OTHER,
          Types.OTHER,
          Types.INTEGER,
          Types.BIGINT,
          Types.NUMERIC,
          Types.NUMERIC,
          Types.VARCHAR,
          Types.BIGINT,
          Types.BIGINT,
          Types.BIGINT,
          Types.BIGINT,
          Types.BIGINT
        });
  }
}

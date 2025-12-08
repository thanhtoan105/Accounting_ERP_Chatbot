package com.accounting.service.impl.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.report.AccountContributionDTO;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.entity.report.ReportMapping;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.repository.report.ReportMappingRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.DrillDownService;
import com.accounting.service.PeriodManagementService;

/**
 * Implementation of DrillDownService for navigating from report lines to accounts to vouchers.
 */
@Service
@Transactional(readOnly = true)
public class DrillDownServiceImpl implements DrillDownService {

  private static final Logger logger = LoggerFactory.getLogger(DrillDownServiceImpl.class);

  private final ReportMappingRepository reportMappingRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final VoucherLineRepository voucherLineRepository;
  private final VoucherRepository voucherRepository;
  private final PeriodManagementService periodManagementService;

  public DrillDownServiceImpl(
      ReportMappingRepository reportMappingRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      VoucherLineRepository voucherLineRepository,
      VoucherRepository voucherRepository,
      PeriodManagementService periodManagementService) {
    this.reportMappingRepository = reportMappingRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.voucherLineRepository = voucherLineRepository;
    this.voucherRepository = voucherRepository;
    this.periodManagementService = periodManagementService;
  }

  @Override
  public Page<AccountContributionDTO> getAccountsForLine(
      String reportType, String lineCode, UUID periodId, Pageable pageable) {

    Long companyId = getCompanyId();

    // Get the mapping for this line
    ReportMapping mapping = reportMappingRepository
        .findCurrentByCompanyAndReportTypeAndLineCode(companyId, reportType, lineCode)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            String.format("Mapping not found: %s:%s", reportType, lineCode)));

    // Cannot drill down on calculated lines
    if (Boolean.TRUE.equals(mapping.getIsCalculated())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Cannot drill down on calculated lines. Drill down on component lines instead.");
    }

    // Get period info
    AccountingPeriodDTO period = periodManagementService
        .getPeriodById(periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Parse the account pattern and find matching accounts
    String accountPattern = mapping.getAccountPattern();
    List<ChartOfAccount> matchingAccounts = findMatchingAccounts(companyId, accountPattern);

    if (matchingAccounts.isEmpty()) {
      return new PageImpl<>(List.of(), pageable, 0);
    }

    // Get account balances for the period
    List<Object[]> balances = voucherLineRepository.calculateOpeningBalances(
        companyId, period.getEndDate().plusDays(1));

    // Build map of account ID to balances
    Map<Long, BigDecimal[]> balanceMap = new HashMap<>();
    for (Object[] row : balances) {
      Long accountId = (Long) row[0];
      BigDecimal debit = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
      BigDecimal credit = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
      balanceMap.put(accountId, new BigDecimal[]{debit, credit});
    }

    // Get transaction counts per account for this period
    List<Object[]> transactionCounts = voucherLineRepository.countTransactionsPerAccountForPeriod(
        companyId, period.getStartDate(), period.getEndDate());
    Map<Long, Integer> countMap = new HashMap<>();
    for (Object[] row : transactionCounts) {
      Long accountId = (Long) row[0];
      Long count = (Long) row[1];
      countMap.put(accountId, count != null ? count.intValue() : 0);
    }

    // Build contribution DTOs
    List<AccountContributionDTO> contributions = new ArrayList<>();
    for (ChartOfAccount account : matchingAccounts) {
      BigDecimal[] balance = balanceMap.get(account.getId());
      if (balance != null) {
        AccountContributionDTO dto = new AccountContributionDTO();
        dto.setAccountId(account.getId());
        dto.setAccountCode(account.getCode());
        dto.setAccountName(account.getName());
        dto.setDebitAmount(balance[0]);
        dto.setCreditAmount(balance[1]);
        dto.setNormalBalance(account.getNormalSide());
        dto.calculateNetAmount();

        // Apply sign modifier from mapping
        BigDecimal contribution = dto.getNetAmount();
        if (mapping.getSignModifier() != null && mapping.getSignModifier() == -1) {
          contribution = contribution.negate();
        }
        dto.setContributionAmount(contribution);

        // Set transaction count from the query
        dto.setTransactionCount(countMap.getOrDefault(account.getId(), 0));

        contributions.add(dto);
      }
    }

    // Sort by account code
    contributions.sort((a, b) -> a.getAccountCode().compareTo(b.getAccountCode()));

    // Apply pagination manually (for MVP - ideally use paginated query)
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), contributions.size());

    if (start > contributions.size()) {
      return new PageImpl<>(List.of(), pageable, contributions.size());
    }

    List<AccountContributionDTO> pageContent = contributions.subList(start, end);
    return new PageImpl<>(pageContent, pageable, contributions.size());
  }

  @Override
  public Page<VoucherSummaryDTO> getVouchersForAccount(
      String accountCode, UUID periodId, Pageable pageable) {

    Long companyId = getCompanyId();

    // Get account
    ChartOfAccount account = chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, accountCode)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Account not found: " + accountCode));

    // Get period info
    AccountingPeriodDTO period = periodManagementService
        .getPeriodById(periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Get voucher lines for this account in the period
    // For MVP, we get all lines and filter - full implementation would use paginated query
    List<VoucherLine> lines = voucherLineRepository
        .findByCompanyIdAndAccountId(companyId, account.getId());

    // Filter by period and posted status, then group by voucher
    Map<UUID, List<VoucherLine>> linesByVoucher = lines.stream()
        .collect(Collectors.groupingBy(VoucherLine::getVoucherId));

    List<VoucherSummaryDTO> summaries = new ArrayList<>();
    for (Map.Entry<UUID, List<VoucherLine>> entry : linesByVoucher.entrySet()) {
      UUID voucherId = entry.getKey();
      List<VoucherLine> voucherLines = entry.getValue();

      // Get voucher details
      Voucher voucher = voucherRepository.findById(voucherId).orElse(null);
      if (voucher == null) continue;

      // Filter by period
      if (voucher.getVoucherDate() == null ||
          voucher.getVoucherDate().isBefore(period.getStartDate()) ||
          voucher.getVoucherDate().isAfter(period.getEndDate())) {
        continue;
      }

      // Filter by status (only posted)
      if (!"posted".equalsIgnoreCase(voucher.getStatus())) {
        continue;
      }

      // Calculate totals for this account
      BigDecimal totalDebit = voucherLines.stream()
          .map(VoucherLine::getDebit)
          .filter(d -> d != null)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalCredit = voucherLines.stream()
          .map(VoucherLine::getCredit)
          .filter(c -> c != null)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      VoucherSummaryDTO summary = new VoucherSummaryDTO(
          voucherId,
          voucher.getVoucherNumber(),
          voucher.getVoucherDate(),
          voucher.getDescription(),
          totalDebit,
          totalCredit,
          voucher.getStatus()
      );
      summaries.add(summary);
    }

    // Sort by date descending
    summaries.sort((a, b) -> b.voucherDate().compareTo(a.voucherDate()));

    // Apply pagination
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), summaries.size());

    if (start > summaries.size()) {
      return new PageImpl<>(List.of(), pageable, summaries.size());
    }

    List<VoucherSummaryDTO> pageContent = summaries.subList(start, end);
    return new PageImpl<>(pageContent, pageable, summaries.size());
  }

  @Override
  public VoucherDetailDTO getVoucherDetail(UUID voucherId) {
    Long companyId = getCompanyId();

    // Get voucher with company check
    Voucher voucher = voucherRepository.findById(voucherId)
        .filter(v -> v.getCompanyId().equals(companyId))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Get voucher lines
    List<VoucherLine> lines = voucherLineRepository
        .findByCompanyIdAndVoucherIdOrderByLineNumberAsc(companyId, voucherId);

    // Build line details
    List<VoucherLineDetailDTO> lineDetails = new ArrayList<>();
    for (VoucherLine line : lines) {
      String accountCode = "";
      String accountName = "";

      if (line.getAccountId() != null) {
        ChartOfAccount account = chartOfAccountsRepository
            .findById(line.getAccountId())
            .orElse(null);
        if (account != null) {
          accountCode = account.getCode();
          accountName = account.getName();
        }
      }

      VoucherLineDetailDTO lineDetail = new VoucherLineDetailDTO(
          line.getLineNumber(),
          accountCode,
          accountName,
          line.getDescription(),
          line.getDebit(),
          line.getCredit()
      );
      lineDetails.add(lineDetail);
    }

    // Get attachments (if attachment entity exists)
    // For MVP, return empty list
    List<AttachmentDTO> attachments = List.of();

    // Get creator name (simplified - would need user lookup)
    String createdByName = "";

    return new VoucherDetailDTO(
        voucherId,
        voucher.getVoucherNumber(),
        voucher.getVoucherDate(),
        "", // voucherType - lookup from VoucherType entity if needed
        voucher.getDescription(),
        lineDetails,
        attachments,
        voucher.getStatus(),
        createdByName,
        voucher.getCreatedAt()
    );
  }

  // ==================== Private Helper Methods ====================

  private Long getCompanyId() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return companyId;
  }

  /**
   * Find accounts matching an account pattern.
   * Supports wildcards (e.g., "111*") and comma-separated patterns.
   */
  private List<ChartOfAccount> findMatchingAccounts(Long companyId, String pattern) {
    if (pattern == null || pattern.isBlank()) {
      return List.of();
    }

    // Skip formula patterns
    if (pattern.startsWith("SUM(") || pattern.startsWith("DIFF(")) {
      return List.of();
    }

    List<ChartOfAccount> allMatching = new ArrayList<>();
    String[] patterns = pattern.split(",");

    for (String p : patterns) {
      p = p.trim();
      if (p.endsWith("*")) {
        // Wildcard pattern - use startsWith query
        String prefix = p.substring(0, p.length() - 1);
        List<ChartOfAccount> matches = chartOfAccountsRepository
            .findByCompanyIdAndCodeStartingWith(companyId, prefix);
        allMatching.addAll(matches);
      } else {
        // Exact match
        chartOfAccountsRepository.findByCompanyIdAndCode(companyId, p)
            .ifPresent(allMatching::add);
      }
    }

    // Remove duplicates
    return allMatching.stream()
        .distinct()
        .collect(Collectors.toList());
  }
}

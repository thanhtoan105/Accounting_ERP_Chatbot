package com.accounting.service.impl.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.audit.IntegrityCheckResultDTO;
import com.accounting.dto.audit.IntegrityIssueDTO;
import com.accounting.entity.AuditLog;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.entity.audit.IntegrityCheckResult;
import com.accounting.entity.audit.IntegrityCheckResult.CheckStatus;
import com.accounting.entity.audit.IntegrityCheckResult.CheckType;
import com.accounting.enums.CashBankAuditAction;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.repository.audit.IntegrityCheckResultRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.IntegrityCheckService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


/**
 * Implementation of IntegrityCheckService for Cash & Bank data integrity checks.
 *
 * <p>Performs daily and period-close checks to verify:
 * <ul>
 *   <li>Dr/Cr parity for all vouchers</li>
 *   <li>Duplicate transaction references</li>
 *   <li>Number sequence gaps</li>
 *   <li>Unusual amounts (statistical anomalies)</li>
 *   <li>Repeated blocked attempts</li>
 * </ul>
 */
@Service
@Transactional
public class IntegrityCheckServiceImpl implements IntegrityCheckService {

  private static final Logger logger = LoggerFactory.getLogger(IntegrityCheckServiceImpl.class);
  private static final int BLOCKED_ATTEMPT_THRESHOLD = 3;
  private static final double ANOMALY_STD_DEV_THRESHOLD = 3.0;

  private final VoucherRepository voucherRepository;
  private final VoucherLineRepository voucherLineRepository;
  private final AuditLogRepository auditLogRepository;
  private final IntegrityCheckResultRepository checkResultRepository;
  private final ObjectMapper objectMapper;

  public IntegrityCheckServiceImpl(
      VoucherRepository voucherRepository,
      VoucherLineRepository voucherLineRepository,
      AuditLogRepository auditLogRepository,
      IntegrityCheckResultRepository checkResultRepository,
      ObjectMapper objectMapper) {
    this.voucherRepository = voucherRepository;
    this.voucherLineRepository = voucherLineRepository;
    this.auditLogRepository = auditLogRepository;
    this.checkResultRepository = checkResultRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public IntegrityCheckResultDTO runDailyIntegrityCheck(Long companyId) {
    logger.info("Starting daily integrity check for company {}", companyId);
    long startTime = System.currentTimeMillis();
    UUID checkId = UUID.randomUUID();

    List<IntegrityIssueDTO> allIssues = new ArrayList<>();
    int totalRecordsChecked = 0;

    try {
      // 1. Check Dr/Cr parity for recent vouchers (last 7 days to catch any delays)
      LocalDate fromDate = LocalDate.now().minusDays(7);
      LocalDate toDate = LocalDate.now();

      List<Voucher> vouchers = voucherRepository.findByCompanyIdAndVoucherDateBetween(
          companyId, fromDate, toDate);
      totalRecordsChecked += vouchers.size();

      List<IntegrityIssueDTO> parityIssues = checkDrCrParity(companyId, vouchers);
      allIssues.addAll(parityIssues);

      // 2. Check for duplicate references
      List<IntegrityIssueDTO> duplicateIssues = checkDuplicateReferences(companyId, vouchers);
      allIssues.addAll(duplicateIssues);

      // 3. Check for sequence gaps (all posted vouchers)
      List<IntegrityIssueDTO> gapIssues = checkSequenceGaps(companyId);
      allIssues.addAll(gapIssues);

      // 4. Check for unusual amounts
      List<IntegrityIssueDTO> anomalyIssues = detectUnusualAmounts(companyId, fromDate, toDate);
      allIssues.addAll(anomalyIssues);

      long duration = System.currentTimeMillis() - startTime;

      // Save check result
      IntegrityCheckResult result = new IntegrityCheckResult();
      result.setCompanyId(companyId);
      result.setCheckType(CheckType.DAILY);

      if (allIssues.isEmpty()) {
        result.markPassed(duration, totalRecordsChecked);
        logIntegrityCheckResult(checkId, CheckType.DAILY, true, allIssues);
      } else {
        result.markFailed(duration, totalRecordsChecked, allIssues.size(), toJson(allIssues));
        logIntegrityCheckResult(checkId, CheckType.DAILY, false, allIssues);
      }

      checkResultRepository.save(result);

      logger.info("Daily integrity check completed for company {} in {}ms - {} issues found",
          companyId, duration, allIssues.size());

      return allIssues.isEmpty()
          ? IntegrityCheckResultDTO.passed(checkId, "DAILY", duration, totalRecordsChecked)
          : IntegrityCheckResultDTO.failed(checkId, "DAILY", duration, totalRecordsChecked, allIssues);

    } catch (Exception e) {
      logger.error("Error during daily integrity check for company {}", companyId, e);

      IntegrityCheckResult result = new IntegrityCheckResult();
      result.setCompanyId(companyId);
      result.setCheckType(CheckType.DAILY);
      result.setStatus(CheckStatus.ERROR);
      result.setDurationMs(System.currentTimeMillis() - startTime);
      checkResultRepository.save(result);

      throw e;
    }
  }

  @Override
  public IntegrityCheckResultDTO runPeriodCloseCheck(UUID periodId) {
    Long companyId = CompanyContext.getCompanyId();
    logger.info("Starting period-close integrity check for period {} company {}",
        periodId, companyId);
    long startTime = System.currentTimeMillis();
    UUID checkId = UUID.randomUUID();

    List<IntegrityIssueDTO> allIssues = new ArrayList<>();
    int totalRecordsChecked = 0;

    try {
      // Get vouchers for the period
      List<Voucher> vouchers = voucherRepository.findByCompanyIdAndPeriodId(companyId, periodId);
      totalRecordsChecked += vouchers.size();

      // 1. All daily checks
      List<IntegrityIssueDTO> parityIssues = checkDrCrParity(companyId, vouchers);
      allIssues.addAll(parityIssues);

      List<IntegrityIssueDTO> duplicateIssues = checkDuplicateReferences(companyId, vouchers);
      allIssues.addAll(duplicateIssues);

      // 2. Period-specific: check all vouchers are posted
      long unpostedCount = vouchers.stream()
          .filter(v -> !"posted".equals(v.getStatus()))
          .count();
      if (unpostedCount > 0) {
        IntegrityIssueDTO issue = new IntegrityIssueDTO();
        issue.setType("UNPOSTED_VOUCHERS");
        issue.setSeverity("HIGH");
        issue.setDescription("Period has unposted vouchers");
        issue.setDetails(String.format("%d vouchers are not posted", unpostedCount));
        allIssues.add(issue);
      }

      long duration = System.currentTimeMillis() - startTime;

      // Save check result
      IntegrityCheckResult result = new IntegrityCheckResult();
      result.setCompanyId(companyId);
      result.setCheckType(CheckType.PERIOD_CLOSE);
      result.setPeriodId(periodId);

      if (allIssues.isEmpty()) {
        result.markPassed(duration, totalRecordsChecked);
        logIntegrityCheckResult(checkId, CheckType.PERIOD_CLOSE, true, allIssues);
      } else {
        result.markFailed(duration, totalRecordsChecked, allIssues.size(), toJson(allIssues));
        logIntegrityCheckResult(checkId, CheckType.PERIOD_CLOSE, false, allIssues);
      }

      checkResultRepository.save(result);

      return allIssues.isEmpty()
          ? IntegrityCheckResultDTO.passed(checkId, "PERIOD_CLOSE", duration, totalRecordsChecked)
          : IntegrityCheckResultDTO.failed(checkId, "PERIOD_CLOSE", duration, totalRecordsChecked, allIssues);

    } catch (Exception e) {
      logger.error("Error during period-close check for period {}", periodId, e);
      throw e;
    }
  }

  @Override
  public List<IntegrityIssueDTO> detectAnomalies(Long companyId, LocalDate from, LocalDate to) {
    List<IntegrityIssueDTO> issues = new ArrayList<>();

    // Detect unusual amounts
    issues.addAll(detectUnusualAmounts(companyId, from, to));

    // Detect repeated blocked attempts
    issues.addAll(checkRepeatedBlockedAttempts(companyId));

    // Log anomalies
    for (IntegrityIssueDTO issue : issues) {
      logAnomalyDetected(issue);
    }

    return issues;
  }

  @Override
  public List<IntegrityIssueDTO> checkRepeatedBlockedAttempts(Long companyId) {
    List<IntegrityIssueDTO> issues = new ArrayList<>();

    // Look for PERIOD_BLOCK_ATTEMPT actions in the last hour
    Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

    Specification<AuditLog> spec = (root, query, cb) -> cb.and(
        cb.equal(root.get("companyId"), companyId),
        cb.equal(root.get("action"), CashBankAuditAction.PERIOD_BLOCK_ATTEMPT.getValue()),
        cb.greaterThanOrEqualTo(root.get("createdAt"), oneHourAgo));

    List<AuditLog> blockedAttempts = auditLogRepository.findAll(spec);

    // Group by user
    Map<Long, List<AuditLog>> byUser = blockedAttempts.stream()
        .filter(log -> log.getUserId() != null)
        .collect(Collectors.groupingBy(AuditLog::getUserId));

    for (Map.Entry<Long, List<AuditLog>> entry : byUser.entrySet()) {
      if (entry.getValue().size() >= BLOCKED_ATTEMPT_THRESHOLD) {
        String email = entry.getValue().get(0).getEmail();
        issues.add(IntegrityIssueDTO.repeatedBlockedAttempts(
            entry.getKey(), email, entry.getValue().size(), 1));
      }
    }

    return issues;
  }

  @Override
  @Transactional(readOnly = true)
  public IntegrityCheckResultDTO getLastCheckResult(Long companyId) {
    return checkResultRepository.findFirstByCompanyIdOrderByExecutedAtDesc(companyId)
        .map(this::toDto)
        .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<IntegrityCheckResultDTO> getCheckHistory(Long companyId, int limit) {
    return checkResultRepository
        .findByCompanyIdOrderByExecutedAtDesc(companyId, PageRequest.of(0, limit))
        .stream()
        .map(this::toDto)
        .toList();
  }

  // === Private Check Methods ===

  private List<IntegrityIssueDTO> checkDrCrParity(Long companyId, List<Voucher> vouchers) {
    List<IntegrityIssueDTO> issues = new ArrayList<>();

    for (Voucher voucher : vouchers) {
      List<VoucherLine> lines = voucherLineRepository
          .findByCompanyIdAndVoucherIdOrderByLineNumberAsc(companyId, voucher.getId());

      BigDecimal totalDebit = BigDecimal.ZERO;
      BigDecimal totalCredit = BigDecimal.ZERO;

      for (VoucherLine line : lines) {
        totalDebit = totalDebit.add(line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO);
        totalCredit = totalCredit.add(line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO);
      }

      // Check if they balance (with small tolerance for rounding)
      BigDecimal difference = totalDebit.subtract(totalCredit).abs();
      if (difference.compareTo(new BigDecimal("0.01")) > 0) {
        issues.add(IntegrityIssueDTO.drCrImbalance(
            "Voucher", voucher.getId().toString(), totalDebit, totalCredit));
      }
    }

    return issues;
  }

  private List<IntegrityIssueDTO> checkDuplicateReferences(Long companyId, List<Voucher> vouchers) {
    List<IntegrityIssueDTO> issues = new ArrayList<>();

    // Group by voucher number
    Map<String, List<Voucher>> byNumber = vouchers.stream()
        .filter(v -> v.getVoucherNumber() != null)
        .collect(Collectors.groupingBy(Voucher::getVoucherNumber));

    for (Map.Entry<String, List<Voucher>> entry : byNumber.entrySet()) {
      if (entry.getValue().size() > 1) {
        issues.add(IntegrityIssueDTO.duplicateReference(
            "Voucher",
            entry.getValue().get(0).getId().toString(),
            entry.getKey(),
            entry.getValue().size()));
      }
    }

    return issues;
  }

  private List<IntegrityIssueDTO> checkSequenceGaps(Long companyId) {
    List<IntegrityIssueDTO> issues = new ArrayList<>();

    // Get all posted voucher numbers for this company, sorted
    List<Voucher> postedVouchers = voucherRepository.findByCompanyIdAndStatus(companyId, "posted");

    // Group by prefix (e.g., "REC-", "PAY-")
    Map<String, List<String>> byPrefix = new HashMap<>();
    for (Voucher v : postedVouchers) {
      if (v.getVoucherNumber() == null) continue;

      String number = v.getVoucherNumber();
      String prefix = extractPrefix(number);
      byPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(number);
    }

    // Check each prefix for gaps
    for (Map.Entry<String, List<String>> entry : byPrefix.entrySet()) {
      List<String> numbers = entry.getValue();
      numbers.sort(String::compareTo);

      for (int i = 0; i < numbers.size() - 1; i++) {
        int current = extractNumber(numbers.get(i));
        int next = extractNumber(numbers.get(i + 1));

        if (next - current > 1) {
          issues.add(IntegrityIssueDTO.sequenceGap("Voucher", numbers.get(i), numbers.get(i + 1)));
        }
      }
    }

    return issues;
  }

  private List<IntegrityIssueDTO> detectUnusualAmounts(Long companyId, LocalDate from, LocalDate to) {
    List<IntegrityIssueDTO> issues = new ArrayList<>();

    List<Voucher> vouchers = voucherRepository.findByCompanyIdAndVoucherDateBetween(
        companyId, from, to);

    if (vouchers.isEmpty()) return issues;

    // Calculate average and std dev of voucher totals
    List<BigDecimal> amounts = new ArrayList<>();
    Map<UUID, BigDecimal> voucherAmounts = new HashMap<>();

    for (Voucher voucher : vouchers) {
      List<VoucherLine> lines = voucherLineRepository
          .findByCompanyIdAndVoucherIdOrderByLineNumberAsc(companyId, voucher.getId());

      BigDecimal total = BigDecimal.ZERO;
      for (VoucherLine line : lines) {
        total = total.add(line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO);
      }

      amounts.add(total);
      voucherAmounts.put(voucher.getId(), total);
    }

    if (amounts.size() < 3) return issues; // Not enough data for statistics

    BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal average = sum.divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP);

    // Calculate standard deviation
    BigDecimal variance = BigDecimal.ZERO;
    for (BigDecimal amount : amounts) {
      BigDecimal diff = amount.subtract(average);
      variance = variance.add(diff.multiply(diff));
    }
    variance = variance.divide(BigDecimal.valueOf(amounts.size()), 4, RoundingMode.HALF_UP);
    double stdDev = Math.sqrt(variance.doubleValue());

    if (stdDev == 0) return issues;

    // Check each voucher against threshold
    for (Voucher voucher : vouchers) {
      BigDecimal amount = voucherAmounts.get(voucher.getId());
      if (amount == null) continue;

      double deviation = Math.abs(amount.subtract(average).doubleValue() / stdDev);

      if (deviation > ANOMALY_STD_DEV_THRESHOLD) {
        issues.add(IntegrityIssueDTO.unusualAmount(
            "Voucher", voucher.getId().toString(), amount, average, deviation));
      }
    }

    return issues;
  }

  // === Helper Methods ===

  private String extractPrefix(String voucherNumber) {
    int lastDash = voucherNumber.lastIndexOf('-');
    if (lastDash > 0) {
      return voucherNumber.substring(0, lastDash + 1);
    }
    return "";
  }

  private int extractNumber(String voucherNumber) {
    try {
      int lastDash = voucherNumber.lastIndexOf('-');
      if (lastDash >= 0 && lastDash < voucherNumber.length() - 1) {
        return Integer.parseInt(voucherNumber.substring(lastDash + 1));
      }
      return Integer.parseInt(voucherNumber);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private IntegrityCheckResultDTO toDto(IntegrityCheckResult result) {
    IntegrityCheckResultDTO dto = new IntegrityCheckResultDTO();
    dto.setCheckId(result.getId());
    dto.setCheckType(result.getCheckType().name());
    dto.setPassed(result.getStatus() == CheckStatus.PASSED);
    dto.setExecutedAt(result.getExecutedAt());
    dto.setDuration(result.getDurationMs() != null ? result.getDurationMs() : 0);
    dto.setRecordsChecked(result.getRecordsChecked() != null ? result.getRecordsChecked() : 0);
    dto.setIssueCount(result.getIssueCount() != null ? result.getIssueCount() : 0);
    dto.setAlertsSent(result.getAlertsSent() != null && result.getAlertsSent());

    if (result.getIssuesJson() != null) {
      try {
        List<IntegrityIssueDTO> issues = objectMapper.readValue(
            result.getIssuesJson(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, IntegrityIssueDTO.class));
        dto.setIssues(issues);
      } catch (JsonProcessingException e) {
        dto.setIssues(List.of());
      }
    } else {
      dto.setIssues(List.of());
    }

    return dto;
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }

  // === Audit Logging ===

  private void logIntegrityCheckResult(UUID checkId, CheckType checkType,
      boolean passed, List<IntegrityIssueDTO> issues) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("checkType", checkType.name());
      metadata.put("checkId", checkId.toString());
      metadata.put("issueCount", issues.size());

      if (!issues.isEmpty()) {
        int highCount = (int) issues.stream()
            .filter(i -> "HIGH".equals(i.getSeverity())).count();
        int medCount = (int) issues.stream()
            .filter(i -> "MEDIUM".equals(i.getSeverity())).count();
        int lowCount = (int) issues.stream()
            .filter(i -> "LOW".equals(i.getSeverity())).count();

        metadata.put("highSeverity", highCount);
        metadata.put("mediumSeverity", medCount);
        metadata.put("lowSeverity", lowCount);
      }

      AuditLog log = new AuditLog();
      log.setAction(passed
          ? CashBankAuditAction.INTEGRITY_CHECK_PASS.getValue()
          : CashBankAuditAction.INTEGRITY_CHECK_FAIL.getValue());
      log.setEventType("CASH_BANK_AUDIT");
      log.setCompanyId(CompanyContext.getCompanyId());
      log.setMetadata(metadata);
      log.setSuccess(true);
      log.setCreatedAt(Instant.now());

      auditLogRepository.save(log);
    } catch (Exception e) {
      logger.warn("Failed to log integrity check result", e);
    }
  }

  private void logAnomalyDetected(IntegrityIssueDTO issue) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("anomalyType", issue.getType());
      metadata.put("severity", issue.getSeverity());
      metadata.put("details", issue.getDetails());
      if (issue.getEntityType() != null) {
        metadata.put("entityType", issue.getEntityType());
      }
      if (issue.getEntityId() != null) {
        metadata.put("entityId", issue.getEntityId());
      }
      if (issue.getAmount() != null) {
        metadata.put("amount", issue.getAmount().toString());
      }

      AuditLog log = new AuditLog();
      log.setAction(CashBankAuditAction.ANOMALY_DETECTED.getValue());
      log.setEventType("CASH_BANK_AUDIT");
      log.setCompanyId(CompanyContext.getCompanyId());
      log.setEntityType(issue.getEntityType());
      log.setEntityId(issue.getEntityId());
      log.setMetadata(metadata);
      log.setSuccess(true);
      log.setCreatedAt(Instant.now());

      auditLogRepository.save(log);
    } catch (Exception e) {
      logger.warn("Failed to log anomaly", e);
    }
  }
}

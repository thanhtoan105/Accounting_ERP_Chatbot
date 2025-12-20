package com.accounting.service.impl.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.report.ComparisonSettingsDTO;
import com.accounting.dto.report.DetailedLedgerDTO;
import com.accounting.dto.report.MultiPeriodLineDTO;
import com.accounting.dto.report.MultiPeriodReportDTO;
import com.accounting.dto.report.PeriodColumnDTO;
import com.accounting.dto.report.StatutoryReportDTO;
import com.accounting.dto.report.StatutoryReportLineDTO;
import com.accounting.dto.report.ValidationErrorDTO;
import com.accounting.dto.report.ValidationResultDTO;
import com.accounting.dto.report.VarianceDTO;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.report.ReportMapping;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.report.ReportMappingRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.CompanyService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.StatutoryReportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementation of StatutoryReportService for generating TT200 statutory reports.
 * Follows patterns from TrialBalanceServiceImpl for consistency.
 */
@Service
@Transactional(readOnly = true)
public class StatutoryReportServiceImpl implements StatutoryReportService {

  private static final Logger logger = LoggerFactory.getLogger(StatutoryReportServiceImpl.class);

  // Report type constants
  private static final String REPORT_B01 = "B01";
  private static final String REPORT_B02 = "B02";
  private static final String REPORT_B03 = "B03";

  private static final int MAX_PERIODS_FOR_COMPARISON = 4;

  // Report names
  private static final Map<String, String[]> REPORT_NAMES = Map.of(
      REPORT_B01, new String[] {"Bảng cân đối kế toán", "Balance Sheet"},
      REPORT_B02, new String[] {"Báo cáo kết quả hoạt động kinh doanh", "Income Statement"},
      REPORT_B03, new String[] {"Báo cáo lưu chuyển tiền tệ", "Cash Flow Statement"});

  private final ReportMappingRepository reportMappingRepository;
  private final VoucherLineRepository voucherLineRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final PeriodManagementService periodManagementService;
  private final CompanyService companyService;
  private final CompanySettingsRepository companySettingsRepository;
  private final ObjectMapper objectMapper;

  public StatutoryReportServiceImpl(
      ReportMappingRepository reportMappingRepository,
      VoucherLineRepository voucherLineRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      PeriodManagementService periodManagementService,
      CompanyService companyService,
      CompanySettingsRepository companySettingsRepository,
      ObjectMapper objectMapper) {
    this.reportMappingRepository = reportMappingRepository;
    this.voucherLineRepository = voucherLineRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.periodManagementService = periodManagementService;
    this.companyService = companyService;
    this.companySettingsRepository = companySettingsRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public StatutoryReportDTO generateBalanceSheet(UUID periodId, UUID comparisonPeriodId) {
    return generateReport(REPORT_B01, periodId, comparisonPeriodId);
  }

  @Override
  public StatutoryReportDTO generateIncomeStatement(UUID periodId, UUID comparisonPeriodId) {
    return generateReport(REPORT_B02, periodId, comparisonPeriodId);
  }

  @Override
  public StatutoryReportDTO generateCashFlowStatement(UUID periodId) {
    // B03 typically doesn't have comparison in the same way
    return generateReport(REPORT_B03, periodId, null);
  }

  /**
   * Core report generation logic.
   */
  private StatutoryReportDTO generateReport(String reportType, UUID periodId, UUID comparisonPeriodId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Get period information
    AccountingPeriodDTO period = periodManagementService
        .getPeriodById(periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Get comparison period if provided
    AccountingPeriodDTO comparisonPeriod = null;
    if (comparisonPeriodId != null) {
      comparisonPeriod = periodManagementService
          .getPeriodById(comparisonPeriodId)
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.NOT_FOUND, "Comparison period not found: " + comparisonPeriodId));
    }

    // Get company information
    Company company = companyService.getCurrentCompanySettings();
    if (company == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company settings not found");
    }

    // Load current mappings
    List<ReportMapping> mappings = reportMappingRepository
        .findCurrentByCompanyAndReportType(companyId, reportType);

    if (mappings.isEmpty()) {
      logger.warn("No mappings found for company {} and report type {}", companyId, reportType);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "No report mappings configured for report type: " + reportType);
    }

    // Calculate account balances for current period
    Map<String, BigDecimal> currentBalances = calculateAccountBalances(
        companyId, period.getStartDate(), period.getEndDate(), reportType);

    // Calculate account balances for comparison period if provided
    Map<String, BigDecimal> priorBalances = new HashMap<>();
    if (comparisonPeriod != null) {
      priorBalances = calculateAccountBalances(
          companyId, comparisonPeriod.getStartDate(), comparisonPeriod.getEndDate(), reportType);
    }

    // Build report lines
    List<StatutoryReportLineDTO> lines = new ArrayList<>();
    Map<String, BigDecimal> lineValues = new HashMap<>();

    // First pass: calculate non-calculated lines (from GL accounts)
    for (ReportMapping mapping : mappings) {
      if (!Boolean.TRUE.equals(mapping.getIsCalculated())) {
        BigDecimal currentAmount = calculateLineValue(mapping, currentBalances);
        BigDecimal priorAmount = comparisonPeriod != null
            ? calculateLineValue(mapping, priorBalances)
            : BigDecimal.ZERO;

        lineValues.put(mapping.getLineCode(), currentAmount);

        StatutoryReportLineDTO lineDTO = createLineDTO(mapping, currentAmount, priorAmount);
        lines.add(lineDTO);
      }
    }

    // Second pass: calculate calculated lines (from formulas)
    for (ReportMapping mapping : mappings) {
      if (Boolean.TRUE.equals(mapping.getIsCalculated())) {
        BigDecimal currentAmount = calculateFormulaValue(mapping.getFormula(), lineValues);
        lineValues.put(mapping.getLineCode(), currentAmount);

        // Calculate prior value if comparison period
        BigDecimal priorAmount = BigDecimal.ZERO;
        if (comparisonPeriod != null) {
          Map<String, BigDecimal> priorLineValues = new HashMap<>();
          // Recalculate non-calculated lines for prior period
          for (ReportMapping m : mappings) {
            if (!Boolean.TRUE.equals(m.getIsCalculated())) {
              priorLineValues.put(m.getLineCode(), calculateLineValue(m, priorBalances));
            }
          }
          // Calculate formula for prior
          for (ReportMapping m : mappings) {
            if (Boolean.TRUE.equals(m.getIsCalculated())) {
              priorLineValues.put(m.getLineCode(),
                  calculateFormulaValue(m.getFormula(), priorLineValues));
            }
          }
          priorAmount = priorLineValues.getOrDefault(mapping.getLineCode(), BigDecimal.ZERO);
        }

        StatutoryReportLineDTO lineDTO = createLineDTO(mapping, currentAmount, priorAmount);
        lines.add(lineDTO);
      }
    }

    // Sort by display order
    lines.sort((a, b) -> {
      ReportMapping ma = findMappingByLineCode(mappings, a.getLineCode());
      ReportMapping mb = findMappingByLineCode(mappings, b.getLineCode());
      int orderA = ma != null ? ma.getDisplayOrder() : 0;
      int orderB = mb != null ? mb.getDisplayOrder() : 0;
      return Integer.compare(orderA, orderB);
    });

    // Build the report DTO
    StatutoryReportDTO report = new StatutoryReportDTO();
    report.setReportId(UUID.randomUUID());
    report.setReportType(reportType);

    String[] names = REPORT_NAMES.get(reportType);
    report.setReportName(names != null ? names[0] : reportType);
    report.setReportNameEnglish(names != null ? names[1] : reportType);

    // Period info
    report.setPeriodId(periodId);
    report.setPeriodName(period.getPeriodName());
    report.setPeriodStartDate(period.getStartDate());
    report.setPeriodEndDate(period.getEndDate());

    // Comparison period info
    if (comparisonPeriod != null) {
      report.setComparisonPeriodId(comparisonPeriodId);
      report.setComparisonPeriodName(comparisonPeriod.getPeriodName());
      report.setComparisonPeriodStartDate(comparisonPeriod.getStartDate());
      report.setComparisonPeriodEndDate(comparisonPeriod.getEndDate());
    }

    // Company info
    report.setCompanyId(companyId);
    report.setCompanyName(company.getName());
    report.setCompanyTaxCode(company.getTaxCode());
    report.setCompanyAddress(company.getAddress());

    report.setLines(lines);
    report.setMappingVersion(mappings.get(0).getVersion());
    report.setGeneratedAt(Instant.now());

    // Check if period is open (draft)
    boolean isDraft = PeriodStatus.OPEN.equals(period.getStatus());
    report.setDraft(isDraft);

    // Calculate totals based on report type
    calculateReportTotals(report, lineValues, reportType);

    // Validate balance for B01
    if (REPORT_B01.equals(reportType)) {
      BigDecimal assets = lineValues.getOrDefault("270", BigDecimal.ZERO);
      BigDecimal liabilitiesAndEquity = lineValues.getOrDefault("440", BigDecimal.ZERO);
      boolean isBalanced = assets.compareTo(liabilitiesAndEquity) == 0;
      report.setBalanced(isBalanced);

      if (!isBalanced) {
        report.addValidationWarning(String.format(
            "Balance Sheet is not balanced: Assets (%s) != Liabilities + Equity (%s)",
            assets, liabilitiesAndEquity));
      }
    }

    return report;
  }

  /**
   * Calculate account balances for reporting.
   * For B01 (Balance Sheet): cumulative balances up to end date
   * For B02 (Income Statement) and B03 (Cash Flow): period activity only
   *
   * @param companyId  company ID
   * @param startDate  period start date
   * @param endDate    period end date
   * @param reportType report type (B01, B02, B03)
   * @return map of account code to net balance
   */
  private Map<String, BigDecimal> calculateAccountBalances(
      Long companyId, LocalDate startDate, LocalDate endDate, String reportType) {

    Map<String, BigDecimal> balances = new HashMap<>();

    // Get all accounts for the company
    List<ChartOfAccount> accounts = chartOfAccountsRepository.findByCompanyId(companyId);
    Map<Long, ChartOfAccount> accountMap = accounts.stream()
        .collect(Collectors.toMap(ChartOfAccount::getId, a -> a));

    List<Object[]> accountBalances;

    if (REPORT_B01.equals(reportType)) {
      // Balance Sheet: cumulative balances up to end date (point-in-time position)
      // Uses all transactions from inception through end of period
      accountBalances = voucherLineRepository.calculateOpeningBalances(companyId, endDate.plusDays(1));
    } else {
      // Income Statement (B02) and Cash Flow (B03): period activity only
      // Uses only transactions within the specified date range
      // This is the key fix - B02/B03 should NOT use cumulative balances
      accountBalances = voucherLineRepository.calculatePeriodActivity(companyId, startDate, endDate);
    }

    for (Object[] row : accountBalances) {
      Long accountId = (Long) row[0];
      BigDecimal debit = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
      BigDecimal credit = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;

      ChartOfAccount account = accountMap.get(accountId);
      if (account != null) {
        String code = account.getCode();
        // Calculate net balance based on normal balance direction
        BigDecimal netBalance;
        if ("CREDIT".equalsIgnoreCase(account.getNormalSide())) {
          netBalance = credit.subtract(debit);
        } else {
          netBalance = debit.subtract(credit);
        }
        balances.put(code, netBalance);
      }
    }

    return balances;
  }

  /**
   * Calculate line value from account pattern.
   * Supports wildcards (e.g., "111*") and comma-separated patterns.
   */
  private BigDecimal calculateLineValue(ReportMapping mapping, Map<String, BigDecimal> accountBalances) {
    String pattern = mapping.getAccountPattern();
    if (pattern == null || pattern.isBlank()) {
      return BigDecimal.ZERO;
    }

    // Skip if this is a formula-based pattern (starts with SUM, DIFF, etc.)
    if (pattern.startsWith("SUM(") || pattern.startsWith("DIFF(")) {
      return BigDecimal.ZERO;
    }

    BigDecimal total = BigDecimal.ZERO;
    String[] patterns = pattern.split(",");

    for (String p : patterns) {
      p = p.trim();
      if (p.endsWith("*")) {
        // Wildcard pattern
        String prefix = p.substring(0, p.length() - 1);
        for (Map.Entry<String, BigDecimal> entry : accountBalances.entrySet()) {
          if (entry.getKey().startsWith(prefix)) {
            total = total.add(entry.getValue());
          }
        }
      } else {
        // Exact match
        BigDecimal value = accountBalances.getOrDefault(p, BigDecimal.ZERO);
        total = total.add(value);
      }
    }

    // Apply sign modifier
    if (mapping.getSignModifier() != null && mapping.getSignModifier() == -1) {
      total = total.negate();
    }

    return total;
  }

  /**
   * Calculate value from formula (e.g., "01-11", "20+21-22").
   */
  private BigDecimal calculateFormulaValue(String formula, Map<String, BigDecimal> lineValues) {
    if (formula == null || formula.isBlank()) {
      return BigDecimal.ZERO;
    }

    try {
      // Simple formula parser for +, - operations
      // Split by operators while keeping them
      String[] tokens = formula.split("(?=[+-])|(?<=[+-])");
      BigDecimal result = BigDecimal.ZERO;
      char operator = '+';

      for (String token : tokens) {
        token = token.trim();
        if (token.equals("+")) {
          operator = '+';
        } else if (token.equals("-")) {
          operator = '-';
        } else if (!token.isEmpty()) {
          BigDecimal value = lineValues.getOrDefault(token, BigDecimal.ZERO);
          if (operator == '+') {
            result = result.add(value);
          } else {
            result = result.subtract(value);
          }
        }
      }

      return result;
    } catch (Exception e) {
      logger.warn("Failed to parse formula '{}': {}", formula, e.getMessage());
      return BigDecimal.ZERO;
    }
  }

  /**
   * Create a StatutoryReportLineDTO from a mapping and calculated values.
   */
  private StatutoryReportLineDTO createLineDTO(
      ReportMapping mapping, BigDecimal currentAmount, BigDecimal priorAmount) {

    StatutoryReportLineDTO dto = new StatutoryReportLineDTO();
    dto.setLineCode(mapping.getLineCode());
    dto.setLineName(mapping.getLineName());
    dto.setLineNameEnglish(mapping.getLineNameEnglish());
    dto.setLevel(mapping.getLevel());
    dto.setCalculated(Boolean.TRUE.equals(mapping.getIsCalculated()));
    dto.setFormula(mapping.getFormula());
    dto.setAccountPattern(mapping.getAccountPattern());
    dto.setCurrentAmount(currentAmount);
    dto.setPriorAmount(priorAmount);
    dto.calculateVariance();

    // Non-calculated lines can be drilled down
    dto.setHasDrillDown(!Boolean.TRUE.equals(mapping.getIsCalculated()));

    return dto;
  }

  /**
   * Find mapping by line code.
   */
  private ReportMapping findMappingByLineCode(List<ReportMapping> mappings, String lineCode) {
    return mappings.stream()
        .filter(m -> m.getLineCode().equals(lineCode))
        .findFirst()
        .orElse(null);
  }

  /**
   * Calculate and set report totals based on report type.
   */
  private void calculateReportTotals(
      StatutoryReportDTO report, Map<String, BigDecimal> lineValues, String reportType) {

    switch (reportType) {
      case REPORT_B01 -> {
        report.setTotalAssets(lineValues.getOrDefault("270", BigDecimal.ZERO));
        report.setTotalLiabilities(lineValues.getOrDefault("300", BigDecimal.ZERO));
        report.setTotalEquity(lineValues.getOrDefault("400", BigDecimal.ZERO));
      }
      case REPORT_B02 -> {
        report.setTotalRevenue(lineValues.getOrDefault("10", BigDecimal.ZERO));
        report.setNetIncome(lineValues.getOrDefault("60", BigDecimal.ZERO));
      }
      case REPORT_B03 -> {
        report.setNetCashFlow(lineValues.getOrDefault("50", BigDecimal.ZERO));
      }
    }
  }

  @Override
  public DetailedLedgerDTO generateDetailedLedger(
      String[] accountCodes, UUID periodId, String subsidiaryType, Long subsidiaryId) {

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Get period information
    AccountingPeriodDTO period = periodManagementService
        .getPeriodById(periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    Company company = companyService.getCurrentCompanySettings();

    DetailedLedgerDTO ledger = new DetailedLedgerDTO();
    ledger.setCompanyId(companyId);
    ledger.setCompanyName(company != null ? company.getName() : "");
    ledger.setPeriodId(periodId);
    ledger.setPeriodName(period.getPeriodName());
    ledger.setStartDate(period.getStartDate());
    ledger.setEndDate(period.getEndDate());

    // For MVP, return basic structure
    // Full implementation would query voucher_lines with filters

    if (accountCodes != null && accountCodes.length > 0) {
      ledger.setAccountCode(accountCodes[0]);
      // Look up account name
      chartOfAccountsRepository.findByCompanyIdAndCode(companyId, accountCodes[0])
          .ifPresent(account -> ledger.setAccountName(account.getName()));
    }

    ledger.setSubsidiaryType(subsidiaryType);
    ledger.setSubsidiaryId(subsidiaryId);

    // Calculate opening balance and transaction lines
    // This is a simplified implementation - full version would query and populate lines

    return ledger;
  }

  @Override
  public ValidationResultDTO validateForExport(UUID periodId, String reportType) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    List<ValidationErrorDTO> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // Get period status
    AccountingPeriodDTO period = periodManagementService
        .getPeriodById(periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    boolean isDraft = PeriodStatus.OPEN.equals(period.getStatus());
    String periodStatus = isDraft ? "OPEN" : "CLOSED";
    if (isDraft) {
      warnings.add("Period is still open. Report will be marked as DRAFT.");
    }

    // Generate report to check for issues
    StatutoryReportDTO report;
    try {
      report = switch (reportType) {
        case REPORT_B01 -> generateBalanceSheet(periodId, null);
        case REPORT_B02 -> generateIncomeStatement(periodId, null);
        case REPORT_B03 -> generateCashFlowStatement(periodId);
        default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Unknown report type: " + reportType);
      };
    } catch (Exception e) {
      errors.add(ValidationErrorDTO.generationFailed(e.getMessage()));
      return new ValidationResultDTO(false, errors, warnings, periodStatus, false);
    }

    // Check for NULL values in required lines
    for (StatutoryReportLineDTO line : report.getLines()) {
      if (line.getCurrentAmount() == null) {
        errors.add(ValidationErrorDTO.nullValue(line.getLineCode(), line.getLineName()));
      }
    }

    // Add any warnings from report generation
    warnings.addAll(report.getValidationWarnings());

    boolean isBalanced = report.isBalanced();
    if (!isBalanced && REPORT_B01.equals(reportType)) {
      // Get the actual values for the imbalance error message
      BigDecimal assets = report.getTotalAssets() != null ? report.getTotalAssets() : BigDecimal.ZERO;
      BigDecimal liabilitiesEquity = report.getTotalLiabilities() != null && report.getTotalEquity() != null
          ? report.getTotalLiabilities().add(report.getTotalEquity())
          : BigDecimal.ZERO;
      errors.add(ValidationErrorDTO.imbalance(assets.toString(), liabilitiesEquity.toString()));
    }

    boolean valid = errors.isEmpty();
    return new ValidationResultDTO(valid, errors, warnings, periodStatus, isBalanced);
  }

  @Override
  public MultiPeriodReportDTO generateMultiPeriodReport(String reportType, List<UUID> periodIds) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    if (periodIds == null || periodIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one period required");
    }
    if (periodIds.size() > MAX_PERIODS_FOR_COMPARISON) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Maximum " + MAX_PERIODS_FOR_COMPARISON + " periods allowed for comparison");
    }

    if (!REPORT_NAMES.containsKey(reportType)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown report type: " + reportType);
    }

    List<AccountingPeriodDTO> periods = new ArrayList<>();
    for (UUID periodId : periodIds) {
      AccountingPeriodDTO period =
          periodManagementService
              .getPeriodById(periodId)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, "Period not found: " + periodId));

      if (!companyId.equals(period.getCompanyId())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Period " + periodId + " belongs to a different company");
      }
      periods.add(period);
    }

    periods.sort((a, b) -> a.getStartDate().compareTo(b.getStartDate()));

    Company company = companyService.getCurrentCompanySettings();
    if (company == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company settings not found");
    }

    List<StatutoryReportDTO> singlePeriodReports = new ArrayList<>();
    for (AccountingPeriodDTO period : periods) {
      StatutoryReportDTO report = generateReport(reportType, period.getId(), null);
      singlePeriodReports.add(report);
    }

    List<PeriodColumnDTO> periodColumns = new ArrayList<>();
    boolean hasDraftPeriod = false;
    for (AccountingPeriodDTO period : periods) {
      boolean isDraft = PeriodStatus.OPEN.equals(period.getStatus());
      if (isDraft) {
        hasDraftPeriod = true;
      }
      periodColumns.add(
          new PeriodColumnDTO(
              period.getId(),
              period.getPeriodName(),
              period.getStartDate(),
              period.getEndDate(),
              period.getFiscalYear() != null ? period.getFiscalYear().toString() : null,
              isDraft));
    }

    Map<String, List<BigDecimal>> lineValuesMap = new LinkedHashMap<>();
    Map<String, StatutoryReportLineDTO> lineMetaMap = new HashMap<>();

    if (!singlePeriodReports.isEmpty()) {
      for (StatutoryReportLineDTO line : singlePeriodReports.get(0).getLines()) {
        lineValuesMap.put(line.getLineCode(), new ArrayList<>());
        lineMetaMap.put(line.getLineCode(), line);
      }
    }

    for (StatutoryReportDTO report : singlePeriodReports) {
      Map<String, BigDecimal> reportLineValues = new HashMap<>();
      for (StatutoryReportLineDTO line : report.getLines()) {
        reportLineValues.put(line.getLineCode(), line.getCurrentAmount());
      }

      for (String lineCode : lineValuesMap.keySet()) {
        BigDecimal value = reportLineValues.getOrDefault(lineCode, BigDecimal.ZERO);
        lineValuesMap.get(lineCode).add(value);
      }
    }

    ComparisonSettingsDTO settings = loadComparisonSettings(companyId);

    List<MultiPeriodLineDTO> multiPeriodLines = new ArrayList<>();
    for (Map.Entry<String, List<BigDecimal>> entry : lineValuesMap.entrySet()) {
      String lineCode = entry.getKey();
      List<BigDecimal> values = entry.getValue();
      StatutoryReportLineDTO meta = lineMetaMap.get(lineCode);

      List<VarianceDTO> variances = calculateVariances(lineCode, values, periods, reportType);

      List<Double> sparklineData = calculateSparklineData(values);

      boolean isMaterial = checkMateriality(variances, settings);

      Map<UUID, BigDecimal> periodValuesMap = new LinkedHashMap<>();
      for (int i = 0; i < periods.size(); i++) {
        periodValuesMap.put(periods.get(i).getId(), values.get(i));
      }

      multiPeriodLines.add(
          new MultiPeriodLineDTO(
              lineCode,
              meta.getLineName(),
              meta.getLineNameEnglish(),
              meta.getLevel(),
              meta.isCalculated(),
              periodValuesMap,
              variances,
              sparklineData,
              isMaterial,
              meta.isHasDrillDown()));
    }

    String[] reportNames = REPORT_NAMES.get(reportType);
    return new MultiPeriodReportDTO(
        reportType,
        reportNames[0],
        companyId,
        company.getName(),
        periodColumns,
        multiPeriodLines,
        settings,
        Instant.now(),
        hasDraftPeriod);
  }

  private ComparisonSettingsDTO loadComparisonSettings(Long companyId) {
    return companySettingsRepository
        .findByCompanyId(companyId)
        .map(
            settings -> {
              String json = settings.getComparisonSettings();
              if (json != null && !json.isBlank()) {
                try {
                  return objectMapper.readValue(json, ComparisonSettingsDTO.class);
                } catch (JsonProcessingException e) {
                  logger.warn("Failed to parse comparison settings JSON: {}", e.getMessage());
                }
              }
              return ComparisonSettingsDTO.defaults();
            })
        .orElse(ComparisonSettingsDTO.defaults());
  }

  private List<VarianceDTO> calculateVariances(
      String lineCode,
      List<BigDecimal> values,
      List<AccountingPeriodDTO> periods,
      String reportType) {
    List<VarianceDTO> variances = new ArrayList<>();

    for (int i = 1; i < values.size(); i++) {
      BigDecimal priorValue = values.get(i - 1);
      BigDecimal currentValue = values.get(i);
      UUID fromPeriodId = periods.get(i - 1).getId();
      UUID toPeriodId = periods.get(i).getId();

      BigDecimal absoluteVariance = currentValue.subtract(priorValue);

      Double percentVariance = null;
      if (priorValue.compareTo(BigDecimal.ZERO) != 0) {
        percentVariance =
            absoluteVariance
                .divide(priorValue.abs(), 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .doubleValue();
      } else if (absoluteVariance.compareTo(BigDecimal.ZERO) != 0) {
        percentVariance = Double.POSITIVE_INFINITY;
      }

      String direction = determineVarianceDirection(lineCode, absoluteVariance, reportType);

      variances.add(
          new VarianceDTO(fromPeriodId, toPeriodId, absoluteVariance, percentVariance, direction));
    }

    return variances;
  }

  private String determineVarianceDirection(
      String lineCode, BigDecimal absoluteVariance, String reportType) {
    if (absoluteVariance.compareTo(BigDecimal.ZERO) == 0) {
      return "NEUTRAL";
    }

    boolean isIncrease = absoluteVariance.compareTo(BigDecimal.ZERO) > 0;

    if (REPORT_B02.equals(reportType)) {
      boolean isExpenseLine = isExpenseLine(lineCode);
      if (isExpenseLine) {
        return isIncrease ? "UNFAVORABLE" : "FAVORABLE";
      } else {
        return isIncrease ? "FAVORABLE" : "UNFAVORABLE";
      }
    } else if (REPORT_B01.equals(reportType)) {
      if (isLiabilityLine(lineCode)) {
        return "NEUTRAL";
      }
      return isIncrease ? "FAVORABLE" : "UNFAVORABLE";
    }

    return "NEUTRAL";
  }

  private boolean isExpenseLine(String lineCode) {
    if (lineCode == null || lineCode.isEmpty()) {
      return false;
    }
    char firstChar = lineCode.charAt(0);
    if (firstChar == '6' || firstChar == '8') {
      return true;
    }
    return lineCode.equals("22")
        || lineCode.equals("25")
        || lineCode.equals("26")
        || lineCode.equals("32")
        || lineCode.equals("51");
  }

  private boolean isLiabilityLine(String lineCode) {
    if (lineCode == null || lineCode.isEmpty()) {
      return false;
    }
    char firstChar = lineCode.charAt(0);
    return firstChar == '3' || firstChar == '4';
  }

  private List<Double> calculateSparklineData(List<BigDecimal> values) {
    if (values == null || values.isEmpty()) {
      return new ArrayList<>();
    }

    BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    BigDecimal range = max.subtract(min);

    List<Double> sparkline = new ArrayList<>();
    for (BigDecimal value : values) {
      if (range.compareTo(BigDecimal.ZERO) == 0) {
        sparkline.add(0.5);
      } else {
        double normalized =
            value
                .subtract(min)
                .divide(range, 6, RoundingMode.HALF_UP)
                .doubleValue();
        sparkline.add(normalized);
      }
    }

    return sparkline;
  }

  private boolean checkMateriality(List<VarianceDTO> variances, ComparisonSettingsDTO settings) {
    if (variances == null || variances.isEmpty()) {
      return false;
    }

    for (VarianceDTO variance : variances) {
      if (variance.absoluteVariance() != null
          && variance.absoluteVariance().abs().compareTo(settings.varianceThresholdAbsolute())
              >= 0) {
        return true;
      }

      if (variance.percentVariance() != null
          && !variance.percentVariance().isInfinite()
          && Math.abs(variance.percentVariance()) >= settings.varianceThresholdPercent()) {
        return true;
      }
    }

    return false;
  }
}

package com.accounting.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.DrillDownResponseDTO;
import com.accounting.dto.DrillDownVoucherDTO;
import com.accounting.dto.TrialBalanceDTO;
import com.accounting.dto.TrialBalanceResponseDTO;
import com.accounting.dto.TrialBalanceValidationDTO;
import com.accounting.dto.TrialBalanceValidationDTO.ValidationDetails;
import com.accounting.dto.TrialBalanceValidationDTO.ValidationError;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Company;
import com.accounting.enums.AmountType;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.CompanyService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.TrialBalanceService;
import com.accounting.service.report.TrialBalancePdfExportService;
import com.accounting.service.report.TrialBalanceSnapshotService;
import com.accounting.service.report.TrialBalanceSnapshotService.PdfExportResult;

/**
 * Implementation of TrialBalanceService for generating Trial Balance reports (S06-DN).
 */
@Service
@Transactional(readOnly = true)
public class TrialBalanceServiceImpl implements TrialBalanceService {

  private static final Logger logger = LoggerFactory.getLogger(TrialBalanceServiceImpl.class);
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
  private static final String TRIAL_BALANCE_SHEET_NAME = "Trial Balance (S06-DN)";

  private final VoucherLineRepository voucherLineRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final PeriodManagementService periodManagementService;
  private final CompanyService companyService;
  private final TrialBalancePdfExportService trialBalancePdfExportService;
  private final TrialBalanceSnapshotService trialBalanceSnapshotService;

  // Store last export result for header retrieval
  private static final ThreadLocal<PdfExportResult> lastExportResult = new ThreadLocal<>();

  @Autowired
  public TrialBalanceServiceImpl(
      VoucherLineRepository voucherLineRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      PeriodManagementService periodManagementService,
      CompanyService companyService,
      TrialBalancePdfExportService trialBalancePdfExportService,
      TrialBalanceSnapshotService trialBalanceSnapshotService) {
    this.voucherLineRepository = voucherLineRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.periodManagementService = periodManagementService;
    this.companyService = companyService;
    this.trialBalancePdfExportService = trialBalancePdfExportService;
    this.trialBalanceSnapshotService = trialBalanceSnapshotService;
  }

  /**
   * Get the last PDF export result for header retrieval.
   * Thread-safe via ThreadLocal.
   */
  public static PdfExportResult getLastExportResult() {
    return lastExportResult.get();
  }

  /**
   * Clear the last export result after headers are set.
   */
  public static void clearLastExportResult() {
    lastExportResult.remove();
  }

  @Override
  public TrialBalanceResponseDTO getTrialBalanceData(UUID periodId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Get period information
    AccountingPeriodDTO period = periodManagementService
        .getPeriodById(periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Get company information
    Company company = companyService.getCurrentCompanySettings();
    String companyName = company != null ? company.getName() : "Unknown Company";

    // Calculate opening balances (all periods before selected period start date)
    List<Object[]> openingBalances = voucherLineRepository.calculateOpeningBalances(
        companyId, period.getStartDate());

    // Calculate period activity (aggregate by account for selected period)
    List<Object[]> periodActivity = voucherLineRepository.aggregateByAccountAndPeriod(
        companyId, periodId);

    // Build map of account balances
    Map<Long, TrialBalanceDTO> accountBalances = new HashMap<>();

    // Process opening balances
    for (Object[] row : openingBalances) {
      Long accountId = (Long) row[0];
      BigDecimal openingDebit = (BigDecimal) row[1];
      BigDecimal openingCredit = (BigDecimal) row[2];

      TrialBalanceDTO dto = new TrialBalanceDTO();
      dto.setAccountId(accountId);
      dto.setOpeningDebit(openingDebit != null ? openingDebit : BigDecimal.ZERO);
      dto.setOpeningCredit(openingCredit != null ? openingCredit : BigDecimal.ZERO);
      accountBalances.put(accountId, dto);
    }

    // Process period activity
    for (Object[] row : periodActivity) {
      Long accountId = (Long) row[0];
      BigDecimal periodDebit = (BigDecimal) row[1];
      BigDecimal periodCredit = (BigDecimal) row[2];

      TrialBalanceDTO dto = accountBalances.computeIfAbsent(accountId, id -> new TrialBalanceDTO());
      dto.setAccountId(accountId);
      dto.setPeriodDebit(periodDebit != null ? periodDebit : BigDecimal.ZERO);
      dto.setPeriodCredit(periodCredit != null ? periodCredit : BigDecimal.ZERO);
    }

    // Get account information and calculate closing balances
    List<TrialBalanceDTO> accountList = new ArrayList<>();
    BigDecimal totalOpeningDebit = BigDecimal.ZERO;
    BigDecimal totalOpeningCredit = BigDecimal.ZERO;
    BigDecimal totalPeriodDebit = BigDecimal.ZERO;
    BigDecimal totalPeriodCredit = BigDecimal.ZERO;
    BigDecimal totalClosingDebit = BigDecimal.ZERO;
    BigDecimal totalClosingCredit = BigDecimal.ZERO;

    // Get all account IDs that have activity
    List<Long> accountIds = new ArrayList<>(accountBalances.keySet());
    if (!accountIds.isEmpty()) {
      Map<Long, ChartOfAccount> accountsMap = chartOfAccountsRepository
          .findAllById(accountIds)
          .stream()
          .filter(acc -> acc.getCompanyId().equals(companyId))
          .collect(Collectors.toMap(ChartOfAccount::getId, acc -> acc));

      for (TrialBalanceDTO dto : accountBalances.values()) {
        ChartOfAccount account = accountsMap.get(dto.getAccountId());
        if (account != null) {
          dto.setAccountCode(account.getCode());
          dto.setAccountName(account.getName());

          // Calculate closing balances
          // Closing = (Opening Dr + Period Dr) - (Opening Cr + Period Cr)
          // If positive, it's a debit balance; if negative, it's a credit balance
          BigDecimal totalDebit = dto.getOpeningDebit().add(dto.getPeriodDebit());
          BigDecimal totalCredit = dto.getOpeningCredit().add(dto.getPeriodCredit());
          BigDecimal netClosing = totalDebit.subtract(totalCredit);
          
          if (netClosing.compareTo(BigDecimal.ZERO) >= 0) {
            dto.setClosingDebit(netClosing);
            dto.setClosingCredit(BigDecimal.ZERO);
          } else {
            dto.setClosingDebit(BigDecimal.ZERO);
            dto.setClosingCredit(netClosing.abs());
          }

          // Accumulate totals
          totalOpeningDebit = totalOpeningDebit.add(dto.getOpeningDebit());
          totalOpeningCredit = totalOpeningCredit.add(dto.getOpeningCredit());
          totalPeriodDebit = totalPeriodDebit.add(dto.getPeriodDebit());
          totalPeriodCredit = totalPeriodCredit.add(dto.getPeriodCredit());
          totalClosingDebit = totalClosingDebit.add(dto.getClosingDebit());
          totalClosingCredit = totalClosingCredit.add(dto.getClosingCredit());

          accountList.add(dto);
        }
      }
    }

    // Sort by account code
    accountList.sort((a, b) -> {
      if (a.getAccountCode() == null && b.getAccountCode() == null) {
        return 0;
      }
      if (a.getAccountCode() == null) {
        return 1;
      }
      if (b.getAccountCode() == null) {
        return -1;
      }
      return a.getAccountCode().compareTo(b.getAccountCode());
    });

    // Basic validation: sum(Dr) = sum(Cr) at closing balance level
    boolean balanced = totalClosingDebit.compareTo(totalClosingCredit) == 0;
    if (!balanced) {
      logger.warn("Trial balance validation failed: Closing Dr ({}) != Closing Cr ({})",
          totalClosingDebit, totalClosingCredit);
      // Note: For MVP, we log a warning but don't throw an exception
      // Full validation will be added post-demo
    }

    // Build response
    TrialBalanceResponseDTO response = new TrialBalanceResponseDTO();
    response.setPeriod(period);
    response.setCompanyName(companyName);
    response.setGeneratedAt(Instant.now());
    response.setAccounts(accountList);
    response.setTotalOpeningDebit(totalOpeningDebit);
    response.setTotalOpeningCredit(totalOpeningCredit);
    response.setTotalPeriodDebit(totalPeriodDebit);
    response.setTotalPeriodCredit(totalPeriodCredit);
    response.setTotalClosingDebit(totalClosingDebit);
    response.setTotalClosingCredit(totalClosingCredit);
    response.setBalanced(balanced);

    return response;
  }

  @Override
  public byte[] exportToExcel(UUID periodId) {
    TrialBalanceResponseDTO data = getTrialBalanceData(periodId);
    return exportReportToExcel(data);
  }

  private byte[] exportReportToExcel(TrialBalanceResponseDTO report) {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet(TRIAL_BALANCE_SHEET_NAME);

      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);

      CellStyle numberStyle = workbook.createCellStyle();
      Font numberFont = workbook.createFont();
      numberStyle.setFont(numberFont);
      numberStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.00"));

      CellStyle totalStyle = workbook.createCellStyle();
      Font totalFont = workbook.createFont();
      totalFont.setBold(true);
      totalStyle.setFont(totalFont);
      totalStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.00"));

      int rowNum = 0;

      // Title
      Row titleRow = sheet.createRow(rowNum++);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("TRIAL BALANCE REPORT (S06-DN)");
      titleCell.setCellStyle(headerStyle);

      rowNum++;
      rowNum = createInfoRow(sheet, rowNum, "Company:", report.getCompanyName());
      rowNum = createInfoRow(sheet, rowNum, "Period:", 
          report.getPeriod() != null ? report.getPeriod().getPeriodName() : "N/A");
      rowNum = createInfoRow(sheet, rowNum, "Date Range:", 
          report.getPeriod() != null 
              ? formatDate(report.getPeriod().getStartDate()) + " - " + formatDate(report.getPeriod().getEndDate())
              : "N/A");
      rowNum = createInfoRow(sheet, rowNum, "Generated:", formatTimestamp(report.getGeneratedAt()));

      rowNum++;
      // Column headers
      String[] headers = {
          "Account Code", "Account Name", 
          "Opening Dr", "Opening Cr", 
          "Period Dr", "Period Cr", 
          "Closing Dr", "Closing Cr"
      };
      Row headerRow = sheet.createRow(rowNum++);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data rows
      for (TrialBalanceDTO account : report.getAccounts()) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(account.getAccountCode() != null ? account.getAccountCode() : "");
        row.createCell(1).setCellValue(account.getAccountName() != null ? account.getAccountName() : "");
        
        Cell openingDrCell = row.createCell(2);
        openingDrCell.setCellValue(account.getOpeningDebit().doubleValue());
        openingDrCell.setCellStyle(numberStyle);
        
        Cell openingCrCell = row.createCell(3);
        openingCrCell.setCellValue(account.getOpeningCredit().doubleValue());
        openingCrCell.setCellStyle(numberStyle);
        
        Cell periodDrCell = row.createCell(4);
        periodDrCell.setCellValue(account.getPeriodDebit().doubleValue());
        periodDrCell.setCellStyle(numberStyle);
        
        Cell periodCrCell = row.createCell(5);
        periodCrCell.setCellValue(account.getPeriodCredit().doubleValue());
        periodCrCell.setCellStyle(numberStyle);
        
        Cell closingDrCell = row.createCell(6);
        closingDrCell.setCellValue(account.getClosingDebit().doubleValue());
        closingDrCell.setCellStyle(numberStyle);
        
        Cell closingCrCell = row.createCell(7);
        closingCrCell.setCellValue(account.getClosingCredit().doubleValue());
        closingCrCell.setCellStyle(numberStyle);
      }

      // Totals row
      rowNum++;
      Row totalRow = sheet.createRow(rowNum++);
      totalRow.createCell(0).setCellValue("TOTAL");
      totalRow.createCell(1).setCellValue("");
      
      Cell totalOpeningDrCell = totalRow.createCell(2);
      totalOpeningDrCell.setCellValue(report.getTotalOpeningDebit().doubleValue());
      totalOpeningDrCell.setCellStyle(totalStyle);
      
      Cell totalOpeningCrCell = totalRow.createCell(3);
      totalOpeningCrCell.setCellValue(report.getTotalOpeningCredit().doubleValue());
      totalOpeningCrCell.setCellStyle(totalStyle);
      
      Cell totalPeriodDrCell = totalRow.createCell(4);
      totalPeriodDrCell.setCellValue(report.getTotalPeriodDebit().doubleValue());
      totalPeriodDrCell.setCellStyle(totalStyle);
      
      Cell totalPeriodCrCell = totalRow.createCell(5);
      totalPeriodCrCell.setCellValue(report.getTotalPeriodCredit().doubleValue());
      totalPeriodCrCell.setCellStyle(totalStyle);
      
      Cell totalClosingDrCell = totalRow.createCell(6);
      totalClosingDrCell.setCellValue(report.getTotalClosingDebit().doubleValue());
      totalClosingDrCell.setCellStyle(totalStyle);
      
      Cell totalClosingCrCell = totalRow.createCell(7);
      totalClosingCrCell.setCellValue(report.getTotalClosingCredit().doubleValue());
      totalClosingCrCell.setCellStyle(totalStyle);

      // Auto-size columns
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to export Trial Balance report to Excel", e);
    }
  }

  private int createInfoRow(Sheet sheet, int rowNum, String label, String value) {
    Row row = sheet.createRow(rowNum);
    row.createCell(0).setCellValue(label);
    row.createCell(1).setCellValue(value != null ? value : "");
    return rowNum + 1;
  }

  private String formatDate(LocalDate date) {
    return date != null ? DATE_FORMATTER.format(date) : "-";
  }

  private String formatTimestamp(Instant instant) {
    Instant value = instant != null ? instant : Instant.now();
    return TIMESTAMP_FORMATTER.format(value.atZone(DEFAULT_ZONE));
  }

  // ==================== Drill-Down Methods ====================

  @Override
  public DrillDownResponseDTO getDrillDownVouchers(UUID periodId, Long accountId, AmountType amountType, Pageable pageable) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Get period information
    AccountingPeriodDTO period = periodManagementService
        .getPeriodById(periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Validate account exists
    ChartOfAccount account = chartOfAccountsRepository.findById(accountId)
        .filter(a -> a.getCompanyId().equals(companyId))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Account not found: " + accountId));

    // Execute appropriate query based on amount type
    Page<Object[]> voucherPage = executeVoucherQuery(companyId, accountId, periodId, period, amountType, pageable);

    // Map results to DTOs
    List<DrillDownVoucherDTO> vouchers = voucherPage.getContent().stream()
        .map(this::mapToVoucherDTO)
        .collect(Collectors.toList());

    // Calculate total amount based on type
    BigDecimal totalAmount = calculateDrillDownTotal(companyId, accountId, periodId, period, amountType);

    // Build response
    DrillDownResponseDTO response = new DrillDownResponseDTO();
    response.setVouchers(vouchers);
    response.setTotalAmount(totalAmount);
    response.setVoucherCount((int) voucherPage.getTotalElements());
    response.setPage(voucherPage.getNumber());
    response.setSize(voucherPage.getSize());
    response.setTotal(voucherPage.getTotalElements());
    response.setHasNext(voucherPage.hasNext());

    logger.debug("Drill-down query returned {} vouchers for account {} with type {}",
        vouchers.size(), account.getCode(), amountType);

    return response;
  }

  private Page<Object[]> executeVoucherQuery(Long companyId, Long accountId, UUID periodId,
      AccountingPeriodDTO period, AmountType amountType, Pageable pageable) {
    switch (amountType) {
      case OPENING_DEBIT:
        return voucherLineRepository.findOpeningDebitVouchers(companyId, accountId, period.getStartDate(), pageable);
      case OPENING_CREDIT:
        return voucherLineRepository.findOpeningCreditVouchers(companyId, accountId, period.getStartDate(), pageable);
      case PERIOD_DEBIT:
        return voucherLineRepository.findPeriodDebitVouchers(companyId, accountId, periodId, pageable);
      case PERIOD_CREDIT:
        return voucherLineRepository.findPeriodCreditVouchers(companyId, accountId, periodId, pageable);
      case CLOSING_DEBIT:
        return voucherLineRepository.findClosingDebitVouchers(companyId, accountId, period.getEndDate(), pageable);
      case CLOSING_CREDIT:
        return voucherLineRepository.findClosingCreditVouchers(companyId, accountId, period.getEndDate(), pageable);
      default:
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid amount type: " + amountType);
    }
  }

  private BigDecimal calculateDrillDownTotal(Long companyId, Long accountId, UUID periodId,
      AccountingPeriodDTO period, AmountType amountType) {
    switch (amountType) {
      case OPENING_DEBIT:
        return voucherLineRepository.sumOpeningDebit(companyId, accountId, period.getStartDate());
      case OPENING_CREDIT:
        return voucherLineRepository.sumOpeningCredit(companyId, accountId, period.getStartDate());
      case PERIOD_DEBIT:
        return voucherLineRepository.sumPeriodDebit(companyId, accountId, periodId);
      case PERIOD_CREDIT:
        return voucherLineRepository.sumPeriodCredit(companyId, accountId, periodId);
      case CLOSING_DEBIT:
        return voucherLineRepository.sumClosingDebit(companyId, accountId, period.getEndDate());
      case CLOSING_CREDIT:
        return voucherLineRepository.sumClosingCredit(companyId, accountId, period.getEndDate());
      default:
        return BigDecimal.ZERO;
    }
  }

  private DrillDownVoucherDTO mapToVoucherDTO(Object[] row) {
    // Query returns 7 columns: id(0), voucherNumber(1), voucherDate(2), description(3),
    // SUM(debit)(4), SUM(credit)(5), status(6)
    DrillDownVoucherDTO dto = new DrillDownVoucherDTO();
    dto.setId((UUID) row[0]);
    dto.setVoucherNumber((String) row[1]);
    dto.setVoucherDate((LocalDate) row[2]);
    dto.setDescription((String) row[3]);
    dto.setDebit((BigDecimal) row[4]);
    dto.setCredit((BigDecimal) row[5]);
    dto.setStatus((String) row[6]);
    // voucherType is not available from current Voucher entity - kept for API compatibility
    dto.setVoucherType(null);
    return dto;
  }

  // ==================== Validation Methods ====================

  @Override
  public TrialBalanceValidationDTO validateForExport(UUID periodId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    TrialBalanceValidationDTO validation = new TrialBalanceValidationDTO();

    // Get trial balance data to check balance
    TrialBalanceResponseDTO data = getTrialBalanceData(periodId);

    // Check if balanced
    if (!data.isBalanced()) {
      BigDecimal difference = data.getTotalClosingDebit().subtract(data.getTotalClosingCredit());
      ValidationDetails details = new ValidationDetails(
          data.getTotalClosingDebit(),
          data.getTotalClosingCredit(),
          difference
      );
      ValidationError error = new ValidationError(
          "GL_IMBALANCE",
          "Cannot export: Trial Balance out of balance",
          details,
          "/help/trial-balance-imbalance"
      );
      validation.addError(error);

      // Log validation failure for audit
      logger.warn("Trial balance validation failed for company {} period {}: Debit={}, Credit={}, Diff={}",
          companyId, periodId, data.getTotalClosingDebit(), data.getTotalClosingCredit(), difference);
    }

    return validation;
  }

  // ==================== PDF Export Methods ====================

  @Override
  public byte[] exportToPdf(UUID periodId, UUID snapshotId) {
    // Validation preflight
    TrialBalanceValidationDTO validation = validateForExport(periodId);
    if (!validation.isValid()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          validation.getErrors().get(0).getMessage());
    }

    // Get trial balance data
    TrialBalanceResponseDTO report = getTrialBalanceData(periodId);

    // Check if period is open (for DRAFT watermark)
    boolean isDraft = periodManagementService.isPeriodOpen(periodId);

    try {
      logger.info("Exporting Trial Balance PDF for period {} (isDraft={})", periodId, isDraft);

      // If snapshotId provided, verify hash matches
      if (snapshotId != null) {
        boolean hashMatches = trialBalanceSnapshotService.verifySnapshotHash(snapshotId, report);
        if (!hashMatches) {
          logger.warn("Data has changed since snapshot {} was created", snapshotId);
        }
      }

      // Export with snapshot (AC7.1-10: Snapshot reproducibility)
      PdfExportResult result = trialBalanceSnapshotService.exportToPdfWithSnapshot(report, periodId, isDraft);

      // Store result for header retrieval by controller
      lastExportResult.set(result);

      return result.pdfBytes();
    } catch (Exception e) {
      logger.error("Failed to export Trial Balance PDF for period {}: {}", periodId, e.getMessage(), e);
      throw new IllegalStateException("Failed to export Trial Balance report to PDF", e);
    }
  }
}

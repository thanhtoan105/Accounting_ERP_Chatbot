package com.accounting.service.impl.ap;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.InputVATReportDTO;
import com.accounting.dto.VATCorrectionCreateRequest;
import com.accounting.dto.VATCorrectionDTO;
import com.accounting.dto.VATValidationResultDTO;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillLine;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.VATCorrection;
import com.accounting.entity.VATReportHistory;
import com.accounting.entity.VatRate;
import com.accounting.repository.PurchaseBillLineRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.VATCorrectionRepository;
import com.accounting.repository.VATReportHistoryRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.VATService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Implementation of VATService for VAT validation, reporting, and corrections.
 */
@Service
@Transactional(readOnly = true)
public class VATServiceImpl implements VATService {

  private static final Logger logger = LoggerFactory.getLogger(VATServiceImpl.class);
  private static final BigDecimal VAT_SUM_TOLERANCE = new BigDecimal("1000.00"); // 1,000₫
  private static final BigDecimal MAX_VAT_RATIO = new BigDecimal("100.00"); // 100%
  private static final BigDecimal MIN_VAT_RATIO = BigDecimal.ZERO; // 0%
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
  private static final String INPUT_VAT_SHEET_NAME = "Input VAT Report";
  private static final String ALL_SUPPLIERS_LABEL = "All Suppliers";
  private static final String ALL_VAT_CLASSES_LABEL = "All VAT Classes";

  // Default VAT rate for company
  // TODO: Read from CompanySettings.defaultVatRate field when available (requires adding field to CompanySettings entity)
  // For now, default to 10% (most common in Vietnam)
  // Note: This is a known limitation - company-specific default VAT rates should be configurable per company
  private static final VatRate DEFAULT_VAT_RATE = VatRate.TEN;

  private final PurchaseBillLineRepository purchaseBillLineRepository;
  private final PurchaseBillRepository purchaseBillRepository;
  private final SupplierRepository supplierRepository;
  private final VATCorrectionRepository vatCorrectionRepository;
  private final VATReportHistoryRepository vatReportHistoryRepository;
  private final PeriodManagementService periodManagementService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  @Autowired
  public VATServiceImpl(
      PurchaseBillLineRepository purchaseBillLineRepository,
      PurchaseBillRepository purchaseBillRepository,
      SupplierRepository supplierRepository,
      VATCorrectionRepository vatCorrectionRepository,
      VATReportHistoryRepository vatReportHistoryRepository,
      PeriodManagementService periodManagementService,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.purchaseBillLineRepository = purchaseBillLineRepository;
    this.purchaseBillRepository = purchaseBillRepository;
    this.supplierRepository = supplierRepository;
    this.vatCorrectionRepository = vatCorrectionRepository;
    this.vatReportHistoryRepository = vatReportHistoryRepository;
    this.periodManagementService = periodManagementService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public VATValidationResultDTO validateVATRate(VatRate rate, Long companyId) {
    VATValidationResultDTO result = new VATValidationResultDTO(true);

    // Validate rate is one of: 0, 5, 10, EXEMPT
    if (rate == null) {
      result.addError("VAT rate is required");
      return result;
    }

    boolean isValidRate = rate == VatRate.ZERO
        || rate == VatRate.FIVE
        || rate == VatRate.TEN
        || rate == VatRate.EXEMPT;

    if (!isValidRate) {
      result.addError("Invalid VAT rate. Must be one of: 0%, 5%, 10%, or EXEMPT");
      return result;
    }

    result.setValidatedRate(rate);

    // Check against company default VAT rate (if different, add warning)
    // TODO: Read from CompanySettings.defaultVatRate field when available (requires adding field to CompanySettings entity)
    // This is a known limitation - company-specific default VAT rates should be configurable per company
    VatRate companyDefault = DEFAULT_VAT_RATE;
    if (rate != companyDefault) {
      result.addWarning(
          String.format(
              "VAT rate %s differs from company default %s. This override will be logged.",
              rate.getDisplayName(), companyDefault.getDisplayName()));

      // Log override to audit
      logVATRateOverride(companyId, rate, companyDefault);
    }

    return result;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public VATValidationResultDTO validateVATSum(PurchaseBill bill) {
    VATValidationResultDTO result = new VATValidationResultDTO(true);

    if (bill == null) {
      result.addError("Purchase bill is required");
      return result;
    }

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      result.addError("Missing company context");
      return result;
    }

    // Get all line items for the bill
    List<PurchaseBillLine> lines = purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
        companyId, bill.getId());

    // Calculate sum of line-level VAT amounts
    BigDecimal lineVATSum = lines.stream()
        .map(PurchaseBillLine::getVatAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Get document-level VAT total
    BigDecimal documentVAT = bill.getVatAmount() != null ? bill.getVatAmount() : BigDecimal.ZERO;

    result.setCalculatedVATAmount(lineVATSum);
    result.setDocumentVATAmount(documentVAT);

    // Calculate difference
    BigDecimal difference = lineVATSum.subtract(documentVAT).abs();
    result.setDifference(difference);

    // Validate: mismatch >1,000₫ blocks post
    if (difference.compareTo(VAT_SUM_TOLERANCE) > 0) {
      result.addError(
          String.format(
              "VAT sum mismatch: Line-level VAT sum (%s) differs from document VAT (%s) by %s, exceeding tolerance of %s. Posting is blocked.",
              lineVATSum, documentVAT, difference, VAT_SUM_TOLERANCE));

      // Log validation failure to audit
      logVATSumValidationFailure(companyId, bill.getId(), lineVATSum, documentVAT, difference);
    } else if (difference.compareTo(BigDecimal.ZERO) > 0) {
      // Within tolerance but not exact - add warning
      result.addWarning(
          String.format(
              "VAT sum difference of %s is within tolerance but not exact. Consider reviewing.",
              difference));
    }

    return result;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public VATValidationResultDTO validateVATRatio(BigDecimal amount, BigDecimal vatAmount) {
    VATValidationResultDTO result = new VATValidationResultDTO(true);

    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      result.addError("Amount must be greater than zero");
      return result;
    }

    if (vatAmount == null) {
      vatAmount = BigDecimal.ZERO;
    }

    // Calculate VAT ratio: (vatAmount / amount) * 100
    BigDecimal ratio = vatAmount.divide(amount, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));

    result.setVatRatio(ratio);

    // Block if ratio < 0 or ratio > 100
    if (ratio.compareTo(MIN_VAT_RATIO) < 0) {
      result.addError(
          String.format(
              "VAT ratio is negative (%.2f%%). Negative VAT is not allowed.", ratio));
      logVATRatioBlock(CompanyContext.getCompanyId(), amount, vatAmount, ratio, "Negative ratio");
    } else if (ratio.compareTo(MAX_VAT_RATIO) > 0) {
      result.addError(
          String.format(
              "VAT ratio exceeds 100%% (%.2f%%). VAT cannot exceed the base amount.", ratio));
      logVATRatioBlock(CompanyContext.getCompanyId(), amount, vatAmount, ratio, "Over 100% ratio");
    }

    return result;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public BigDecimal mapVATToGL(PurchaseBill bill) {
    if (bill == null) {
      throw new IllegalArgumentException("Purchase bill is required");
    }

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    // Get all line items and calculate total VAT
    List<PurchaseBillLine> lines = purchaseBillLineRepository.findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(
        companyId, bill.getId());

    // Sum all VAT amounts from line items
    BigDecimal totalVAT = lines.stream()
        .map(PurchaseBillLine::getVatAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    logger.debug(
        "Mapped VAT to GL for bill {}: total VAT amount = {}", bill.getId(), totalVAT);

    return totalVAT;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public InputVATReportDTO generateInputVATReport(
      UUID periodId, Long supplierId, String vatClass, Map<String, Object> filters) {
    Long companyId = requireCompanyId();
    Map<String, Object> safeFilters = copyFilters(filters);
    VATReportHistory.ExportFormat exportFormat = resolveExportFormat(safeFilters);

    ReportComputation computation = buildReportData(companyId, periodId, supplierId, vatClass, safeFilters);

    InputVATReportDTO report = computation.report();
    report.setFormat(exportFormat);

    VATReportHistory history = buildReportHistory(
        null, // Don't set ID for new entities - let Hibernate generate it
        companyId,
        periodId,
        supplierId,
        vatClass,
        computation.dateRange(),
        exportFormat,
        report);
    persistReportHistory(history);
    
    // Set the generated ID on the report DTO
    report.setReportId(history.getId());

    Map<String, Object> auditFilters = buildAuditFilters(periodId, supplierId, vatClass, computation.dateRange(),
        safeFilters);
    auditService.logVatReportGenerated(
        companyId,
        getCurrentUserId(),
        history.getId(),
        VATReportHistory.ReportType.INPUT_VAT.name(),
        auditFilters);

    return report;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public byte[] exportInputVATReport(UUID reportId, String format) {
    Long companyId = requireCompanyId();
    VATReportHistory history = vatReportHistoryRepository
        .findByCompanyIdAndId(companyId, reportId)
        .orElseThrow(() -> new IllegalArgumentException("VAT report not found: " + reportId));

    VATReportHistory.ExportFormat exportFormat = parseExportFormat(format, history.getFormat());

    Map<String, Object> filters = new HashMap<>();
    filters.put("startDate", history.getStartDate());
    filters.put("endDate", history.getEndDate());

    ReportComputation computation = buildReportData(
        companyId, history.getPeriodId(), history.getSupplierId(), history.getVatClass(), filters);

    InputVATReportDTO report = computation.report();
    report.setReportId(history.getId());
    report.setFormat(exportFormat);
    report.setGenerationDate(history.getGenerationDate());

    byte[] exported = exportFormat == VATReportHistory.ExportFormat.PDF
        ? exportReportToPdf(report, computation.dateRange(), history.getHash())
        : exportReportToExcel(report, computation.dateRange(), history.getHash());

    int currentDownloads = history.getDownloadCount() != null ? history.getDownloadCount() : 0;
    history.setDownloadCount(currentDownloads + 1);
    persistReportHistory(history);

    auditService.logReportExport(
        companyId, getCurrentUserId(), exportFormat.name(), getCurrentRequest());

    return exported;
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public VATCorrectionDTO createVATCorrection(VATCorrectionCreateRequest request) {
    ensureVatCorrectionRole();
    Long companyId = requireCompanyId();
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction request is required");
    }

    UUID billId = Objects.requireNonNull(request.getPurchaseBillId(), "purchaseBillId is required");

    PurchaseBill bill = purchaseBillRepository
        .findByCompanyIdAndId(companyId, billId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Purchase bill not found: " + billId));

    PurchaseBillLine line = null;
    if (request.getPurchaseBillLineId() != null) {
      UUID lineId = request.getPurchaseBillLineId();
      line = purchaseBillLineRepository
          .findByCompanyIdAndId(companyId, lineId)
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Purchase bill line not found: " + lineId));
      if (!bill.getId().equals(line.getPurchaseBillId())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Line does not belong to the specified bill");
      }
    }

    BigDecimal newAmount = safe(request.getNewVatAmount()).setScale(2, RoundingMode.HALF_UP);
    if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "New VAT amount must be zero or positive");
    }

    BigDecimal oldAmount = line != null ? safe(line.getVatAmount()) : safe(bill.getVatAmount());
    if (newAmount.compareTo(oldAmount) == 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "New VAT amount must differ from existing amount");
    }

    VATCorrection correction = new VATCorrection();
    correction.setCompanyId(companyId);
    correction.setPurchaseBillId(bill.getId());
    correction.setPurchaseBillLineId(line != null ? line.getId() : null);
    correction.setOldVatAmount(oldAmount);
    correction.setNewVatAmount(newAmount);
    correction.setReason(request.getReason());
    correction.setCorrectedById(getCurrentUserId());
    correction.setCorrectedAt(Instant.now());
    correction.setStatus(VATCorrection.Status.PENDING);

    VATCorrection saved = vatCorrectionRepository.save(correction);

    auditService.logVatCorrectionCreated(
        companyId,
        saved.getCorrectedById(),
        saved.getId(),
        saved.getPurchaseBillId(),
        saved.getOldVatAmount(),
        saved.getNewVatAmount(),
        saved.getReason());

    return mapCorrectionToDTO(saved);
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  @Transactional
  public VATCorrectionDTO approveVATCorrection(UUID correctionId, Long approverId) {
    ensureVatCorrectionRole();
    Long companyId = requireCompanyId();
    UUID targetCorrectionId = Objects.requireNonNull(correctionId, "correctionId is required");

    VATCorrection correction = vatCorrectionRepository
        .findByCompanyIdAndId(companyId, targetCorrectionId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "VAT correction not found: " + targetCorrectionId));

    if (correction.getStatus() != VATCorrection.Status.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only pending corrections can be approved");
    }

    PurchaseBill bill = purchaseBillRepository
        .findByCompanyIdAndId(companyId, correction.getPurchaseBillId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Purchase bill not found: " + correction.getPurchaseBillId()));

    PurchaseBillLine line = null;
    if (correction.getPurchaseBillLineId() != null) {
      line = purchaseBillLineRepository
          .findByCompanyIdAndId(companyId, correction.getPurchaseBillLineId())
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND,
                  "Purchase bill line not found: " + correction.getPurchaseBillLineId()));
    }

    BigDecimal newAmount = correction.getNewVatAmount();
    if (line != null) {
      line.setVatAmount(newAmount);
      purchaseBillLineRepository.save(line);
      bill.setVatAmount(recalculateBillVatAmount(companyId, bill.getId()));
    } else {
      bill.setVatAmount(newAmount);
    }
    purchaseBillRepository.save(bill);

    Long approver = approverId != null ? approverId : SecurityUtils.getCurrentUserId();
    correction.setStatus(VATCorrection.Status.APPROVED);
    correction.setApprovedById(approver);
    correction.setApprovedAt(Instant.now());
    VATCorrection saved = vatCorrectionRepository.save(correction);

    auditService.logVatCorrectionApproved(
        companyId,
        approver,
        saved.getId(),
        saved.getPurchaseBillId(),
        saved.getOldVatAmount(),
        saved.getNewVatAmount());

    return mapCorrectionToDTO(saved);
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public List<VATCorrectionDTO> getVATCorrections(UUID billId, Map<String, Object> filters) {
    Long companyId = requireCompanyId();
    UUID targetBillId = Objects.requireNonNull(billId, "billId is required");

    VATCorrection.Status statusFilter = resolveCorrectionStatus(filters);
    LocalDate startDate = parseLocalDate(filters != null ? filters.get("startDate") : null);
    LocalDate endDate = parseLocalDate(filters != null ? filters.get("endDate") : null);
    Long correctedById = parseLong(filters != null ? filters.get("correctedById") : null);
    Instant startInstant = toStartInstant(startDate);
    Instant endInstant = toEndInstant(endDate);

    List<VATCorrection> corrections;
    if (statusFilter != null) {
      corrections = vatCorrectionRepository.findByCompanyIdAndPurchaseBillIdAndStatusOrderByCorrectedAtDesc(
          companyId, targetBillId, statusFilter);
    } else {
      corrections = vatCorrectionRepository.findByCompanyIdAndPurchaseBillIdOrderByCorrectedAtDesc(
          companyId, targetBillId);
    }

    return corrections.stream()
        .filter(c -> filterByDateRange(c, startInstant, endInstant))
        .filter(c -> filterByUser(c, correctedById))
        .map(this::mapCorrectionToDTO)
        .collect(Collectors.toList());
  }

  /**
   * Log VAT rate override to audit trail.
   */
  private void logVATRateOverride(Long companyId, VatRate actualRate, VatRate defaultRate) {
    try {
      HttpServletRequest request = getCurrentRequest();
      Long userId = null;
      try {
        userId = getCurrentUserId();
      } catch (Exception e) {
        // In unit tests or non-authenticated contexts, userId may be unavailable
        logger.debug("Unable to get current user ID for VAT rate override logging", e);
      }
      auditService.logVatRateOverride(
          companyId,
          userId,
          actualRate != null ? actualRate.getDisplayName() : null,
          defaultRate != null ? defaultRate.getDisplayName() : null,
          request);
    } catch (Exception e) {
      logger.error("Failed to log VAT rate override", e);
    }
  }

  /**
   * Log VAT sum validation failure to audit trail.
   */
  private void logVATSumValidationFailure(
      Long companyId,
      UUID billId,
      BigDecimal lineVATSum,
      BigDecimal documentVAT,
      BigDecimal difference) {
    try {
      HttpServletRequest request = getCurrentRequest();
      Long userId = null;
      try {
        userId = getCurrentUserId();
      } catch (Exception e) {
        // In unit tests or non-authenticated contexts, userId may be unavailable
        logger.debug("Unable to get current user ID for VAT sum validation failure logging", e);
      }
      auditService.logVatSumValidationFailure(
          companyId,
          userId,
          billId,
          lineVATSum,
          documentVAT,
          difference,
          request);
    } catch (Exception e) {
      logger.error("Failed to log VAT sum validation failure", e);
    }
  }

  /**
   * Log VAT ratio block to audit trail.
   */
  private void logVATRatioBlock(
      Long companyId, BigDecimal amount, BigDecimal vatAmount, BigDecimal ratio, String reason) {
    try {
      HttpServletRequest request = getCurrentRequest();
      Long userId = null;
      try {
        userId = getCurrentUserId();
      } catch (Exception e) {
        // In unit tests or non-authenticated contexts, userId may be unavailable
        logger.debug("Unable to get current user ID for VAT ratio block logging", e);
      }
      auditService.logVatRatioBlock(
          companyId,
          userId,
          amount,
          vatAmount,
          ratio,
          reason,
          request);
    } catch (Exception e) {
      logger.error("Failed to log VAT ratio block", e);
    }
  }

  /**
   * Get current HTTP request from context.
   */
  private HttpServletRequest getCurrentRequest() {
    try {
      ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      return attributes != null ? attributes.getRequest() : null;
    } catch (Exception e) {
      logger.debug("Could not get current request from context", e);
      return null;
    }
  }

  private Long requireCompanyId() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }
    return companyId;
  }

  private DateRange resolveDateRange(UUID periodId, Map<String, Object> filters) {
    LocalDate startDate = parseLocalDate(filters.get("startDate"));
    LocalDate endDate = parseLocalDate(filters.get("endDate"));

    if ((startDate == null || endDate == null) && periodId != null) {
      Optional<AccountingPeriodDTO> periodOpt = periodManagementService.getPeriodById(periodId);
      if (periodOpt.isEmpty()) {
        throw new IllegalArgumentException("Accounting period not found: " + periodId);
      }
      AccountingPeriodDTO period = periodOpt.get();
      startDate = period.getStartDate();
      endDate = period.getEndDate();
    }

    if (startDate == null || endDate == null) {
      LocalDate now = LocalDate.now();
      startDate = now.withDayOfMonth(1);
      endDate = now.withDayOfMonth(now.lengthOfMonth());
    }

    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("End date cannot be before start date");
    }

    return new DateRange(startDate, endDate);
  }

  private VATReportHistory.ExportFormat resolveExportFormat(Map<String, Object> filters) {
    Object formatObj = filters.get("format");
    if (formatObj instanceof VATReportHistory.ExportFormat exportFormat) {
      return exportFormat;
    }
    if (formatObj instanceof String formatStr && StringUtils.hasText(formatStr)) {
      try {
        return VATReportHistory.ExportFormat.valueOf(formatStr.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
        logger.warn("Unsupported VAT report format '{}', defaulting to EXCEL", formatStr);
      }
    }
    return VATReportHistory.ExportFormat.EXCEL;
  }

  private VATReportHistory.ExportFormat parseExportFormat(
      String requestedFormat, VATReportHistory.ExportFormat fallback) {
    if (StringUtils.hasText(requestedFormat)) {
      try {
        return VATReportHistory.ExportFormat.valueOf(requestedFormat.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        logger.warn(
            "Unsupported VAT report export format '{}', falling back to {}",
            requestedFormat,
            fallback != null ? fallback : VATReportHistory.ExportFormat.EXCEL);
      }
    }
    return fallback != null ? fallback : VATReportHistory.ExportFormat.EXCEL;
  }

  private ReportComputation buildReportData(
      Long companyId, UUID periodId, Long supplierId, String vatClass, Map<String, Object> filters) {
    Map<String, Object> safeFilters = copyFilters(filters);
    DateRange dateRange = resolveDateRange(periodId, safeFilters);
    if (dateRange.start() == null || dateRange.end() == null) {
      throw new IllegalArgumentException(
          "Either accounting period or explicit start/end dates must be provided");
    }

    List<PurchaseBill> bills = purchaseBillRepository.findAll(
        buildReportSpecification(companyId, supplierId, dateRange.start(), dateRange.end()));

    Map<UUID, List<PurchaseBillLine>> linesByBill = loadLinesByBill(companyId, bills);
    InputVATReportDTO report = initializeReportDTO(
        companyId, periodId, supplierId, vatClass, dateRange, VATReportHistory.ExportFormat.EXCEL);

    boolean filterByVatClass = StringUtils.hasText(vatClass);
    String normalizedVatClass = filterByVatClass ? vatClass.trim().toUpperCase() : null;

    for (PurchaseBill bill : bills) {
      List<PurchaseBillLine> billLines = linesByBill.getOrDefault(bill.getId(), Collections.emptyList());
      Supplier supplier = resolveSupplier(bill);

      for (PurchaseBillLine line : billLines) {
        if (filterByVatClass && !matchesVatClass(line, normalizedVatClass)) {
          continue;
        }
        InputVATReportDTO.ReportLineItemDTO lineItem = toReportLineItem(line, bill, supplier);
        report.getItems().add(lineItem);

        VatRate vatRate = line.getVatRate() != null ? line.getVatRate() : VatRate.ZERO;
        BigDecimal vatAmount = safe(line.getVatAmount());
        BigDecimal grossAmount = safe(line.getAmount()).add(vatAmount);

        report.getTotalVATByRate().merge(vatRate, vatAmount, BigDecimal::add);
        report.setGrandTotalVAT(report.getGrandTotalVAT().add(vatAmount));
        report.setGrandTotalAmount(report.getGrandTotalAmount().add(grossAmount));
      }
    }

    return new ReportComputation(report, dateRange);
  }

  private Specification<PurchaseBill> buildReportSpecification(
      Long companyId, Long supplierId, LocalDate startDate, LocalDate endDate) {
    return (root, query, cb) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("companyId"), companyId));
      predicates.add(cb.equal(root.get("status"), PurchaseBillStatus.POSTED));
      predicates.add(cb.between(root.get("billDate"), startDate, endDate));
      if (supplierId != null) {
        predicates.add(cb.equal(root.get("supplierId"), supplierId));
      }
      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
  }

  private Map<UUID, List<PurchaseBillLine>> loadLinesByBill(
      Long companyId, List<PurchaseBill> bills) {
    if (bills.isEmpty()) {
      return Collections.emptyMap();
    }
    List<UUID> billIds = bills.stream().map(PurchaseBill::getId).toList();
    List<PurchaseBillLine> lines = purchaseBillLineRepository
        .findByCompanyIdAndPurchaseBillIdInOrderByPurchaseBillIdAscLineNumberAsc(
            companyId, billIds);
    Map<UUID, List<PurchaseBillLine>> grouped = new HashMap<>();
    for (PurchaseBillLine line : lines) {
      grouped.computeIfAbsent(line.getPurchaseBillId(), id -> new ArrayList<>()).add(line);
    }
    return grouped;
  }

  private byte[] exportReportToExcel(
      InputVATReportDTO report, DateRange dateRange, String hashValue) {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet(INPUT_VAT_SHEET_NAME);

      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);

      int rowNum = 0;
      Row titleRow = sheet.createRow(rowNum++);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("INPUT VAT REPORT");
      titleCell.setCellStyle(headerStyle);

      rowNum++;
      rowNum = createInfoRow(sheet, rowNum, "Company ID:", String.valueOf(report.getCompanyId()));
      rowNum = createInfoRow(sheet, rowNum, "Period:", formatDateRange(dateRange.start(), dateRange.end()));
      rowNum = createInfoRow(sheet, rowNum, "Supplier:", formatReportSupplierLabel(report));
      rowNum = createInfoRow(sheet, rowNum, "VAT Class:", formatVatClass(report.getVatClass()));

      rowNum++;
      String[] headers = {
          "Supplier", "Bill Number", "Bill Date", "VAT Rate", "Base Amount", "VAT Amount", "Total Amount"
      };
      Row headerRow = sheet.createRow(rowNum++);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      for (InputVATReportDTO.ReportLineItemDTO item : report.getItems()) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(formatSupplier(item));
        row.createCell(1).setCellValue(
            item.getBillNumber() != null ? item.getBillNumber() : "-");
        row.createCell(2).setCellValue(formatDate(item.getBillDate()));
        row.createCell(3).setCellValue(formatRateLabel(item.getVatRate()));
        row.createCell(4).setCellValue(formatCurrency(item.getBaseAmount()));
        row.createCell(5).setCellValue(formatCurrency(item.getVatAmount()));
        row.createCell(6).setCellValue(formatCurrency(item.getTotalAmount()));
      }

      rowNum++;
      Row summaryHeader = sheet.createRow(rowNum++);
      Cell summaryCell = summaryHeader.createCell(0);
      summaryCell.setCellValue("Summary Totals by VAT Rate");
      summaryCell.setCellStyle(headerStyle);

      for (VatRate rate : VatRate.values()) {
        BigDecimal vatTotal = report.getTotalVATByRate().getOrDefault(rate, BigDecimal.ZERO);
        createInfoRow(
            sheet, rowNum++, formatRateLabel(rate) + " VAT:", formatCurrency(vatTotal));
      }
      createInfoRow(sheet, rowNum++, "Grand Total VAT:", formatCurrency(report.getGrandTotalVAT()));
      createInfoRow(
          sheet, rowNum++, "Grand Total Amount:", formatCurrency(report.getGrandTotalAmount()));

      rowNum++;
      String footer = "Generated: "
          + formatTimestamp(report.getGenerationDate())
          + " | Hash: "
          + (StringUtils.hasText(hashValue) ? hashValue : calculateReportHash(report));
      createInfoRow(sheet, rowNum, footer, "");

      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to export Input VAT report to Excel", e);
    }
  }

  private byte[] exportReportToPdf(
      InputVATReportDTO report, DateRange dateRange, String hashValue) {
    StringBuilder pdf = new StringBuilder();
    pdf.append("INPUT VAT REPORT\n\n");
    pdf.append("Company ID: ").append(report.getCompanyId()).append("\n");
    pdf.append("Period: ")
        .append(formatDateRange(dateRange.start(), dateRange.end()))
        .append("\n");
    pdf.append("Supplier: ").append(formatReportSupplierLabel(report)).append("\n");
    pdf.append("VAT Class: ").append(formatVatClass(report.getVatClass())).append("\n\n");

    pdf.append(
        String.format(
            "%-30s %-15s %-12s %-8s %15s %15s %15s%n",
            "Supplier",
            "Bill #",
            "Bill Date",
            "Rate",
            "Base",
            "VAT",
            "Total"));
    pdf.append("-".repeat(120)).append("\n");

    for (InputVATReportDTO.ReportLineItemDTO item : report.getItems()) {
      pdf.append(
          String.format(
              "%-30s %-15s %-12s %-8s %15s %15s %15s%n",
              truncate(formatSupplier(item), 30),
              truncate(item.getBillNumber(), 15),
              formatDate(item.getBillDate()),
              truncate(formatRateLabel(item.getVatRate()), 8),
              formatCurrency(item.getBaseAmount()),
              formatCurrency(item.getVatAmount()),
              formatCurrency(item.getTotalAmount())));
    }

    pdf.append("\nSummary Totals:\n");
    for (VatRate rate : VatRate.values()) {
      BigDecimal vatTotal = report.getTotalVATByRate().getOrDefault(rate, BigDecimal.ZERO);
      pdf.append(" - ")
          .append(formatRateLabel(rate))
          .append(": ")
          .append(formatCurrency(vatTotal))
          .append("\n");
    }
    pdf.append("Grand Total VAT: ")
        .append(formatCurrency(report.getGrandTotalVAT()))
        .append("\n");
    pdf.append("Grand Total Amount: ")
        .append(formatCurrency(report.getGrandTotalAmount()))
        .append("\n\n");

    pdf.append("Generated: ")
        .append(formatTimestamp(report.getGenerationDate()))
        .append(" | Hash: ")
        .append(StringUtils.hasText(hashValue) ? hashValue : calculateReportHash(report))
        .append("\n");

    return pdf.toString().getBytes(StandardCharsets.UTF_8);
  }

  private int createInfoRow(Sheet sheet, int rowNum, String label, String value) {
    Row row = sheet.createRow(rowNum);
    row.createCell(0).setCellValue(label);
    row.createCell(1).setCellValue(value != null ? value : "");
    return rowNum + 1;
  }

  private String formatReportSupplierLabel(InputVATReportDTO report) {
    if (StringUtils.hasText(report.getSupplierName())) {
      if (StringUtils.hasText(report.getSupplierCode())) {
        return report.getSupplierName() + " (" + report.getSupplierCode() + ")";
      }
      return report.getSupplierName();
    }
    if (report.getSupplierId() != null) {
      return "Supplier #" + report.getSupplierId();
    }
    return ALL_SUPPLIERS_LABEL;
  }

  private String formatVatClass(String vatClass) {
    return StringUtils.hasText(vatClass) ? vatClass : ALL_VAT_CLASSES_LABEL;
  }

  private String formatSupplier(InputVATReportDTO.ReportLineItemDTO item) {
    if (StringUtils.hasText(item.getSupplierName())) {
      if (StringUtils.hasText(item.getSupplierCode())) {
        return item.getSupplierName() + " (" + item.getSupplierCode() + ")";
      }
      return item.getSupplierName();
    }
    if (item.getSupplierId() != null) {
      return "Supplier #" + item.getSupplierId();
    }
    return "-";
  }

  private String formatRateLabel(VatRate rate) {
    return rate != null ? rate.getDisplayName() : VatRate.ZERO.getDisplayName();
  }

  private String formatDate(LocalDate date) {
    return date != null ? DATE_FORMATTER.format(date) : "-";
  }

  private String formatDateRange(LocalDate start, LocalDate end) {
    return formatDate(start) + " - " + formatDate(end);
  }

  private String formatTimestamp(Instant instant) {
    Instant value = instant != null ? instant : Instant.now();
    return TIMESTAMP_FORMATTER.format(value.atZone(DEFAULT_ZONE));
  }

  private String formatCurrency(BigDecimal amount) {
    DecimalFormat formatter = currencyFormatter();
    return formatter.format(amount != null ? amount : BigDecimal.ZERO);
  }

  private DecimalFormat currencyFormatter() {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("vi", "VN"));
    symbols.setDecimalSeparator('.');
    symbols.setGroupingSeparator(',');
    DecimalFormat decimalFormat = new DecimalFormat("#,##0.00₫", symbols);
    decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
    return decimalFormat;
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value != null ? value : "";
    }
    return value.substring(0, Math.max(0, maxLength - 3)) + "...";
  }

  private Map<String, Object> copyFilters(Map<String, Object> filters) {
    if (filters == null || filters.isEmpty()) {
      return new HashMap<>();
    }
    return new HashMap<>(filters);
  }

  private InputVATReportDTO initializeReportDTO(
      Long companyId,
      UUID periodId,
      Long supplierId,
      String vatClass,
      DateRange dateRange,
      VATReportHistory.ExportFormat format) {
    InputVATReportDTO dto = new InputVATReportDTO();
    dto.setCompanyId(companyId);
    dto.setPeriodId(periodId);
    dto.setSupplierId(supplierId);
    dto.setVatClass(vatClass);
    dto.setStartDate(dateRange.start());
    dto.setEndDate(dateRange.end());
    dto.setGenerationDate(Instant.now());
    dto.setGeneratedByName(getCurrentUsername());
    dto.setFormat(format);
    dto.setGrandTotalVAT(BigDecimal.ZERO);
    dto.setGrandTotalAmount(BigDecimal.ZERO);
    dto.setTotalVATByRate(new EnumMap<>(VatRate.class));
    dto.setReportId(UUID.randomUUID());

    if (supplierId != null) {
      supplierRepository
          .findByCompanyIdAndId(companyId, supplierId)
          .ifPresent(
              supplier -> {
                dto.setSupplierName(supplier.getName());
                dto.setSupplierCode(supplier.getCode());
              });
    }

    return dto;
  }

  private Supplier resolveSupplier(PurchaseBill bill) {
    if (bill.getSupplier() != null) {
      return bill.getSupplier();
    }
    Long supplierId = bill.getSupplierId();
    if (supplierId == null) {
      return null;
    }
    return supplierRepository
        .findByCompanyIdAndId(bill.getCompanyId(), supplierId)
        .orElse(null);
  }

  private boolean matchesVatClass(PurchaseBillLine line, String normalizedVatClass) {
    if (!StringUtils.hasText(normalizedVatClass)) {
      return true;
    }
    VatRate rate = line.getVatRate();
    if (rate == null) {
      return false;
    }
    return rate.name().equalsIgnoreCase(normalizedVatClass)
        || rate.getDisplayName().equalsIgnoreCase(normalizedVatClass);
  }

  private InputVATReportDTO.ReportLineItemDTO toReportLineItem(
      PurchaseBillLine line, PurchaseBill bill, Supplier supplier) {
    InputVATReportDTO.ReportLineItemDTO lineItem = new InputVATReportDTO.ReportLineItemDTO();
    lineItem.setBillId(bill.getId());
    lineItem.setBillNumber(bill.getBillNumber());
    lineItem.setBillDate(bill.getBillDate());
    lineItem.setSupplierId(bill.getSupplierId());
    if (supplier != null) {
      lineItem.setSupplierName(supplier.getName());
      lineItem.setSupplierCode(supplier.getCode());
    }
    lineItem.setVatRate(line.getVatRate());
    BigDecimal baseAmount = safe(line.getAmount());
    BigDecimal vatAmount = safe(line.getVatAmount());
    lineItem.setBaseAmount(baseAmount);
    lineItem.setVatAmount(vatAmount);
    lineItem.setTotalAmount(baseAmount.add(vatAmount));
    return lineItem;
  }

  private VATReportHistory buildReportHistory(
      UUID reportId,
      Long companyId,
      UUID periodId,
      Long supplierId,
      String vatClass,
      DateRange dateRange,
      VATReportHistory.ExportFormat format,
      InputVATReportDTO report) {
    VATReportHistory history = new VATReportHistory();
    // Only set ID if provided and entity already exists (for updates)
    // For new entities, let Hibernate generate the ID
    if (reportId != null) {
      history.setId(reportId);
    }
    history.setCompanyId(companyId);
    history.setReportType(VATReportHistory.ReportType.INPUT_VAT);
    history.setPeriodId(periodId);
    history.setSupplierId(supplierId);
    history.setVatClass(vatClass);
    history.setGenerationDate(report.getGenerationDate());
    history.setGeneratedBy(getCurrentUserId());
    history.setFormat(format);
    history.setStartDate(dateRange.start());
    history.setEndDate(dateRange.end());
    history.setHash(calculateReportHash(report));
    return history;
  }

  @SuppressWarnings("null")
  private void persistReportHistory(VATReportHistory history) {
    // Use merge() if ID is set (update), save() if ID is null (insert)
    if (history.getId() != null) {
      vatReportHistoryRepository.save(history);
    } else {
      VATReportHistory saved = vatReportHistoryRepository.save(history);
      history.setId(saved.getId());
    }
  }

  private String calculateReportHash(InputVATReportDTO dto) {
    try {
      byte[] payload = objectMapper.writeValueAsBytes(dto);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(payload);
      return bytesToHex(hashed);
    } catch (Exception e) {
      logger.warn("Failed to calculate VAT report hash: {}", e.getMessage());
      return UUID.randomUUID().toString().replace("-", "");
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private Map<String, Object> buildAuditFilters(
      UUID periodId,
      Long supplierId,
      String vatClass,
      DateRange dateRange,
      Map<String, Object> originalFilters) {
    Map<String, Object> auditFilters = new HashMap<>();
    if (periodId != null) {
      auditFilters.put("periodId", periodId);
    }
    if (supplierId != null) {
      auditFilters.put("supplierId", supplierId);
    }
    if (StringUtils.hasText(vatClass)) {
      auditFilters.put("vatClass", vatClass);
    }
    auditFilters.put("startDate", dateRange.start());
    auditFilters.put("endDate", dateRange.end());
    if (originalFilters != null && !originalFilters.isEmpty()) {
      auditFilters.put("rawFilters", originalFilters);
    }
    return auditFilters;
  }

  private Long getCurrentUserId() {
    try {
      return SecurityUtils.getCurrentUserId();
    } catch (ResponseStatusException e) {
      // Re-throw ResponseStatusException as-is (401 Unauthorized)
      throw e;
    } catch (Exception e) {
      logger.warn("Failed to get current user ID: {}", e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user for VAT report generation", e);
    }
  }

  private String getCurrentUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      return authentication.getName();
    }
    return "System";
  }

  private LocalDate parseLocalDate(Object value) {
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    if (value instanceof java.sql.Date sqlDate) {
      return sqlDate.toLocalDate();
    }
    if (value instanceof String text && StringUtils.hasText(text)) {
      return LocalDate.parse(text.trim());
    }
    return null;
  }

  private BigDecimal safe(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private Instant toStartInstant(LocalDate date) {
    if (date == null) {
      return null;
    }
    return date.atStartOfDay(DEFAULT_ZONE).toInstant();
  }

  private Instant toEndInstant(LocalDate date) {
    if (date == null) {
      return null;
    }
    return date.atTime(LocalTime.MAX).atZone(DEFAULT_ZONE).toInstant();
  }

  private boolean filterByDateRange(VATCorrection correction, Instant start, Instant end) {
    Instant correctedAt = correction.getCorrectedAt();
    if (start != null && (correctedAt == null || correctedAt.isBefore(start))) {
      return false;
    }
    if (end != null && (correctedAt == null || correctedAt.isAfter(end))) {
      return false;
    }
    return true;
  }

  private boolean filterByUser(VATCorrection correction, Long correctedById) {
    if (correctedById == null) {
      return true;
    }
    return Objects.equals(correction.getCorrectedById(), correctedById);
  }

  private Long parseLong(Object value) {
    if (value instanceof Long l) {
      return l;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && StringUtils.hasText(text)) {
      return Long.parseLong(text.trim());
    }
    return null;
  }

  private VATCorrectionDTO mapCorrectionToDTO(VATCorrection correction) {
    VATCorrectionDTO dto = new VATCorrectionDTO();
    dto.setId(correction.getId());
    dto.setPurchaseBillId(correction.getPurchaseBillId());
    dto.setPurchaseBillLineId(correction.getPurchaseBillLineId());
    dto.setOldVatAmount(correction.getOldVatAmount());
    dto.setNewVatAmount(correction.getNewVatAmount());
    dto.setDifference(correction.getNewVatAmount().subtract(correction.getOldVatAmount()));
    dto.setReason(correction.getReason());
    dto.setStatus(correction.getStatus());
    dto.setCorrectedById(correction.getCorrectedById());
    dto.setCorrectedAt(correction.getCorrectedAt());
    dto.setApprovedById(correction.getApprovedById());
    dto.setApprovedAt(correction.getApprovedAt());
    return dto;
  }

  private VATCorrection.Status resolveCorrectionStatus(Map<String, Object> filters) {
    if (filters == null || filters.isEmpty()) {
      return null;
    }
    Object statusObj = filters.get("status");
    if (statusObj instanceof VATCorrection.Status status) {
      return status;
    }
    if (statusObj instanceof String text && StringUtils.hasText(text)) {
      try {
        return VATCorrection.Status.valueOf(text.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
        logger.warn("Unknown VAT correction status filter: {}", text);
      }
    }
    return null;
  }

  private BigDecimal recalculateBillVatAmount(Long companyId, UUID billId) {
    return purchaseBillLineRepository
        .findByCompanyIdAndPurchaseBillIdOrderByLineNumberAsc(companyId, billId)
        .stream()
        .map(line -> safe(line.getVatAmount()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private void ensureVatCorrectionRole() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getAuthorities() == null) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Insufficient permissions for VAT corrections");
    }
    boolean allowed = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(
            authority -> "ROLE_CHIEF_ACCOUNTANT".equalsIgnoreCase(authority)
                || "ROLE_CFO".equalsIgnoreCase(authority));
    if (!allowed) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only Chief Accountant or CFO can perform VAT corrections");
    }
  }

  private record DateRange(LocalDate start, LocalDate end) {
  }

  private record ReportComputation(InputVATReportDTO report, DateRange dateRange) {
  }
}

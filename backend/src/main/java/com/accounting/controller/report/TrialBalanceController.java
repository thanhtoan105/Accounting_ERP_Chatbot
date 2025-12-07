package com.accounting.controller.report;

import com.accounting.dto.DrillDownResponseDTO;
import com.accounting.dto.TrialBalanceResponseDTO;
import com.accounting.dto.TrialBalanceValidationDTO;
import com.accounting.enums.AmountType;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.TrialBalanceService;
import com.accounting.service.impl.TrialBalanceServiceImpl;
import com.accounting.service.report.TrialBalanceSnapshotService.PdfExportResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Trial Balance report (S06-DN) endpoints.
 * Provides access to trial balance data, drill-down to voucher level,
 * validation preflight, and export to Excel/PDF formats.
 */
@RestController
@RequestMapping("/api/v1/reports/trial-balance")
public class TrialBalanceController {

  private static final Logger logger = LoggerFactory.getLogger(TrialBalanceController.class);
  private static final int MAX_PAGE_SIZE = 100;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final TrialBalanceService trialBalanceService;
  private final AuditService auditService;

  public TrialBalanceController(
      TrialBalanceService trialBalanceService,
      AuditService auditService) {
    this.trialBalanceService = trialBalanceService;
    this.auditService = auditService;
  }

  /**
   * Get trial balance data for a specific period.
   *
   * @param periodId period ID (UUID)
   * @return trial balance response with account balances
   */
  @PreAuthorize("hasAnyAuthority('chief_accountant','CHIEF_ACCOUNTANT','ROLE_CHIEF_ACCOUNTANT','admin','ADMIN','ROLE_ADMIN')")
  @GetMapping
  public ResponseEntity<TrialBalanceResponseDTO> getTrialBalance(
      @RequestParam UUID periodId) {
    TrialBalanceResponseDTO data = trialBalanceService.getTrialBalanceData(periodId);
    return ResponseEntity.ok(data);
  }

  /**
   * Export trial balance report to Excel.
   *
   * @param periodId period ID (UUID)
   * @param format export format (default: xlsx)
   * @return Excel file as byte array
   */
  @PreAuthorize("hasAnyAuthority('chief_accountant','CHIEF_ACCOUNTANT','ROLE_CHIEF_ACCOUNTANT','admin','ADMIN','ROLE_ADMIN')")
  @GetMapping("/export")
  public ResponseEntity<byte[]> exportTrialBalance(
      @RequestParam UUID periodId,
      @RequestParam(defaultValue = "xlsx") String format,
      HttpServletRequest request) {

    byte[] bytes = trialBalanceService.exportToExcel(periodId);

    // Audit export (best-effort)
    try {
      Long companyId = CompanyContext.getCompanyId();
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      Long userId = (auth != null && auth.getPrincipal() != null)
          ? Long.parseLong(auth.getPrincipal().toString())
          : null;
      auditService.logReportExport(companyId, userId, "XLSX", request);
    } catch (Exception e) {
      logger.warn("Failed to log report export audit: {}", e.getMessage());
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    headers.set(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=trial-balance-" + periodId.toString() + ".xlsx");
    return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
  }

  /**
   * Get drill-down vouchers for a specific account and amount type.
   * Returns paginated list of vouchers that contribute to the selected amount
   * in the Trial Balance table.
   *
   * @param periodId period ID
   * @param accountId account ID to drill into
   * @param amountType type of amount column (OPENING_DEBIT, PERIOD_CREDIT, etc.)
   * @param page page number (default: 0)
   * @param size page size (default: 20, max: 100)
   * @param sortBy sort field (date, amount, voucherNumber)
   * @param sortDir sort direction (asc, desc)
   * @return paginated drill-down response with voucher details
   */
  @PreAuthorize("hasAnyAuthority('chief_accountant','CHIEF_ACCOUNTANT','ROLE_CHIEF_ACCOUNTANT','admin','ADMIN','ROLE_ADMIN')")
  @GetMapping("/drill-down")
  public ResponseEntity<DrillDownResponseDTO> getDrillDownVouchers(
      @RequestParam UUID periodId,
      @RequestParam Long accountId,
      @RequestParam AmountType amountType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "voucherDate") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDir,
      HttpServletRequest request) {

    // Validate page size
    if (size <= 0 || size > MAX_PAGE_SIZE) {
      size = DEFAULT_PAGE_SIZE;
    }

    // Create sort
    Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
        ? Sort.Direction.ASC : Sort.Direction.DESC;

    // Map sortBy to entity field
    String sortField = mapSortField(sortBy);
    Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

    DrillDownResponseDTO response = trialBalanceService.getDrillDownVouchers(
        periodId, accountId, amountType, pageable);

    // Audit drill-down access (best-effort) - logged as DRILL_DOWN format
    try {
      Long companyId = CompanyContext.getCompanyId();
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      Long userId = (auth != null && auth.getPrincipal() != null)
          ? Long.parseLong(auth.getPrincipal().toString())
          : null;
      auditService.logReportExport(companyId, userId, "DRILL_DOWN", request);
    } catch (Exception e) {
      logger.warn("Failed to log drill-down access audit: {}", e.getMessage());
    }

    return ResponseEntity.ok(response);
  }

  /**
   * Validate trial balance before export.
   * Checks if GL is balanced and period status is valid.
   *
   * @param request validation request with periodId
   * @return validation result with errors if any
   */
  @PreAuthorize("hasAnyAuthority('chief_accountant','CHIEF_ACCOUNTANT','ROLE_CHIEF_ACCOUNTANT','admin','ADMIN','ROLE_ADMIN')")
  @PostMapping("/validate")
  public ResponseEntity<TrialBalanceValidationDTO> validateForExport(
      @RequestBody ValidationRequest request) {
    TrialBalanceValidationDTO validation = trialBalanceService.validateForExport(request.getPeriodId());
    return ResponseEntity.ok(validation);
  }

  /**
   * Export trial balance report to PDF with TT200 layout.
   *
   * @param periodId period ID (UUID)
   * @param snapshotId optional snapshot ID for reproducibility
   * @return PDF file as byte array
   */
  @PreAuthorize("hasAnyAuthority('chief_accountant','CHIEF_ACCOUNTANT','ROLE_CHIEF_ACCOUNTANT','admin','ADMIN','ROLE_ADMIN')")
  @GetMapping("/export/pdf")
  public ResponseEntity<byte[]> exportTrialBalancePdf(
      @RequestParam UUID periodId,
      @RequestParam(required = false) UUID snapshotId,
      HttpServletRequest request) {

    byte[] bytes = trialBalanceService.exportToPdf(periodId, snapshotId);

    // Audit export (best-effort)
    try {
      Long companyId = CompanyContext.getCompanyId();
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      Long userId = (auth != null && auth.getPrincipal() != null)
          ? Long.parseLong(auth.getPrincipal().toString())
          : null;
      auditService.logReportExport(companyId, userId, "PDF", request);
    } catch (Exception e) {
      logger.warn("Failed to log PDF export audit: {}", e.getMessage());
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.set(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=trial-balance-" + periodId.toString() + ".pdf");

    // AC7.1-06 & AC7.1-10: Add X-Content-SHA256 and X-Snapshot-Id headers
    try {
      PdfExportResult exportResult = TrialBalanceServiceImpl.getLastExportResult();
      if (exportResult != null) {
        if (exportResult.dataHash() != null) {
          headers.set("X-Content-SHA256", exportResult.dataHash());
        }
        if (exportResult.snapshotId() != null) {
          headers.set("X-Snapshot-Id", exportResult.snapshotId().toString());
        }
        TrialBalanceServiceImpl.clearLastExportResult();
      }
    } catch (Exception e) {
      logger.warn("Failed to set export headers: {}", e.getMessage());
    }

    return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
  }

  /**
   * Map frontend sort field names to entity field names.
   */
  private String mapSortField(String sortBy) {
    switch (sortBy.toLowerCase()) {
      case "date":
      case "voucherdate":
        return "voucherDate";
      case "amount":
        return "debit"; // Sort by debit for simplicity
      case "vouchernumber":
      case "number":
        return "voucherNumber";
      default:
        return "voucherDate";
    }
  }

  /**
   * Request body for validation endpoint.
   */
  public static class ValidationRequest {
    private UUID periodId;

    public UUID getPeriodId() {
      return periodId;
    }

    public void setPeriodId(UUID periodId) {
      this.periodId = periodId;
    }
  }
}


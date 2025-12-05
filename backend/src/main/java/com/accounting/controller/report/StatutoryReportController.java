package com.accounting.controller.report;

import com.accounting.dto.report.AccountContributionDTO;
import com.accounting.dto.report.DetailedLedgerDTO;
import com.accounting.dto.report.StatutoryReportDTO;
import com.accounting.dto.report.ValidationResultDTO;
import com.accounting.service.DrillDownService;
import com.accounting.service.DrillDownService.VoucherDetailDTO;
import com.accounting.service.DrillDownService.VoucherSummaryDTO;
import com.accounting.service.StatutoryReportService;
import com.accounting.service.impl.report.StatutoryReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for statutory financial reports.
 * Provides endpoints for generating B01, B02, B03, F01 reports with export capability.
 */
@RestController
@RequestMapping("/api/reports/statutory")
@Tag(name = "Statutory Reports", description = "TT200 statutory financial reports (B01, B02, B03, F01)")
public class StatutoryReportController {

  private final StatutoryReportService statutoryReportService;
  private final StatutoryReportExportService exportService;
  private final DrillDownService drillDownService;

  public StatutoryReportController(
      StatutoryReportService statutoryReportService,
      StatutoryReportExportService exportService,
      DrillDownService drillDownService) {
    this.statutoryReportService = statutoryReportService;
    this.exportService = exportService;
    this.drillDownService = drillDownService;
  }

  // ==================== B01 Balance Sheet ====================

  @GetMapping("/b01")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Generate B01-DN Balance Sheet",
      description = "Generate Balance Sheet for a period with optional comparison period")
  public ResponseEntity<StatutoryReportDTO> generateBalanceSheet(
      @Parameter(description = "Period ID", required = true)
      @RequestParam UUID periodId,
      @Parameter(description = "Comparison period ID for variance analysis")
      @RequestParam(required = false) UUID comparisonPeriodId) {

    StatutoryReportDTO report = statutoryReportService.generateBalanceSheet(periodId, comparisonPeriodId);
    return ResponseEntity.ok(report);
  }

  @GetMapping("/b01/export/excel")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Export B01 to Excel")
  public ResponseEntity<byte[]> exportBalanceSheetToExcel(
      @RequestParam UUID periodId,
      @RequestParam(required = false) UUID comparisonPeriodId) {

    StatutoryReportDTO report = statutoryReportService.generateBalanceSheet(periodId, comparisonPeriodId);
    byte[] excelBytes = exportService.exportToExcel(report);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=B01-DN_" + periodId + ".xlsx")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(excelBytes);
  }

  @GetMapping("/b01/export/pdf")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Export B01 to PDF")
  public ResponseEntity<byte[]> exportBalanceSheetToPdf(
      @RequestParam UUID periodId,
      @RequestParam(required = false) UUID comparisonPeriodId) {

    StatutoryReportDTO report = statutoryReportService.generateBalanceSheet(periodId, comparisonPeriodId);
    byte[] pdfBytes = exportService.exportToPdf(report);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=B01-DN_" + periodId + ".pdf")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdfBytes);
  }

  // ==================== B02 Income Statement ====================

  @GetMapping("/b02")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Generate B02-DN Income Statement",
      description = "Generate Income Statement for a period with optional comparison period")
  public ResponseEntity<StatutoryReportDTO> generateIncomeStatement(
      @RequestParam UUID periodId,
      @RequestParam(required = false) UUID comparisonPeriodId) {

    StatutoryReportDTO report = statutoryReportService.generateIncomeStatement(periodId, comparisonPeriodId);
    return ResponseEntity.ok(report);
  }

  @GetMapping("/b02/export/excel")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Export B02 to Excel")
  public ResponseEntity<byte[]> exportIncomeStatementToExcel(
      @RequestParam UUID periodId,
      @RequestParam(required = false) UUID comparisonPeriodId) {

    StatutoryReportDTO report = statutoryReportService.generateIncomeStatement(periodId, comparisonPeriodId);
    byte[] excelBytes = exportService.exportToExcel(report);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=B02-DN_" + periodId + ".xlsx")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(excelBytes);
  }

  @GetMapping("/b02/export/pdf")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Export B02 to PDF")
  public ResponseEntity<byte[]> exportIncomeStatementToPdf(
      @RequestParam UUID periodId,
      @RequestParam(required = false) UUID comparisonPeriodId) {

    StatutoryReportDTO report = statutoryReportService.generateIncomeStatement(periodId, comparisonPeriodId);
    byte[] pdfBytes = exportService.exportToPdf(report);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=B02-DN_" + periodId + ".pdf")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdfBytes);
  }

  // ==================== B03 Cash Flow Statement ====================

  @GetMapping("/b03")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Generate B03-DN Cash Flow Statement",
      description = "Generate Cash Flow Statement using direct method")
  public ResponseEntity<StatutoryReportDTO> generateCashFlowStatement(
      @RequestParam UUID periodId) {

    StatutoryReportDTO report = statutoryReportService.generateCashFlowStatement(periodId);
    return ResponseEntity.ok(report);
  }

  @GetMapping("/b03/export/excel")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Export B03 to Excel")
  public ResponseEntity<byte[]> exportCashFlowStatementToExcel(@RequestParam UUID periodId) {

    StatutoryReportDTO report = statutoryReportService.generateCashFlowStatement(periodId);
    byte[] excelBytes = exportService.exportToExcel(report);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=B03-DN_" + periodId + ".xlsx")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(excelBytes);
  }

  // ==================== F01 Detailed Ledger ====================

  @GetMapping("/f01")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Generate F01 Detailed Ledger",
      description = "Generate detailed ledger for specific accounts")
  public ResponseEntity<DetailedLedgerDTO> generateDetailedLedger(
      @Parameter(description = "Comma-separated account codes")
      @RequestParam String accountCodes,
      @RequestParam UUID periodId,
      @RequestParam(required = false) String subsidiaryType,
      @RequestParam(required = false) Long subsidiaryId) {

    String[] codes = accountCodes.split(",");
    DetailedLedgerDTO ledger = statutoryReportService.generateDetailedLedger(
        codes, periodId, subsidiaryType, subsidiaryId);
    return ResponseEntity.ok(ledger);
  }

  // ==================== Drill-Down ====================

  @GetMapping("/drill-down/line/{reportType}/{lineCode}")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Get accounts contributing to a report line",
      description = "Drill down from report line to contributing GL accounts")
  public ResponseEntity<Page<AccountContributionDTO>> drillDownToAccounts(
      @PathVariable String reportType,
      @PathVariable String lineCode,
      @RequestParam UUID periodId,
      Pageable pageable) {

    Page<AccountContributionDTO> accounts = drillDownService.getAccountsForLine(
        reportType, lineCode, periodId, pageable);
    return ResponseEntity.ok(accounts);
  }

  @GetMapping("/drill-down/account/{accountCode}")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Get vouchers for an account",
      description = "Drill down from account to vouchers")
  public ResponseEntity<Page<VoucherSummaryDTO>> drillDownToVouchers(
      @PathVariable String accountCode,
      @RequestParam UUID periodId,
      Pageable pageable) {

    Page<VoucherSummaryDTO> vouchers = drillDownService.getVouchersForAccount(
        accountCode, periodId, pageable);
    return ResponseEntity.ok(vouchers);
  }

  @GetMapping("/drill-down/voucher/{voucherId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Get voucher detail",
      description = "Get full voucher detail including lines and attachments")
  public ResponseEntity<VoucherDetailDTO> getVoucherDetail(@PathVariable UUID voucherId) {
    VoucherDetailDTO detail = drillDownService.getVoucherDetail(voucherId);
    return ResponseEntity.ok(detail);
  }

  // ==================== Validation ====================

  @GetMapping("/validate/{reportType}")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'ACCOUNTANT')")
  @Operation(summary = "Validate report before export",
      description = "Check for NULL values and balance validation")
  public ResponseEntity<ValidationResultDTO> validateReport(
      @PathVariable String reportType,
      @RequestParam UUID periodId) {

    ValidationResultDTO result = statutoryReportService.validateForExport(periodId, reportType);
    HttpStatus status = result.isValid() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
    return ResponseEntity.status(status).body(result);
  }
}

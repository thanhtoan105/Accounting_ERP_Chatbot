package com.accounting.controller.sales;

import com.accounting.dto.ARVATCorrectionCreateRequest;
import com.accounting.dto.ARVATCorrectionDTO;
import com.accounting.dto.OutputVATReportDTO;
import com.accounting.entity.ARVATCorrection;
import com.accounting.service.ARVATCorrectionService;
import com.accounting.service.ARVATReportService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for AR (Accounts Receivable) VAT reporting and corrections.
 * Handles output VAT reports and VAT corrections for sales invoices.
 */
@RestController
@RequestMapping("/api/v1/ar-vat")
public class ARVATController {

  private static final MediaType MEDIA_TYPE_XLSX =
      new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ARVATReportService arVatReportService;
  private final ARVATCorrectionService arVatCorrectionService;

  public ARVATController(
      ARVATReportService arVatReportService,
      ARVATCorrectionService arVatCorrectionService) {
    this.arVatReportService = arVatReportService;
    this.arVatCorrectionService = arVatCorrectionService;
  }

  /**
   * Generate output VAT report (AC-VAT-005).
   * GET /api/v1/ar-vat-report?period=2025-01&customerId=123&vatClass=10
   */
  @GetMapping("/report")
  public ResponseEntity<OutputVATReportDTO> generateVATReport(
      @RequestParam(required = false) UUID periodId,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) String vatClass,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    Map<String, Object> filters = new HashMap<>();
    if (startDate != null) {
      filters.put("startDate", startDate);
    }
    if (endDate != null) {
      filters.put("endDate", endDate);
    }
    OutputVATReportDTO report =
        arVatReportService.generateVATReport(periodId, customerId, vatClass, filters);
    return ResponseEntity.ok(report);
  }

  /**
   * Export output VAT report to Excel (AC-VAT-005).
   * GET /api/v1/ar-vat-report/export?format=EXCEL&period=2025-01
   */
  @GetMapping("/report/export")
  public ResponseEntity<byte[]> exportVATReport(
      @RequestParam UUID reportId,
      @RequestParam(defaultValue = "EXCEL") String format) {
    byte[] file = arVatReportService.exportVATReport(reportId, format);
    MediaType mediaType = "PDF".equalsIgnoreCase(format) ? MediaType.APPLICATION_PDF : MEDIA_TYPE_XLSX;
    String extension = "PDF".equalsIgnoreCase(format) ? "pdf" : "xlsx";
    String filename = String.format("output-vat-report-%s.%s", reportId, extension);

    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(file);
  }

  /**
   * Create VAT correction (AC-VAT-006).
   * POST /api/v1/ar-vat-corrections
   */
  @PostMapping("/corrections")
  public ResponseEntity<ARVATCorrectionDTO> createCorrection(
      @Valid @RequestBody ARVATCorrectionCreateRequest request) {
    ARVATCorrectionDTO correction = arVatCorrectionService.createCorrection(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(correction);
  }

  /**
   * List VAT corrections for an invoice (AC-VAT-006).
   * GET /api/v1/ar-vat-corrections?invoiceId=xxx&status=PENDING
   */
  @GetMapping("/corrections")
  public ResponseEntity<List<ARVATCorrectionDTO>> listCorrections(
      @RequestParam UUID invoiceId,
      @RequestParam(required = false) ARVATCorrection.Status status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(required = false) Long correctedById) {
    Map<String, Object> filters = new HashMap<>();
    if (status != null) {
      filters.put("status", status);
    }
    if (startDate != null) {
      filters.put("startDate", startDate);
    }
    if (endDate != null) {
      filters.put("endDate", endDate);
    }
    if (correctedById != null) {
      filters.put("correctedById", correctedById);
    }

    List<ARVATCorrectionDTO> corrections = arVatCorrectionService.getCorrections(invoiceId, filters);
    return ResponseEntity.ok(corrections);
  }

  /**
   * Approve VAT correction (AC-VAT-006).
   * POST /api/v1/ar-vat-corrections/:id/approve
   */
  @PostMapping("/corrections/{correctionId}/approve")
  public ResponseEntity<ARVATCorrectionDTO> approveCorrection(@PathVariable UUID correctionId) {
    ARVATCorrectionDTO correction = arVatCorrectionService.approveCorrection(correctionId, null);
    return ResponseEntity.ok(correction);
  }

  /**
   * Reject VAT correction (AC-VAT-006).
   * POST /api/v1/ar-vat-corrections/:id/reject
   */
  @PostMapping("/corrections/{correctionId}/reject")
  public ResponseEntity<ARVATCorrectionDTO> rejectCorrection(
      @PathVariable UUID correctionId,
      @RequestParam String reason) {
    ARVATCorrectionDTO correction = arVatCorrectionService.rejectCorrection(correctionId, reason);
    return ResponseEntity.ok(correction);
  }
}


package com.accounting.controller.ap;

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

import com.accounting.dto.InputVATReportDTO;
import com.accounting.dto.InputVATReportRequest;
import com.accounting.dto.VATCorrectionCreateRequest;
import com.accounting.dto.VATCorrectionDTO;
import com.accounting.entity.VATCorrection;
import com.accounting.service.VATService;

import jakarta.validation.Valid;

/**
 * REST controller for VAT reporting and corrections.
 */
@RestController
@RequestMapping("/api/v1/vat")
public class VATController {

  private static final MediaType MEDIA_TYPE_XLSX =
      new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final VATService vatService;

  public VATController(VATService vatService) {
    this.vatService = vatService;
  }

  @PostMapping("/reports/input")
  public ResponseEntity<InputVATReportDTO> generateInputReport(
      @Valid @RequestBody InputVATReportRequest request) {
    Map<String, Object> filters = buildReportFilters(request);
    InputVATReportDTO report =
        vatService.generateInputVATReport(
            request.getPeriodId(), request.getSupplierId(), request.getVatClass(), filters);
    return ResponseEntity.ok(report);
  }

  @SuppressWarnings("null")
  @GetMapping("/reports/{reportId}/export")
  public ResponseEntity<byte[]> exportInputReport(
      @PathVariable UUID reportId, @RequestParam(defaultValue = "PDF") String format) {
    byte[] file = vatService.exportInputVATReport(reportId, format);
    MediaType mediaType =
        "PDF".equalsIgnoreCase(format) ? MediaType.APPLICATION_PDF : MEDIA_TYPE_XLSX;
    String extension = "PDF".equalsIgnoreCase(format) ? "pdf" : "xlsx";
    String filename = String.format("input-vat-report-%s.%s", reportId, extension);

    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(file);
  }

  @PostMapping("/corrections")
  public ResponseEntity<VATCorrectionDTO> createCorrection(
      @Valid @RequestBody VATCorrectionCreateRequest request) {
    VATCorrectionDTO correction = vatService.createVATCorrection(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(correction);
  }

  @PostMapping("/corrections/{correctionId}/approve")
  public ResponseEntity<VATCorrectionDTO> approveCorrection(@PathVariable UUID correctionId) {
    VATCorrectionDTO correction = vatService.approveVATCorrection(correctionId, null);
    return ResponseEntity.ok(correction);
  }

  @GetMapping("/corrections")
  public ResponseEntity<List<VATCorrectionDTO>> listCorrections(
      @RequestParam UUID billId,
      @RequestParam(required = false) VATCorrection.Status status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(required = false) Long correctedById) {
    Map<String, Object> filters = new HashMap<>();
    filters.put("billId", billId);
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

    List<VATCorrectionDTO> corrections = vatService.getVATCorrections(billId, filters);
    return ResponseEntity.ok(corrections);
  }

  private Map<String, Object> buildReportFilters(InputVATReportRequest request) {
    Map<String, Object> filters = new HashMap<>(request.getFilters());
    if (request.getStartDate() != null) {
      filters.put("startDate", request.getStartDate());
    }
    if (request.getEndDate() != null) {
      filters.put("endDate", request.getEndDate());
    }
    if (request.getVatClass() != null) {
      filters.put("vatClass", request.getVatClass());
    }
    if (request.getSupplierId() != null) {
      filters.put("supplierId", request.getSupplierId());
    }
    return filters;
  }
}

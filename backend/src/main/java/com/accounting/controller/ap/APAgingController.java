package com.accounting.controller.ap;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.APAgingReportDTO;
import com.accounting.dto.AgingBillDetailsDTO;
import com.accounting.dto.BatchReminderRequestDTO;
import com.accounting.dto.BatchReminderResultDTO;
import com.accounting.dto.OverdueCountDTO;
import com.accounting.dto.OverdueSupplierDTO;
import com.accounting.dto.ReminderRequestDTO;
import com.accounting.dto.ReminderResultDTO;
import com.accounting.service.APAgingAlertService;
import com.accounting.service.APAgingService;
import com.accounting.service.AuditService;

import jakarta.validation.Valid;

/**
 * REST controller for AP aging report operations.
 * Provides endpoints for aging reports, overdue detection, drill-down, and export.
 */
@RestController
@RequestMapping("/api/v1/ap-aging")
public class APAgingController {

  private final APAgingService agingService;
  private final APAgingAlertService alertService;
  private final AuditService auditService;

  public APAgingController(
      APAgingService agingService, APAgingAlertService alertService, AuditService auditService) {
    this.agingService = agingService;
    this.alertService = alertService;
    this.auditService = auditService;
  }

  /**
   * Get paginated aging report with filters.
   *
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20, max: 100)
   * @param supplier filter by supplier ID (optional)
   * @param period filter by period ID (optional)
   * @param asOfDate as-of date for aging calculation (optional, default: today)
   * @param status filter by bill status (optional)
   * @param bucket filter by aging bucket (optional: CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90)
   * @param sort sort parameters (optional)
   * @return paginated aging report
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getAgingReport(
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false) Long supplier,
      @RequestParam(required = false) Long period,
      @RequestParam(required = false) LocalDate asOfDate,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String bucket,
      @RequestParam(required = false) String[] sort) {

    // Validate page size
    if (size > 100) {
      size = 100;
    }

    // Build sort object
    Sort sortObj = Sort.unsorted();
    if (sort != null && sort.length > 0) {
      List<Sort.Order> orders = new java.util.ArrayList<>();
      for (String sortParam : sort) {
        String[] parts = sortParam.split(",");
        if (parts.length == 2) {
          Sort.Direction direction =
              parts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
          orders.add(new Sort.Order(direction, parts[0]));
        }
      }
      if (!orders.isEmpty()) {
        sortObj = Sort.by(orders);
      }
    }

    Pageable pageable = PageRequest.of(page, size, sortObj);
    Page<APAgingReportDTO> result =
        agingService.getAgingReport(supplier, period, asOfDate, status, bucket, pageable);

    // Log audit event
    try {
      Long companyId = com.accounting.security.CompanyContext.getCompanyId();
      Long userId = com.accounting.security.SecurityUtils.getCurrentUserId();
      if (companyId != null && userId != null) {
        Map<String, Object> filters = new HashMap<>();
        if (supplier != null) {
          filters.put("supplier", supplier);
        }
        if (period != null) {
          filters.put("period", period);
        }
        if (asOfDate != null) {
          filters.put("asOfDate", asOfDate.toString());
        }
        if (status != null) {
          filters.put("status", status);
        }
        if (bucket != null) {
          filters.put("bucket", bucket);
        }
        auditService.logAgingReportViewed(companyId, userId, filters);
      }
    } catch (Exception e) {
      // Log error but don't fail the request
      org.slf4j.LoggerFactory.getLogger(APAgingController.class)
          .warn("Failed to log aging report view: {}", e.getMessage());
    }

    Map<String, Object> response = new HashMap<>();
    response.put("content", result.getContent());
    response.put("totalElements", result.getTotalElements());
    response.put("totalPages", result.getTotalPages());
    response.put("number", result.getNumber());
    response.put("size", result.getSize());
    response.put("first", result.isFirst());
    response.put("last", result.isLast());

    return ResponseEntity.ok(response);
  }

  /**
   * Get top overdue suppliers for dashboard badge.
   *
   * @param period filter by period ID (optional)
   * @param asOfDate as-of date for aging calculation (optional, default: today)
   * @param limit maximum number of suppliers to return (default: 5)
   * @return list of overdue suppliers
   */
  @GetMapping("/overdue")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<List<OverdueSupplierDTO>> getOverdueSuppliers(
      @RequestParam(required = false) Long period,
      @RequestParam(required = false) LocalDate asOfDate,
      @RequestParam(required = false, defaultValue = "5") Integer limit) {

    List<OverdueSupplierDTO> result = agingService.getOverdueSuppliers(period, asOfDate, limit);
    return ResponseEntity.ok(result);
  }

  /**
   * Get count of overdue payables for dashboard badge.
   *
   * @param period filter by period ID (optional)
   * @param asOfDate as-of date for aging calculation (optional, default: today)
   * @return overdue count
   */
  @GetMapping("/overdue/count")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<OverdueCountDTO> getOverdueCount(
      @RequestParam(required = false) Long period,
      @RequestParam(required = false) LocalDate asOfDate) {

    OverdueCountDTO result = agingService.getOverdueCount(period, asOfDate);
    return ResponseEntity.ok(result);
  }

  /**
   * Get bill/payment history for drill-down.
   *
   * @param supplierId supplier ID
   * @param bucket aging bucket (CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90)
   * @param period filter by period ID (optional)
   * @param asOfDate as-of date for aging calculation (optional, default: today)
   * @param status filter by bill status (optional)
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20, max: 100)
   * @return paginated bill details with payment history
   */
  @GetMapping("/{supplierId}/bills")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getAgingBillDetails(
      @PathVariable Long supplierId,
      @RequestParam(required = false) String bucket,
      @RequestParam(required = false) Long period,
      @RequestParam(required = false) LocalDate asOfDate,
      @RequestParam(required = false) String status,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size) {

    // Validate page size
    if (size > 100) {
      size = 100;
    }

    Pageable pageable = PageRequest.of(page, size);
    Page<AgingBillDetailsDTO> result =
        agingService.getAgingBillDetails(
            supplierId, bucket, period, asOfDate, status, pageable);

    try {
      Long companyId = com.accounting.security.CompanyContext.getCompanyId();
      Long userId = com.accounting.security.SecurityUtils.getCurrentUserId();
      if (companyId != null && userId != null) {
        auditService.logAgingDrilldownViewed(companyId, userId, supplierId, bucket);
      }
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(APAgingController.class)
          .warn("Failed to log aging drill-down view: {}", e.getMessage());
    }

    Map<String, Object> response = new HashMap<>();
    response.put("content", result.getContent());
    response.put("totalElements", result.getTotalElements());
    response.put("totalPages", result.getTotalPages());
    response.put("number", result.getNumber());
    response.put("size", result.getSize());
    response.put("first", result.isFirst());
    response.put("last", result.isLast());

    return ResponseEntity.ok(response);
  }

  /**
   * Export aging report to Excel or PDF.
   *
   * @param format export format (EXCEL or PDF)
   * @param supplier filter by supplier ID (optional)
   * @param period filter by period ID (optional)
   * @param asOfDate as-of date for aging calculation (optional, default: today)
   * @param status filter by bill status (optional)
   * @param bucket filter by aging bucket (optional)
   * @return exported file
   */
  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<byte[]> exportAgingReport(
      @RequestParam(required = false, defaultValue = "EXCEL") String format,
      @RequestParam(required = false) Long supplier,
      @RequestParam(required = false) Long period,
      @RequestParam(required = false) LocalDate asOfDate,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String bucket) {

    byte[] fileContent =
        agingService.exportAgingReport(format, supplier, period, asOfDate, status, bucket);

    // Log audit event
    try {
      Long companyId = com.accounting.security.CompanyContext.getCompanyId();
      Long userId = com.accounting.security.SecurityUtils.getCurrentUserId();
      if (companyId != null && userId != null) {
        // Use generic report export logging
        auditService.logReportExport(companyId, userId, format, null);
      }
    } catch (Exception e) {
      // Log error but don't fail the request
      org.slf4j.LoggerFactory.getLogger(APAgingController.class)
          .warn("Failed to log aging report export: {}", e.getMessage());
    }

    HttpHeaders headers = new HttpHeaders();
    if (format.equalsIgnoreCase("PDF")) {
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("attachment", "ap-aging-report.pdf");
    } else {
      headers.setContentType(
          MediaType.parseMediaType(
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
      headers.setContentDispositionFormData("attachment", "ap-aging-report.xlsx");
    }

      return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
  }

  /**
   * Send reminder for overdue payables.
   *
   * @param request reminder request with supplier ID, bill IDs, recipients, and optional message
   * @return reminder result
   */
  @PostMapping("/remind")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<ReminderResultDTO> sendReminder(@RequestBody @Valid ReminderRequestDTO request) {
    ReminderResultDTO result = alertService.sendReminder(request);
    return ResponseEntity.ok(result);
  }

  /**
   * Send batch reminders for multiple suppliers.
   *
   * @param request batch reminder request with supplier IDs, recipients, and optional message
   * @return batch reminder result
   */
  @PostMapping("/remind/batch")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<BatchReminderResultDTO> sendBatchReminders(
      @RequestBody @Valid BatchReminderRequestDTO request) {
    BatchReminderResultDTO result = alertService.sendBatchReminders(request);
    return ResponseEntity.ok(result);
  }
}

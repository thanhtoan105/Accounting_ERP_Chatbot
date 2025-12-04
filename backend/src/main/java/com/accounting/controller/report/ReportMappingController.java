package com.accounting.controller.report;

import com.accounting.dto.report.MappingVersionDTO;
import com.accounting.dto.report.ReportMappingDTO;
import com.accounting.service.ReportMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing TT200 report mappings.
 * Provides endpoints for viewing, editing, and rolling back mappings.
 */
@RestController
@RequestMapping("/api/reports/mappings")
@Tag(name = "Report Mappings", description = "TT200 account-to-line mapping configuration")
public class ReportMappingController {

  private final ReportMappingService reportMappingService;

  public ReportMappingController(ReportMappingService reportMappingService) {
    this.reportMappingService = reportMappingService;
  }

  @GetMapping("/{reportType}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  @Operation(summary = "Get all current mappings for a report type",
      description = "Returns all active (current version) mappings ordered by display order")
  public ResponseEntity<List<ReportMappingDTO>> getMappings(@PathVariable String reportType) {
    List<ReportMappingDTO> mappings = reportMappingService.getMappings(reportType);
    return ResponseEntity.ok(mappings);
  }

  @GetMapping("/{reportType}/{lineCode}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  @Operation(summary = "Get a specific mapping by line code",
      description = "Returns the current version of a specific line mapping")
  public ResponseEntity<ReportMappingDTO> getMapping(
      @PathVariable String reportType,
      @PathVariable String lineCode) {

    ReportMappingDTO mapping = reportMappingService.getMapping(reportType, lineCode);
    return ResponseEntity.ok(mapping);
  }

  @PutMapping("/{reportType}/{lineCode}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Update a mapping",
      description = "Creates a new version of the mapping with updated values. Requires reason for audit.")
  public ResponseEntity<ReportMappingDTO> updateMapping(
      @PathVariable String reportType,
      @PathVariable String lineCode,
      @RequestParam String accountPattern,
      @RequestParam(required = false, defaultValue = "SUM") String operator,
      @RequestParam String reason) {

    ReportMappingDTO updated = reportMappingService.updateMapping(
        reportType, lineCode, accountPattern, operator, reason);
    return ResponseEntity.ok(updated);
  }

  @GetMapping("/{reportType}/{lineCode}/history")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  @Operation(summary = "Get version history for a mapping",
      description = "Returns all versions of a mapping, newest first")
  public ResponseEntity<List<MappingVersionDTO>> getMappingHistory(
      @PathVariable String reportType,
      @PathVariable String lineCode) {

    List<MappingVersionDTO> history = reportMappingService.getMappingHistory(reportType, lineCode);
    return ResponseEntity.ok(history);
  }

  @GetMapping("/{reportType}/audit-history")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Get audit history for all mappings of a report type",
      description = "Returns audit log entries from the audit_logs table")
  public ResponseEntity<List<MappingVersionDTO>> getAuditHistory(@PathVariable String reportType) {
    List<MappingVersionDTO> history = reportMappingService.getReportMappingAuditHistory(reportType);
    return ResponseEntity.ok(history);
  }

  @PostMapping("/{reportType}/{lineCode}/rollback")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Rollback a mapping to a previous version",
      description = "Creates a new version copying values from the specified historical version")
  public ResponseEntity<ReportMappingDTO> rollbackMapping(
      @PathVariable String reportType,
      @PathVariable String lineCode,
      @RequestParam Integer version) {

    ReportMappingDTO rolledBack = reportMappingService.rollbackMapping(reportType, lineCode, version);
    return ResponseEntity.ok(rolledBack);
  }

  @GetMapping("/{reportType}/affected-snapshots")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Count snapshots affected by a mapping version",
      description = "Returns the count of snapshots that used a specific mapping version")
  public ResponseEntity<Long> countAffectedSnapshots(
      @PathVariable String reportType,
      @RequestParam Integer mappingVersion) {

    long count = reportMappingService.countAffectedSnapshots(reportType, mappingVersion);
    return ResponseEntity.ok(count);
  }
}

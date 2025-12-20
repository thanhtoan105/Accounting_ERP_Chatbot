package com.accounting.service;

import java.util.List;

import com.accounting.dto.report.MappingVersionDTO;
import com.accounting.dto.report.ReportMappingDTO;

/**
 * Service for managing TT200 report mappings with versioning and audit trail.
 * Provides CRUD operations for mappings and rollback capability.
 */
public interface ReportMappingService {

  /**
   * Get all current mappings for a report type.
   *
   * @param reportType report type ('B01', 'B02', 'B03', 'F01')
   * @return list of current mappings
   */
  List<ReportMappingDTO> getMappings(String reportType);

  /**
   * Get a specific mapping by line code.
   *
   * @param reportType report type
   * @param lineCode TT200 line code
   * @return mapping DTO
   */
  ReportMappingDTO getMapping(String reportType, String lineCode);

  /**
   * Update a mapping. Creates a new version and logs to audit.
   *
   * @param reportType report type
   * @param lineCode TT200 line code
   * @param accountPattern new account pattern
   * @param operator new operator ('SUM', 'DIFF', 'ABS', 'CALC')
   * @param reason reason for the change (required for audit)
   * @return updated mapping DTO (new version)
   */
  ReportMappingDTO updateMapping(
      String reportType, String lineCode, String accountPattern, String operator, String reason);

  /**
   * Get version history for a mapping.
   *
   * @param reportType report type
   * @param lineCode TT200 line code
   * @return list of versions (newest first)
   */
  List<MappingVersionDTO> getMappingHistory(String reportType, String lineCode);

  /**
   * Get audit log history for all mappings of a report type.
   * Queries from audit_logs table with entity_type='REPORT_MAPPING'.
   *
   * @param reportType report type
   * @return list of version changes from audit log
   */
  List<MappingVersionDTO> getReportMappingAuditHistory(String reportType);

  /**
   * Rollback a mapping to a previous version.
   * Creates a new version copying values from the specified historical version.
   *
   * @param reportType report type
   * @param lineCode TT200 line code
   * @param versionId version number to rollback to
   * @return new mapping DTO created from rollback
   */
  ReportMappingDTO rollbackMapping(String reportType, String lineCode, Integer versionId);

  /**
   * Get affected snapshots that used a specific mapping version.
   * Useful for rollback impact analysis.
   *
   * @param reportType report type
   * @param mappingVersion mapping version number
   * @return count of snapshots using that version
   */
  long countAffectedSnapshots(String reportType, Integer mappingVersion);
}

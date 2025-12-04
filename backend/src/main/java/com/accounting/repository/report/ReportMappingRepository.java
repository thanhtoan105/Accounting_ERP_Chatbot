package com.accounting.repository.report;

import com.accounting.entity.report.ReportMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ReportMapping entity.
 * Provides queries for TT200 report line-to-account mappings with versioning support.
 */
@Repository
public interface ReportMappingRepository extends JpaRepository<ReportMapping, UUID> {

  /**
   * Find all current mappings for a company and report type.
   * Only returns the active version (is_current=true) for each line code.
   *
   * @param companyId company ID
   * @param reportType report type (B01, B02, B03, F01)
   * @return list of current mappings ordered by display_order
   */
  @Query("SELECT rm FROM ReportMapping rm " +
      "WHERE rm.companyId = :companyId " +
      "AND rm.reportType = :reportType " +
      "AND rm.isCurrent = true " +
      "ORDER BY rm.displayOrder")
  List<ReportMapping> findCurrentByCompanyAndReportType(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType);

  /**
   * Find a specific current mapping by line code.
   *
   * @param companyId company ID
   * @param reportType report type
   * @param lineCode TT200 line code
   * @return optional mapping if found
   */
  @Query("SELECT rm FROM ReportMapping rm " +
      "WHERE rm.companyId = :companyId " +
      "AND rm.reportType = :reportType " +
      "AND rm.lineCode = :lineCode " +
      "AND rm.isCurrent = true")
  Optional<ReportMapping> findCurrentByCompanyAndReportTypeAndLineCode(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      @Param("lineCode") String lineCode);

  /**
   * Find all versions of a specific line code mapping.
   * Ordered by version descending (newest first).
   *
   * @param companyId company ID
   * @param reportType report type
   * @param lineCode TT200 line code
   * @return list of all versions
   */
  @Query("SELECT rm FROM ReportMapping rm " +
      "WHERE rm.companyId = :companyId " +
      "AND rm.reportType = :reportType " +
      "AND rm.lineCode = :lineCode " +
      "ORDER BY rm.version DESC")
  List<ReportMapping> findVersionHistory(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      @Param("lineCode") String lineCode);

  /**
   * Find a specific version of a mapping.
   *
   * @param companyId company ID
   * @param reportType report type
   * @param lineCode TT200 line code
   * @param version version number
   * @return optional mapping if found
   */
  Optional<ReportMapping> findByCompanyIdAndReportTypeAndLineCodeAndVersion(
      Long companyId, String reportType, String lineCode, Integer version);

  /**
   * Get the maximum version number for a line code.
   *
   * @param companyId company ID
   * @param reportType report type
   * @param lineCode TT200 line code
   * @return max version or null if no mappings exist
   */
  @Query("SELECT MAX(rm.version) FROM ReportMapping rm " +
      "WHERE rm.companyId = :companyId " +
      "AND rm.reportType = :reportType " +
      "AND rm.lineCode = :lineCode")
  Integer findMaxVersion(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      @Param("lineCode") String lineCode);

  /**
   * Mark a mapping as not current (for versioning).
   *
   * @param mappingId mapping ID to mark as not current
   */
  @Modifying
  @Query("UPDATE ReportMapping rm SET rm.isCurrent = false WHERE rm.id = :mappingId")
  void markAsNotCurrent(@Param("mappingId") UUID mappingId);

  /**
   * Mark all mappings for a line code as not current.
   * Used when creating a new version.
   *
   * @param companyId company ID
   * @param reportType report type
   * @param lineCode TT200 line code
   */
  @Modifying
  @Query("UPDATE ReportMapping rm SET rm.isCurrent = false " +
      "WHERE rm.companyId = :companyId " +
      "AND rm.reportType = :reportType " +
      "AND rm.lineCode = :lineCode")
  void markAllVersionsAsNotCurrent(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      @Param("lineCode") String lineCode);

  /**
   * Find all non-calculated mappings for a report type.
   * These are the mappings that aggregate from GL accounts directly.
   *
   * @param companyId company ID
   * @param reportType report type
   * @return list of non-calculated current mappings
   */
  @Query("SELECT rm FROM ReportMapping rm " +
      "WHERE rm.companyId = :companyId " +
      "AND rm.reportType = :reportType " +
      "AND rm.isCurrent = true " +
      "AND rm.isCalculated = false " +
      "ORDER BY rm.displayOrder")
  List<ReportMapping> findNonCalculatedMappings(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType);

  /**
   * Find all calculated mappings for a report type.
   * These are mappings that compute values from other line codes.
   *
   * @param companyId company ID
   * @param reportType report type
   * @return list of calculated current mappings
   */
  @Query("SELECT rm FROM ReportMapping rm " +
      "WHERE rm.companyId = :companyId " +
      "AND rm.reportType = :reportType " +
      "AND rm.isCurrent = true " +
      "AND rm.isCalculated = true " +
      "ORDER BY rm.displayOrder")
  List<ReportMapping> findCalculatedMappings(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType);

  /**
   * Check if mappings exist for a company and report type.
   *
   * @param companyId company ID
   * @param reportType report type
   * @return true if mappings exist
   */
  boolean existsByCompanyIdAndReportTypeAndIsCurrent(
      Long companyId, String reportType, Boolean isCurrent);

  /**
   * Count current mappings for a report type.
   *
   * @param companyId company ID
   * @param reportType report type
   * @return count of current mappings
   */
  @Query("SELECT COUNT(rm) FROM ReportMapping rm " +
      "WHERE rm.companyId = :companyId " +
      "AND rm.reportType = :reportType " +
      "AND rm.isCurrent = true")
  long countCurrentMappings(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType);
}

package com.accounting.repository.report;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.report.ReportSnapshot;

/**
 * Repository for ReportSnapshot entity.
 * Provides queries for immutable report snapshots with legal hold support.
 */
@Repository
public interface ReportSnapshotRepository extends JpaRepository<ReportSnapshot, UUID> {

  /**
   * Find all active (non-deleted) snapshots for a company and report type.
   *
   * @param companyId company ID
   * @param reportType report type
   * @param pageable pagination settings
   * @return page of snapshots ordered by generation date descending
   */
  @Query("SELECT rs FROM ReportSnapshot rs " +
      "WHERE rs.companyId = :companyId " +
      "AND rs.reportType = :reportType " +
      "AND rs.deletedAt IS NULL " +
      "ORDER BY rs.generatedAt DESC")
  Page<ReportSnapshot> findActiveByCompanyAndReportType(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      Pageable pageable);

  /**
   * Find the latest snapshot for a specific period.
   *
   * @param companyId company ID
   * @param reportType report type
   * @param periodId period ID
   * @return optional latest snapshot
   */
  @Query("SELECT rs FROM ReportSnapshot rs " +
      "WHERE rs.companyId = :companyId " +
      "AND rs.reportType = :reportType " +
      "AND rs.periodId = :periodId " +
      "AND rs.deletedAt IS NULL " +
      "ORDER BY rs.generatedAt DESC " +
      "LIMIT 1")
  Optional<ReportSnapshot> findLatestByCompanyAndReportTypeAndPeriod(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      @Param("periodId") UUID periodId);

  /**
   * Find all snapshots for a period (for history view).
   *
   * @param companyId company ID
   * @param reportType report type
   * @param periodId period ID
   * @return list of snapshots ordered by generation date descending
   */
  @Query("SELECT rs FROM ReportSnapshot rs " +
      "WHERE rs.companyId = :companyId " +
      "AND rs.reportType = :reportType " +
      "AND rs.periodId = :periodId " +
      "AND rs.deletedAt IS NULL " +
      "ORDER BY rs.generatedAt DESC")
  List<ReportSnapshot> findAllByCompanyAndReportTypeAndPeriod(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      @Param("periodId") UUID periodId);

  /**
   * Find snapshot by data hash (for integrity verification).
   *
   * @param dataHash SHA-256 hash of snapshot data
   * @return optional snapshot if hash matches
   */
  Optional<ReportSnapshot> findByDataHash(String dataHash);

  /**
   * Find all snapshots under legal hold for a company.
   *
   * @param companyId company ID
   * @return list of snapshots with legal hold
   */
  @Query("SELECT rs FROM ReportSnapshot rs " +
      "WHERE rs.companyId = :companyId " +
      "AND rs.legalHold = true " +
      "AND rs.deletedAt IS NULL " +
      "ORDER BY rs.legalHoldAt DESC")
  List<ReportSnapshot> findLegalHoldSnapshots(@Param("companyId") Long companyId);

  /**
   * Find all draft snapshots for a company.
   *
   * @param companyId company ID
   * @return list of draft snapshots
   */
  @Query("SELECT rs FROM ReportSnapshot rs " +
      "WHERE rs.companyId = :companyId " +
      "AND rs.isDraft = true " +
      "AND rs.deletedAt IS NULL " +
      "ORDER BY rs.generatedAt DESC")
  List<ReportSnapshot> findDraftSnapshots(@Param("companyId") Long companyId);

  /**
   * Find snapshots that used a specific mapping version.
   * Useful for rollback impact analysis.
   *
   * @param companyId company ID
   * @param reportType report type
   * @param mappingVersion mapping version number
   * @return list of snapshots using that version
   */
  @Query("SELECT rs FROM ReportSnapshot rs " +
      "WHERE rs.companyId = :companyId " +
      "AND rs.reportType = :reportType " +
      "AND rs.mappingVersion = :mappingVersion " +
      "AND rs.deletedAt IS NULL")
  List<ReportSnapshot> findByMappingVersion(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      @Param("mappingVersion") Integer mappingVersion);

  /**
   * Count snapshots for a period.
   *
   * @param companyId company ID
   * @param reportType report type
   * @param periodId period ID
   * @return count of active snapshots
   */
  @Query("SELECT COUNT(rs) FROM ReportSnapshot rs " +
      "WHERE rs.companyId = :companyId " +
      "AND rs.reportType = :reportType " +
      "AND rs.periodId = :periodId " +
      "AND rs.deletedAt IS NULL")
  long countByCompanyAndReportTypeAndPeriod(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      @Param("periodId") UUID periodId);

  /**
   * Check if a final snapshot exists for a period.
   *
   * @param companyId company ID
   * @param reportType report type
   * @param periodId period ID
   * @return true if a final snapshot exists
   */
  @Query("SELECT CASE WHEN COUNT(rs) > 0 THEN true ELSE false END " +
      "FROM ReportSnapshot rs " +
      "WHERE rs.companyId = :companyId " +
      "AND rs.reportType = :reportType " +
      "AND rs.periodId = :periodId " +
      "AND rs.isFinal = true " +
      "AND rs.deletedAt IS NULL")
  boolean existsFinalSnapshot(
      @Param("companyId") Long companyId,
      @Param("reportType") String reportType,
      @Param("periodId") UUID periodId);
}

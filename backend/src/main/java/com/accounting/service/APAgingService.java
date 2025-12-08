package com.accounting.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.APAgingBucketDTO;
import com.accounting.dto.APAgingReportDTO;
import com.accounting.dto.AgingBillDetailsDTO;
import com.accounting.dto.OverdueCountDTO;
import com.accounting.dto.OverdueSupplierDTO;

/**
 * Service interface for AP aging report operations.
 * Handles aging bucket calculation, overdue detection, and drill-down functionality.
 */
public interface APAgingService {

  /**
   * Calculate aging buckets for a specific supplier.
   *
   * @param supplierId supplier ID (optional, null for all suppliers)
   * @param asOfDate as-of date for aging calculation
   * @param periodId period ID (optional)
   * @return aging bucket DTO with amounts per bucket
   */
  APAgingBucketDTO calculateAgingBuckets(Long supplierId, LocalDate asOfDate, Long periodId);

  /**
   * Get paginated aging report with filters.
   *
   * @param supplierId filter by supplier ID (optional)
   * @param periodId filter by period ID (optional)
   * @param asOfDate as-of date for aging calculation
   * @param status filter by bill status (optional)
   * @param bucket filter by aging bucket (optional: CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90)
   * @param pageable pagination and sorting parameters
   * @return paginated aging report
   */
  Page<APAgingReportDTO> getAgingReport(
      Long supplierId,
      Long periodId,
      LocalDate asOfDate,
      String status,
      String bucket,
      Pageable pageable);

  /**
   * Get top overdue suppliers for dashboard badge.
   *
   * @param periodId period ID (optional)
   * @param asOfDate as-of date for aging calculation
   * @param limit maximum number of suppliers to return
   * @return list of overdue suppliers
   */
  List<OverdueSupplierDTO> getOverdueSuppliers(Long periodId, LocalDate asOfDate, Integer limit);

  /**
   * Get count of overdue payables for dashboard badge.
   *
   * @param periodId period ID (optional)
   * @param asOfDate as-of date for aging calculation
   * @return overdue count DTO
   */
  OverdueCountDTO getOverdueCount(Long periodId, LocalDate asOfDate);

  /**
   * Get bill/payment history for drill-down.
   *
   * @param supplierId supplier ID
   * @param bucket aging bucket (CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90)
   * @param periodId period ID (optional)
   * @param asOfDate as-of date for aging calculation
   * @param status filter by bill status (optional)
   * @param pageable pagination parameters
   * @return paginated bill details with payment history
   */
  Page<AgingBillDetailsDTO> getAgingBillDetails(
      Long supplierId,
      String bucket,
      Long periodId,
      LocalDate asOfDate,
      String status,
      Pageable pageable);

  /**
   * Export aging report to Excel or PDF.
   *
   * @param format export format (EXCEL or PDF)
   * @param supplierId filter by supplier ID (optional)
   * @param periodId filter by period ID (optional)
   * @param asOfDate as-of date for aging calculation
   * @param status filter by bill status (optional)
   * @param bucket filter by aging bucket (optional)
   * @return byte array of exported file
   */
  byte[] exportAgingReport(
      String format,
      Long supplierId,
      Long periodId,
      LocalDate asOfDate,
      String status,
      String bucket);

  /**
   * Invalidate cached AP aging calculations.
   */
  void invalidateAgingCache();
}

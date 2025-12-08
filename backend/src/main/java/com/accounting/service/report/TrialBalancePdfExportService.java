package com.accounting.service.report;

import java.util.UUID;

import com.accounting.dto.TrialBalanceResponseDTO;

/**
 * Service interface for exporting Trial Balance (S06-DN) to PDF format.
 * Follows TT200 layout requirements with Vietnamese headers and professional formatting.
 */
public interface TrialBalancePdfExportService {

  /**
   * Export Trial Balance report to PDF format with TT200 layout.
   *
   * @param report the trial balance data to export
   * @param snapshotId optional snapshot ID for reproducibility tracking
   * @return PDF file as byte array
   * @throws IllegalStateException if PDF generation fails
   */
  byte[] exportToPdf(TrialBalanceResponseDTO report, UUID snapshotId);

  /**
   * Export Trial Balance report to PDF with DRAFT watermark support.
   *
   * @param report the trial balance data to export
   * @param snapshotId optional snapshot ID for reproducibility tracking
   * @param isDraft true if period is open (adds DRAFT watermark)
   * @return PDF file as byte array
   * @throws IllegalStateException if PDF generation fails
   */
  byte[] exportToPdf(TrialBalanceResponseDTO report, UUID snapshotId, boolean isDraft);
}

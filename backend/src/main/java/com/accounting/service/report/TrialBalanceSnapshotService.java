package com.accounting.service.report;

import com.accounting.dto.TrialBalanceResponseDTO;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing Trial Balance report snapshots.
 * Handles snapshot creation, hash calculation, and reproducibility verification.
 */
public interface TrialBalanceSnapshotService {

  /**
   * Result of PDF export with snapshot metadata.
   */
  record PdfExportResult(
      byte[] pdfBytes,
      UUID snapshotId,
      String dataHash
  ) {}

  /**
   * Export Trial Balance to PDF and save a snapshot.
   *
   * @param report the trial balance data
   * @param periodId the period ID
   * @param isDraft true if period is open
   * @return export result with PDF bytes, snapshot ID, and hash
   */
  PdfExportResult exportToPdfWithSnapshot(TrialBalanceResponseDTO report, UUID periodId, boolean isDraft);

  /**
   * Re-export using an existing snapshot for reproducibility.
   * Verifies hash matches original data.
   *
   * @param snapshotId existing snapshot ID
   * @return export result, or empty if snapshot not found or hash mismatch
   */
  Optional<PdfExportResult> reexportFromSnapshot(UUID snapshotId);

  /**
   * Calculate SHA-256 hash of trial balance data.
   *
   * @param report the trial balance data
   * @return hex-encoded SHA-256 hash
   */
  String calculateDataHash(TrialBalanceResponseDTO report);

  /**
   * Verify if current data matches a snapshot hash.
   *
   * @param snapshotId snapshot to verify against
   * @param currentReport current trial balance data
   * @return true if hashes match
   */
  boolean verifySnapshotHash(UUID snapshotId, TrialBalanceResponseDTO currentReport);
}

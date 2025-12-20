package com.accounting.service.impl.report;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.TrialBalanceResponseDTO;
import com.accounting.entity.report.ReportSnapshot;
import com.accounting.repository.report.ReportSnapshotRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.report.TrialBalancePdfExportService;
import com.accounting.service.report.TrialBalanceSnapshotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementation of Trial Balance snapshot service.
 * Handles PDF export with snapshot storage and hash verification for AC7.1-10.
 */
@Service
public class TrialBalanceSnapshotServiceImpl implements TrialBalanceSnapshotService {

  private static final Logger logger = LoggerFactory.getLogger(TrialBalanceSnapshotServiceImpl.class);
  private static final String REPORT_TYPE = "S06-DN";
  private static final int MAPPING_VERSION = 1;

  private final ReportSnapshotRepository snapshotRepository;
  private final TrialBalancePdfExportService pdfExportService;
  private final ObjectMapper objectMapper;

  @Value("${app.version:1.0.0}")
  private String applicationVersion;

  @Autowired
  public TrialBalanceSnapshotServiceImpl(
      ReportSnapshotRepository snapshotRepository,
      TrialBalancePdfExportService pdfExportService,
      ObjectMapper objectMapper) {
    this.snapshotRepository = snapshotRepository;
    this.pdfExportService = pdfExportService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public PdfExportResult exportToPdfWithSnapshot(TrialBalanceResponseDTO report, UUID periodId, boolean isDraft) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = getCurrentUserId();

    // Calculate data hash for reproducibility
    String dataHash = calculateDataHash(report);

    // Generate PDF
    byte[] pdfBytes = pdfExportService.exportToPdf(report, null, isDraft);

    // Create and save snapshot
    ReportSnapshot snapshot = new ReportSnapshot();
    snapshot.setCompanyId(companyId);
    snapshot.setReportType(REPORT_TYPE);
    snapshot.setPeriodId(periodId);
    snapshot.setMappingVersion(MAPPING_VERSION);
    snapshot.setDataHash(dataHash);
    snapshot.setIsDraft(isDraft);
    snapshot.setGeneratedBy(userId);

    // Store parameters as JSON
    try {
      JsonNode parameters = objectMapper.createObjectNode()
          .put("periodId", periodId.toString())
          .put("isDraft", isDraft)
          .put("applicationVersion", applicationVersion);
      snapshot.setParameters(parameters);

      // Store snapshot data (just totals for verification, not full data to save space)
      JsonNode snapshotData = objectMapper.valueToTree(createSnapshotSummary(report));
      snapshot.setSnapshotData(snapshotData);
    } catch (Exception e) {
      logger.warn("Failed to serialize snapshot data: {}", e.getMessage());
      // Use empty JSON if serialization fails
      snapshot.setParameters(objectMapper.createObjectNode());
      snapshot.setSnapshotData(objectMapper.createObjectNode());
    }

    ReportSnapshot savedSnapshot = snapshotRepository.save(snapshot);
    logger.info("Saved Trial Balance snapshot {} for company {} period {} with hash {}",
        savedSnapshot.getId(), companyId, periodId, dataHash);

    return new PdfExportResult(pdfBytes, savedSnapshot.getId(), dataHash);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PdfExportResult> reexportFromSnapshot(UUID snapshotId) {
    Long companyId = CompanyContext.getCompanyId();

    Optional<ReportSnapshot> snapshotOpt = snapshotRepository.findById(snapshotId);
    if (snapshotOpt.isEmpty()) {
      logger.warn("Snapshot {} not found", snapshotId);
      return Optional.empty();
    }

    ReportSnapshot snapshot = snapshotOpt.get();

    // Verify company access
    if (!snapshot.getCompanyId().equals(companyId)) {
      logger.warn("Access denied to snapshot {} for company {}", snapshotId, companyId);
      return Optional.empty();
    }

    // Regenerate PDF from snapshot data for reproducibility (AC7.1-10)
    logger.info("Re-export requested for snapshot {} with hash {}", snapshotId, snapshot.getDataHash());

    try {
      // Extract parameters from snapshot
      boolean isDraft = Boolean.TRUE.equals(snapshot.getIsDraft());

      // Regenerate PDF using the same parameters
      // Note: For true reproducibility with identical output, we would need to
      // store the full report data in snapshot. For now, we regenerate fresh.
      byte[] pdfBytes = pdfExportService.exportToPdf(null, snapshotId, isDraft);

      return Optional.of(new PdfExportResult(
          pdfBytes,
          snapshot.getId(),
          snapshot.getDataHash()
      ));
    } catch (Exception e) {
      logger.error("Failed to regenerate PDF from snapshot {}: {}", snapshotId, e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public String calculateDataHash(TrialBalanceResponseDTO report) {
    try {
      // Create a canonical representation of the data for hashing
      // Include only the data that affects the report output
      StringBuilder hashInput = new StringBuilder();
      hashInput.append("period:").append(report.getPeriod() != null ? report.getPeriod().getId() : "null").append("|");
      hashInput.append("company:").append(report.getCompanyName()).append("|");
      hashInput.append("totalOpeningDebit:").append(report.getTotalOpeningDebit()).append("|");
      hashInput.append("totalOpeningCredit:").append(report.getTotalOpeningCredit()).append("|");
      hashInput.append("totalPeriodDebit:").append(report.getTotalPeriodDebit()).append("|");
      hashInput.append("totalPeriodCredit:").append(report.getTotalPeriodCredit()).append("|");
      hashInput.append("totalClosingDebit:").append(report.getTotalClosingDebit()).append("|");
      hashInput.append("totalClosingCredit:").append(report.getTotalClosingCredit()).append("|");
      hashInput.append("accountCount:").append(report.getAccounts() != null ? report.getAccounts().size() : 0).append("|");

      // Include account-level data in hash
      if (report.getAccounts() != null) {
        for (var account : report.getAccounts()) {
          hashInput.append("acc:")
              .append(account.getAccountCode()).append(":")
              .append(account.getOpeningDebit()).append(":")
              .append(account.getOpeningCredit()).append(":")
              .append(account.getPeriodDebit()).append(":")
              .append(account.getPeriodCredit()).append(":")
              .append(account.getClosingDebit()).append(":")
              .append(account.getClosingCredit()).append("|");
        }
      }

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(hashInput.toString().getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean verifySnapshotHash(UUID snapshotId, TrialBalanceResponseDTO currentReport) {
    Optional<ReportSnapshot> snapshotOpt = snapshotRepository.findById(snapshotId);
    if (snapshotOpt.isEmpty()) {
      return false;
    }

    String currentHash = calculateDataHash(currentReport);
    String storedHash = snapshotOpt.get().getDataHash();
    boolean matches = storedHash.equals(currentHash);

    if (!matches) {
      logger.warn("Hash mismatch for snapshot {}: stored={}, current={}",
          snapshotId, storedHash, currentHash);
    }

    return matches;
  }

  /**
   * Create a summary object for snapshot storage.
   */
  private SnapshotSummary createSnapshotSummary(TrialBalanceResponseDTO report) {
    return new SnapshotSummary(
        report.getTotalOpeningDebit(),
        report.getTotalOpeningCredit(),
        report.getTotalPeriodDebit(),
        report.getTotalPeriodCredit(),
        report.getTotalClosingDebit(),
        report.getTotalClosingCredit(),
        report.getAccounts() != null ? report.getAccounts().size() : 0,
        report.isBalanced()
    );
  }

  private Long getCurrentUserId() {
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getPrincipal() != null) {
        return Long.parseLong(auth.getPrincipal().toString());
      }
    } catch (Exception e) {
      logger.debug("Could not get current user ID: {}", e.getMessage());
    }
    return null;
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder();
    for (byte b : bytes) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  /**
   * Summary data stored in snapshot for verification.
   */
  private record SnapshotSummary(
      java.math.BigDecimal totalOpeningDebit,
      java.math.BigDecimal totalOpeningCredit,
      java.math.BigDecimal totalPeriodDebit,
      java.math.BigDecimal totalPeriodCredit,
      java.math.BigDecimal totalClosingDebit,
      java.math.BigDecimal totalClosingCredit,
      int accountCount,
      boolean balanced
  ) {}
}

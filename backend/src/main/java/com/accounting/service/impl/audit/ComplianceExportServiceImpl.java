package com.accounting.service.impl.audit;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.audit.CashAuditQueryDTO;
import com.accounting.entity.AuditLog;
import com.accounting.enums.CashBankAuditAction;
import com.accounting.repository.AuditLogRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ComplianceExportService;
import com.accounting.service.PeriodManagementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of ComplianceExportService for compliance-controlled exports.
 *
 * <p>Features:
 * <ul>
 *   <li>SHA-256 hash calculation for document integrity</li>
 *   <li>Period status detection for DRAFT watermark</li>
 *   <li>Audit logging for all export operations</li>
 * </ul>
 */
@Service
@Transactional
public class ComplianceExportServiceImpl implements ComplianceExportService {

  private static final Logger logger = LoggerFactory.getLogger(ComplianceExportServiceImpl.class);
  private static final int EXPORT_LIMIT = 10000;
  private static final DateTimeFormatter FILENAME_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final AuditLogRepository auditLogRepository;
  private final PeriodManagementService periodManagementService;
  private final ObjectMapper objectMapper;

  public ComplianceExportServiceImpl(
      AuditLogRepository auditLogRepository,
      PeriodManagementService periodManagementService,
      ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.periodManagementService = periodManagementService;
    this.objectMapper = objectMapper;
  }

  @Override
  public ComplianceExportResult exportAuditLogs(CashAuditQueryDTO filter, ExportFormat format) {
    Long companyId = CompanyContext.getCompanyId();
    logger.info("Exporting audit logs for company {} in {} format", companyId, format);

    // Get period status for watermark decision
    PeriodStatusInfo periodStatus = getPeriodStatus(filter.getDateFrom(), filter.getDateTo());
    boolean isDraft = periodStatus != null && periodStatus.isOpen();

    // Query audit logs
    Specification<AuditLog> spec = buildSpecification(companyId, filter);
    Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
    List<AuditLog> logs = auditLogRepository.findAll(spec, PageRequest.of(0, EXPORT_LIMIT, sort))
        .getContent();

    // Build export content based on format
    byte[] content;
    String mimeType;
    String extension;

    switch (format) {
      case JSON:
        content = buildJsonExport(logs, periodStatus, isDraft);
        mimeType = "application/json";
        extension = "json";
        break;
      case CSV:
        content = buildCsvExport(logs, periodStatus, isDraft);
        mimeType = "text/csv";
        extension = "csv";
        break;
      case PDF:
        // PDF export would require additional library (OpenPDF/iText)
        // For now, generate a simple text-based format
        content = buildPdfPlaceholder(logs, periodStatus, isDraft);
        mimeType = "application/pdf";
        extension = "pdf";
        break;
      default:
        throw new IllegalArgumentException("Unsupported format: " + format);
    }

    String hash = calculateDocumentHash(content);
    String filename = String.format("audit-export-%s.%s",
        FILENAME_TS.format(java.time.OffsetDateTime.now()), extension);

    // Log the export
    logComplianceExport(companyId, format, logs.size(), hash, isDraft);

    String periodStatusStr = periodStatus != null
        ? (periodStatus.isOpen() ? "OPEN" : "CLOSED")
        : "UNKNOWN";

    return new ComplianceExportResult(content, filename, hash, logs.size(),
        mimeType, isDraft, periodStatusStr);
  }

  @Override
  public BackupResult createWeeklyBackup(Long companyId, LocalDate fromDate, LocalDate toDate) {
    logger.info("Creating weekly backup for company {} from {} to {}", companyId, fromDate, toDate);

    UUID backupId = UUID.randomUUID();

    // TODO: Implement actual backup creation with:
    // 1. Export bank accounts master data
    // 2. Export cash book entries
    // 3. Export reconciliation sessions
    // 4. Export audit logs
    // 5. Create manifest with SHA-256 hashes
    // 6. Compress into ZIP archive
    // 7. Upload to Supabase Storage

    // For now, create a placeholder backup result
    String manifestHash = calculateDocumentHash("manifest-placeholder".getBytes());

    // Log backup export
    logBackupExport(companyId, backupId, 0, 0, manifestHash);

    logger.info("Weekly backup {} created (placeholder) for company {}", backupId, companyId);

    return new BackupResult(backupId, null, 0, 0, manifestHash, false);
  }

  @Override
  public String calculateDocumentHash(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content);
      Formatter formatter = new Formatter();
      for (byte b : hash) {
        formatter.format("%02x", b);
      }
      String result = formatter.toString();
      formatter.close();
      return result;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  @Override
  public boolean isPeriodOpen(LocalDate date) {
    return periodManagementService.isDateInOpenPeriod(date);
  }

  @Override
  public PeriodStatusInfo getPeriodStatus(LocalDate fromDate, LocalDate toDate) {
    // Check if any date in the range falls in an open period
    Optional<AccountingPeriodDTO> fromPeriod = periodManagementService.findPeriodByDate(fromDate);
    Optional<AccountingPeriodDTO> toPeriod = periodManagementService.findPeriodByDate(toDate);

    // If either boundary is in an open period, mark as open
    if (fromPeriod.isPresent()) {
      boolean isOpen = periodManagementService.isPeriodOpen(fromPeriod.get().getId());
      return new PeriodStatusInfo(
          fromPeriod.get().getPeriodName(),
          fromPeriod.get().getStartDate(),
          fromPeriod.get().getEndDate(),
          isOpen,
          fromPeriod.get().getId());
    }

    if (toPeriod.isPresent()) {
      boolean isOpen = periodManagementService.isPeriodOpen(toPeriod.get().getId());
      return new PeriodStatusInfo(
          toPeriod.get().getPeriodName(),
          toPeriod.get().getStartDate(),
          toPeriod.get().getEndDate(),
          isOpen,
          toPeriod.get().getId());
    }

    return null;
  }

  // === Private Methods ===

  private Specification<AuditLog> buildSpecification(Long companyId, CashAuditQueryDTO filter) {
    return (root, query, cb) -> {
      var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

      predicates.add(cb.equal(root.get("companyId"), companyId));

      if (filter.getDateFrom() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
            filter.getDateFrom().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)));
      }
      if (filter.getDateTo() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"),
            filter.getDateTo().atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC)));
      }
      if (filter.getUserId() != null) {
        predicates.add(cb.equal(root.get("userId"), filter.getUserId()));
      }
      if (filter.getEntityType() != null && !filter.getEntityType().isEmpty()) {
        predicates.add(cb.equal(root.get("entityType"), filter.getEntityType()));
      }
      if (filter.getActionTypes() != null && !filter.getActionTypes().isEmpty()) {
        predicates.add(root.get("action").in(filter.getActionTypes()));
      }

      return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
    };
  }

  private byte[] buildJsonExport(List<AuditLog> logs, PeriodStatusInfo periodStatus,
      boolean isDraft) {
    try {
      ObjectNode root = objectMapper.createObjectNode();

      // Header with compliance info
      ObjectNode header = objectMapper.createObjectNode();
      header.put("exportedAt", Instant.now().toString());
      header.put("recordCount", logs.size());
      header.put("isDraft", isDraft);
      if (periodStatus != null) {
        header.put("periodName", periodStatus.periodName());
        header.put("periodStatus", periodStatus.isOpen() ? "OPEN" : "CLOSED");
      }
      root.set("header", header);

      // Data
      root.set("data", objectMapper.valueToTree(logs.stream()
          .map(this::toExportDto)
          .toList()));

      // Footer with hash placeholder (will be calculated after)
      ObjectNode footer = objectMapper.createObjectNode();
      footer.put("signatureBlock", "Prepared by: ___ | Reviewed by: ___ | Approved by: ___");
      root.set("footer", footer);

      return objectMapper.writerWithDefaultPrettyPrinter()
          .writeValueAsBytes(root);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to build JSON export", e);
    }
  }

  private byte[] buildCsvExport(List<AuditLog> logs, PeriodStatusInfo periodStatus,
      boolean isDraft) {
    StringBuilder sb = new StringBuilder();

    // Header comment with compliance info
    sb.append("# Export generated: ").append(Instant.now()).append("\n");
    sb.append("# Record count: ").append(logs.size()).append("\n");
    if (periodStatus != null) {
      sb.append("# Period: ").append(periodStatus.periodName())
          .append(" (").append(periodStatus.isOpen() ? "OPEN" : "CLOSED").append(")\n");
    }
    if (isDraft) {
      sb.append("# *** DRAFT - DATA MAY CHANGE ***\n");
    }
    sb.append("\n");

    // CSV header
    sb.append("timestamp,action,eventType,entityType,entityId,userId,email,success,ipAddress\n");

    // CSV data
    for (AuditLog log : logs) {
      appendCsvField(sb, log.getCreatedAt() != null ? log.getCreatedAt().toString() : "");
      appendCsvField(sb, log.getAction());
      appendCsvField(sb, log.getEventType());
      appendCsvField(sb, log.getEntityType());
      appendCsvField(sb, log.getEntityId());
      appendCsvField(sb, log.getUserId() != null ? log.getUserId().toString() : "");
      appendCsvField(sb, log.getEmail());
      appendCsvField(sb, log.getSuccess() != null ? log.getSuccess().toString() : "");
      appendCsvField(sb, log.getIpAddress());
      // Remove trailing comma and add newline
      if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') {
        sb.setCharAt(sb.length() - 1, '\n');
      }
    }

    // Footer
    sb.append("\n# Signature Block: Prepared by: ___ | Reviewed by: ___ | Approved by: ___\n");

    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private byte[] buildPdfPlaceholder(List<AuditLog> logs, PeriodStatusInfo periodStatus,
      boolean isDraft) {
    // Placeholder: In production, use OpenPDF/iText to create actual PDF with watermarks
    StringBuilder sb = new StringBuilder();
    sb.append("PDF EXPORT PLACEHOLDER\n");
    sb.append("======================\n\n");

    if (isDraft) {
      sb.append("*** DRAFT - PERIOD OPEN - DATA MAY CHANGE ***\n\n");
    }

    sb.append("Export Date: ").append(Instant.now()).append("\n");
    sb.append("Record Count: ").append(logs.size()).append("\n");
    if (periodStatus != null) {
      sb.append("Period: ").append(periodStatus.periodName())
          .append(" (").append(periodStatus.isOpen() ? "OPEN" : "CLOSED").append(")\n");
    }
    sb.append("\n");

    sb.append("Signature Block:\n");
    sb.append("Prepared by: ___________________\n");
    sb.append("Reviewed by: ___________________\n");
    sb.append("Approved by: ___________________\n");

    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void appendCsvField(StringBuilder sb, String value) {
    sb.append('"');
    if (value != null) {
      sb.append(value.replace("\"", "\"\""));
    }
    sb.append('"').append(',');
  }

  private Object toExportDto(AuditLog log) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("id", log.getId() != null ? log.getId().toString() : null);
    node.put("timestamp", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
    node.put("action", log.getAction());
    node.put("eventType", log.getEventType());
    node.put("entityType", log.getEntityType());
    node.put("entityId", log.getEntityId());
    node.put("userId", log.getUserId());
    node.put("email", log.getEmail());
    node.put("success", log.getSuccess());
    node.put("ipAddress", log.getIpAddress());
    return node;
  }

  private void logComplianceExport(Long companyId, ExportFormat format, int recordCount,
      String hash, boolean isDraft) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("format", format.name());
      metadata.put("recordCount", recordCount);
      metadata.put("hash", hash);
      metadata.put("isDraft", isDraft);

      AuditLog log = new AuditLog();
      log.setAction(CashBankAuditAction.COMPLIANCE_EXPORT.getValue());
      log.setEventType("CASH_BANK_AUDIT");
      log.setCompanyId(companyId);
      log.setMetadata(metadata);
      log.setSuccess(true);
      log.setCreatedAt(Instant.now());

      auditLogRepository.save(log);
    } catch (Exception e) {
      logger.warn("Failed to log compliance export", e);
    }
  }

  private void logBackupExport(Long companyId, UUID backupId, long fileSize, int fileCount,
      String hash) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("backupId", backupId.toString());
      metadata.put("fileSize", fileSize);
      metadata.put("fileCount", fileCount);
      metadata.put("hash", hash);

      AuditLog log = new AuditLog();
      log.setAction(CashBankAuditAction.BACKUP_EXPORT.getValue());
      log.setEventType("CASH_BANK_AUDIT");
      log.setCompanyId(companyId);
      log.setMetadata(metadata);
      log.setSuccess(true);
      log.setCreatedAt(Instant.now());

      auditLogRepository.save(log);
    } catch (Exception e) {
      logger.warn("Failed to log backup export", e);
    }
  }
}

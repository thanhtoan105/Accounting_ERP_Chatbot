package com.accounting.service.impl.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.report.MappingVersionDTO;
import com.accounting.dto.report.ReportMappingDTO;
import com.accounting.entity.AuditLog;
import com.accounting.entity.User;
import com.accounting.entity.report.ReportMapping;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.report.ReportMappingRepository;
import com.accounting.repository.report.ReportSnapshotRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ReportMappingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Implementation of ReportMappingService for managing TT200 mappings.
 * Uses existing audit_logs table for audit trail (entity_type='REPORT_MAPPING').
 */
@Service
@Transactional
public class ReportMappingServiceImpl implements ReportMappingService {

  private static final Logger logger = LoggerFactory.getLogger(ReportMappingServiceImpl.class);
  private static final String ENTITY_TYPE_REPORT_MAPPING = "REPORT_MAPPING";

  private final ReportMappingRepository reportMappingRepository;
  private final ReportSnapshotRepository reportSnapshotRepository;
  private final AuditLogRepository auditLogRepository;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  public ReportMappingServiceImpl(
      ReportMappingRepository reportMappingRepository,
      ReportSnapshotRepository reportSnapshotRepository,
      AuditLogRepository auditLogRepository,
      UserRepository userRepository,
      ObjectMapper objectMapper) {
    this.reportMappingRepository = reportMappingRepository;
    this.reportSnapshotRepository = reportSnapshotRepository;
    this.auditLogRepository = auditLogRepository;
    this.userRepository = userRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ReportMappingDTO> getMappings(String reportType) {
    Long companyId = getCompanyId();

    List<ReportMapping> mappings = reportMappingRepository
        .findCurrentByCompanyAndReportType(companyId, reportType);

    return mappings.stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public ReportMappingDTO getMapping(String reportType, String lineCode) {
    Long companyId = getCompanyId();

    ReportMapping mapping = reportMappingRepository
        .findCurrentByCompanyAndReportTypeAndLineCode(companyId, reportType, lineCode)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            String.format("Mapping not found: %s:%s", reportType, lineCode)));

    return toDTO(mapping);
  }

  @Override
  public ReportMappingDTO updateMapping(
      String reportType, String lineCode, String accountPattern, String operator, String reason) {

    Long companyId = getCompanyId();
    Long userId = getUserId();

    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Change reason is required for audit trail");
    }

    // Find current mapping
    ReportMapping currentMapping = reportMappingRepository
        .findCurrentByCompanyAndReportTypeAndLineCode(companyId, reportType, lineCode)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            String.format("Mapping not found: %s:%s", reportType, lineCode)));

    // Store old values for audit diff
    String oldAccountPattern = currentMapping.getAccountPattern();
    String oldOperator = currentMapping.getOperator();

    // Mark current as not current
    reportMappingRepository.markAllVersionsAsNotCurrent(companyId, reportType, lineCode);

    // Create new version
    ReportMapping newMapping = currentMapping.createNewVersion();
    newMapping.setAccountPattern(accountPattern);
    newMapping.setOperator(operator);
    newMapping.setChangeReason(reason);
    newMapping.setCreatedBy(userId);

    newMapping = reportMappingRepository.save(newMapping);

    // Log to audit_logs
    logMappingChange(companyId, userId, reportType, lineCode, "UPDATE", reason,
        oldAccountPattern, accountPattern, oldOperator, operator,
        currentMapping.getVersion(), newMapping.getVersion());

    logger.info("Updated mapping {}:{} to version {} by user {}",
        reportType, lineCode, newMapping.getVersion(), userId);

    return toDTO(newMapping);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MappingVersionDTO> getMappingHistory(String reportType, String lineCode) {
    Long companyId = getCompanyId();

    List<ReportMapping> versions = reportMappingRepository
        .findVersionHistory(companyId, reportType, lineCode);

    List<MappingVersionDTO> history = new ArrayList<>();
    ReportMapping previousVersion = null;

    for (ReportMapping mapping : versions) {
      MappingVersionDTO dto = toVersionDTO(mapping);

      // Calculate diff from previous version
      if (previousVersion != null) {
        dto.setDiff(calculateDiff(previousVersion, mapping));
      }

      history.add(dto);
      previousVersion = mapping;
    }

    return history;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MappingVersionDTO> getReportMappingAuditHistory(String reportType) {
    Long companyId = getCompanyId();

    // Query audit_logs for REPORT_MAPPING entries
    List<AuditLog> auditLogs = auditLogRepository
        .findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc(
            ENTITY_TYPE_REPORT_MAPPING,
            reportType + ":%", // Pattern match for report type
            companyId);

    // For MVP, return simplified history
    // Full implementation would parse the changes JSON
    return auditLogs.stream()
        .filter(log -> log.getEntityId() != null && log.getEntityId().startsWith(reportType + ":"))
        .map(this::auditLogToVersionDTO)
        .collect(Collectors.toList());
  }

  @Override
  public ReportMappingDTO rollbackMapping(String reportType, String lineCode, Integer versionId) {
    Long companyId = getCompanyId();
    Long userId = getUserId();

    // Find the historical version to rollback to
    ReportMapping historicalMapping = reportMappingRepository
        .findByCompanyIdAndReportTypeAndLineCodeAndVersion(companyId, reportType, lineCode, versionId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            String.format("Version %d not found for %s:%s", versionId, reportType, lineCode)));

    // Find current version for audit
    ReportMapping currentMapping = reportMappingRepository
        .findCurrentByCompanyAndReportTypeAndLineCode(companyId, reportType, lineCode)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            String.format("Current mapping not found: %s:%s", reportType, lineCode)));

    String oldAccountPattern = currentMapping.getAccountPattern();
    String oldOperator = currentMapping.getOperator();

    // Mark all versions as not current
    reportMappingRepository.markAllVersionsAsNotCurrent(companyId, reportType, lineCode);

    // Create new version copying from historical
    ReportMapping newMapping = new ReportMapping();
    newMapping.setCompanyId(companyId);
    newMapping.setReportType(reportType);
    newMapping.setLineCode(lineCode);
    newMapping.setLineName(historicalMapping.getLineName());
    newMapping.setLineNameEnglish(historicalMapping.getLineNameEnglish());
    newMapping.setAccountPattern(historicalMapping.getAccountPattern());
    newMapping.setOperator(historicalMapping.getOperator());
    newMapping.setSignModifier(historicalMapping.getSignModifier());
    newMapping.setDisplayOrder(historicalMapping.getDisplayOrder());
    newMapping.setParentLineCode(historicalMapping.getParentLineCode());
    newMapping.setLevel(historicalMapping.getLevel());
    newMapping.setIsCalculated(historicalMapping.getIsCalculated());
    newMapping.setFormula(historicalMapping.getFormula());
    newMapping.setVersion(currentMapping.getVersion() + 1);
    newMapping.setIsCurrent(true);
    newMapping.setCreatedBy(userId);
    newMapping.setChangeReason("Rollback to version " + versionId);

    newMapping = reportMappingRepository.save(newMapping);

    // Log rollback to audit
    logMappingChange(companyId, userId, reportType, lineCode, "ROLLBACK",
        "Rollback to version " + versionId,
        oldAccountPattern, newMapping.getAccountPattern(),
        oldOperator, newMapping.getOperator(),
        currentMapping.getVersion(), newMapping.getVersion());

    logger.info("Rolled back mapping {}:{} from version {} to {} (new version {}) by user {}",
        reportType, lineCode, currentMapping.getVersion(), versionId, newMapping.getVersion(), userId);

    return toDTO(newMapping);
  }

  @Override
  @Transactional(readOnly = true)
  public long countAffectedSnapshots(String reportType, Integer mappingVersion) {
    Long companyId = getCompanyId();
    return reportSnapshotRepository.findByMappingVersion(companyId, reportType, mappingVersion).size();
  }

  // ==================== Private Helper Methods ====================

  private Long getCompanyId() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return companyId;
  }

  private Long getUserId() {
    return SecurityUtils.getCurrentUserId();
  }

  private ReportMappingDTO toDTO(ReportMapping mapping) {
    ReportMappingDTO dto = new ReportMappingDTO();
    dto.setId(mapping.getId());
    dto.setReportType(mapping.getReportType());
    dto.setLineCode(mapping.getLineCode());
    dto.setLineName(mapping.getLineName());
    dto.setLineNameEnglish(mapping.getLineNameEnglish());
    dto.setAccountPattern(mapping.getAccountPattern());
    dto.setOperator(mapping.getOperator());
    dto.setSignModifier(mapping.getSignModifier());
    dto.setDisplayOrder(mapping.getDisplayOrder());
    dto.setParentLineCode(mapping.getParentLineCode());
    dto.setLevel(mapping.getLevel());
    dto.setIsCalculated(mapping.getIsCalculated());
    dto.setFormula(mapping.getFormula());
    dto.setVersion(mapping.getVersion());
    dto.setIsCurrent(mapping.getIsCurrent());
    dto.setCreatedAt(mapping.getCreatedAt());
    dto.setUpdatedAt(mapping.getUpdatedAt());
    dto.setChangeReason(mapping.getChangeReason());

    // Get creator name
    if (mapping.getCreatedBy() != null) {
      userRepository.findById(mapping.getCreatedBy())
          .map(User::getFullName)
          .ifPresent(dto::setCreatedByName);
    }

    return dto;
  }

  private MappingVersionDTO toVersionDTO(ReportMapping mapping) {
    MappingVersionDTO dto = new MappingVersionDTO();
    dto.setVersion(mapping.getVersion());
    dto.setLineCode(mapping.getLineCode());
    dto.setReportType(mapping.getReportType());
    dto.setAccountPattern(mapping.getAccountPattern());
    dto.setOperator(mapping.getOperator());
    dto.setFormula(mapping.getFormula());
    dto.setChangeReason(mapping.getChangeReason());
    dto.setCreatedAt(mapping.getCreatedAt());
    dto.setIsCurrent(mapping.getIsCurrent());

    if (mapping.getCreatedBy() != null) {
      userRepository.findById(mapping.getCreatedBy())
          .map(User::getFullName)
          .ifPresent(dto::setCreatedByName);
    }

    return dto;
  }

  private MappingVersionDTO auditLogToVersionDTO(AuditLog auditLog) {
    MappingVersionDTO dto = new MappingVersionDTO();

    // Parse entity_id (format: "B01:110")
    String entityId = auditLog.getEntityId();
    if (entityId != null && entityId.contains(":")) {
      String[] parts = entityId.split(":");
      dto.setReportType(parts[0]);
      dto.setLineCode(parts[1]);
    }

    dto.setChangeReason(auditLog.getReason());
    dto.setCreatedAt(auditLog.getCreatedAt());

    if (auditLog.getUserId() != null) {
      userRepository.findById(auditLog.getUserId())
          .map(User::getFullName)
          .ifPresent(dto::setCreatedByName);
    }

    // Parse changes JSON for diff
    if (auditLog.getChanges() != null) {
      try {
        Map<String, Object> diff = objectMapper.convertValue(auditLog.getChanges(), Map.class);
        dto.setDiff(diff);
      } catch (Exception e) {
        logger.warn("Failed to parse audit log changes: {}", e.getMessage());
      }
    }

    return dto;
  }

  private Map<String, Object> calculateDiff(ReportMapping oldMapping, ReportMapping newMapping) {
    Map<String, Object> diff = new HashMap<>();

    if (!objectsEqual(oldMapping.getAccountPattern(), newMapping.getAccountPattern())) {
      diff.put("accountPattern", Map.of(
          "old", oldMapping.getAccountPattern(),
          "new", newMapping.getAccountPattern()));
    }

    if (!objectsEqual(oldMapping.getOperator(), newMapping.getOperator())) {
      diff.put("operator", Map.of(
          "old", oldMapping.getOperator(),
          "new", newMapping.getOperator()));
    }

    if (!objectsEqual(oldMapping.getFormula(), newMapping.getFormula())) {
      diff.put("formula", Map.of(
          "old", oldMapping.getFormula(),
          "new", newMapping.getFormula()));
    }

    return diff.isEmpty() ? null : diff;
  }

  private boolean objectsEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.equals(b);
  }

  private void logMappingChange(
      Long companyId, Long userId, String reportType, String lineCode,
      String action, String reason,
      String oldAccountPattern, String newAccountPattern,
      String oldOperator, String newOperator,
      Integer oldVersion, Integer newVersion) {

    try {
      AuditLog auditLog = new AuditLog();
      auditLog.setCompanyId(companyId);
      auditLog.setUserId(userId);
      auditLog.setEntityType(ENTITY_TYPE_REPORT_MAPPING);
      auditLog.setEntityId(reportType + ":" + lineCode);
      auditLog.setAction(action);
      auditLog.setReason(reason);
      auditLog.setSuccess(true);

      // Build changes JSON
      ObjectNode changes = objectMapper.createObjectNode();
      changes.put("old_version", oldVersion);
      changes.put("new_version", newVersion);

      ObjectNode diff = objectMapper.createObjectNode();
      if (!objectsEqual(oldAccountPattern, newAccountPattern)) {
        ObjectNode patternDiff = objectMapper.createObjectNode();
        patternDiff.put("old", oldAccountPattern);
        patternDiff.put("new", newAccountPattern);
        diff.set("account_pattern", patternDiff);
      }
      if (!objectsEqual(oldOperator, newOperator)) {
        ObjectNode operatorDiff = objectMapper.createObjectNode();
        operatorDiff.put("old", oldOperator);
        operatorDiff.put("new", newOperator);
        diff.set("operator", operatorDiff);
      }
      changes.set("diff", diff);

      auditLog.setChanges(changes);
      auditLog.setCreatedAt(Instant.now());

      auditLogRepository.save(auditLog);

    } catch (Exception e) {
      logger.error("Failed to log mapping change audit: {}", e.getMessage(), e);
      // Don't fail the operation if audit logging fails
    }
  }
}

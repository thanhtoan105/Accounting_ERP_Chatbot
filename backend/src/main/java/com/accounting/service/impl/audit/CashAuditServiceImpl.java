package com.accounting.service.impl.audit;

import com.accounting.dto.audit.CashAuditLogDTO;
import com.accounting.dto.audit.CashAuditPageDTO;
import com.accounting.dto.audit.CashAuditQueryDTO;
import com.accounting.dto.audit.PurgeRequestDTO;
import com.accounting.dto.audit.PurgeResponseDTO;
import com.accounting.entity.AuditLog;
import com.accounting.entity.audit.AuditPurgeRequest;
import com.accounting.entity.audit.AuditPurgeRequest.PurgeStatus;
import com.accounting.enums.CashBankAuditAction;
import com.accounting.exception.BusinessException;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.audit.AuditPurgeRequestRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.CashAuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of CashAuditService for Cash & Bank audit operations.
 *
 * <p>Provides audit log querying, export, and purge workflow functionality
 * with full multi-tenancy support.
 */
@Service
@Transactional
public class CashAuditServiceImpl implements CashAuditService {

  private static final Logger logger = LoggerFactory.getLogger(CashAuditServiceImpl.class);
  private static final int EXPORT_LIMIT = 10000;

  private final AuditLogRepository auditLogRepository;
  private final AuditPurgeRequestRepository purgeRequestRepository;
  private final ObjectMapper objectMapper;

  // ThreadLocal to store last export hash for retrieval
  private final ThreadLocal<String> lastExportHash = new ThreadLocal<>();

  public CashAuditServiceImpl(
      AuditLogRepository auditLogRepository,
      AuditPurgeRequestRepository purgeRequestRepository,
      ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.purgeRequestRepository = purgeRequestRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public CashAuditPageDTO queryAuditLogs(CashAuditQueryDTO filter) {
    Long companyId = requireCompanyContext();
    validateFilter(filter);

    // Build specification
    Specification<AuditLog> spec = buildSpecification(companyId, filter);

    // Execute query with pagination
    Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
    PageRequest pageRequest = PageRequest.of(filter.getPage(), filter.getSize(), sort);
    Page<AuditLog> page = auditLogRepository.findAll(spec, pageRequest);

    // Convert to DTOs
    List<CashAuditLogDTO> data = page.getContent().stream()
        .map(this::toDto)
        .toList();

    // Log the query action
    logAuditExplorerQuery(filter, page.getTotalElements());

    return CashAuditPageDTO.of(data, filter.getPage(), filter.getSize(), page.getTotalElements());
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] exportAuditLogs(CashAuditQueryDTO filter, ExportFormat format) {
    Long companyId = requireCompanyContext();
    validateFilter(filter);

    // Build specification with export limit
    Specification<AuditLog> spec = buildSpecification(companyId, filter);
    Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
    PageRequest pageRequest = PageRequest.of(0, EXPORT_LIMIT, sort);

    Page<AuditLog> page = auditLogRepository.findAll(spec, pageRequest);

    if (page.getTotalElements() > EXPORT_LIMIT) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Export limited to " + EXPORT_LIMIT + " records. Apply filters to reduce result set.");
    }

    List<CashAuditLogDTO> data = page.getContent().stream()
        .map(this::toDto)
        .toList();

    byte[] content;
    switch (format) {
      case JSON -> content = exportToJson(data);
      case CSV -> content = exportToCsv(data);
      case PDF -> content = exportToPdf(data, filter);
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown format: " + format);
    }

    String hash = calculateHash(content);
    lastExportHash.set(hash);

    // Log the export action
    logComplianceExport(filter, format, data.size(), hash);

    return content;
  }

  @Override
  public String getLastExportHash() {
    return lastExportHash.get();
  }

  @Override
  public PurgeResponseDTO requestPurge(PurgeRequestDTO request) {
    Long companyId = requireCompanyContext();
    Long requesterId = SecurityUtils.getCurrentUserId();

    // Estimate records to be purged
    Instant from = request.getDateFrom().atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant to = request.getDateTo().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    long estimatedRecords = auditLogRepository.count(
        buildSpecificationForDateRange(companyId, from, to));

    // Create purge request
    AuditPurgeRequest purgeRequest = new AuditPurgeRequest();
    purgeRequest.setCompanyId(companyId);
    purgeRequest.setRequesterId(requesterId);
    purgeRequest.setDateFrom(request.getDateFrom());
    purgeRequest.setDateTo(request.getDateTo());
    purgeRequest.setReason(request.getReason());
    purgeRequest.setEstimatedRecords((int) estimatedRecords);

    purgeRequest = purgeRequestRepository.save(purgeRequest);

    // Log the purge request
    logPurgeRequest(purgeRequest);

    logger.info("Purge request created: {} by user {} for company {}",
        purgeRequest.getId(), requesterId, companyId);

    return PurgeResponseDTO.pending(
        purgeRequest.getId(),
        requesterId,
        request.getDateFrom(),
        request.getDateTo(),
        request.getReason(),
        (int) estimatedRecords);
  }

  @Override
  public PurgeResponseDTO approvePurge(UUID requestId) {
    Long companyId = requireCompanyContext();
    Long approverId = SecurityUtils.getCurrentUserId();

    AuditPurgeRequest request = purgeRequestRepository.findByIdAndCompanyId(requestId, companyId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Purge request not found"));

    if (request.getStatus() != PurgeStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Purge request already processed");
    }

    // CRITICAL: Validate dual approval - approver must be different from requester
    if (request.getRequesterId().equals(approverId)) {
      throw new BusinessException(
          "Approver cannot be the same as requester",
          "SELF_APPROVAL_FORBIDDEN");
    }

    // Execute the purge
    Instant from = request.getDateFrom().atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant to = request.getDateTo().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);

    Specification<AuditLog> spec = buildSpecificationForDateRange(companyId, from, to);
    List<AuditLog> logsToDelete = auditLogRepository.findAll(spec);
    int deletedCount = logsToDelete.size();

    auditLogRepository.deleteAll(logsToDelete);

    // Update request status
    request.approve(approverId, deletedCount);
    purgeRequestRepository.save(request);

    // Log the approval
    logPurgeApproval(request);

    logger.info("Purge approved: {} by user {} - {} records purged",
        requestId, approverId, deletedCount);

    return PurgeResponseDTO.approved(requestId, request.getRequesterId(), approverId, deletedCount);
  }

  @Override
  public PurgeResponseDTO rejectPurge(UUID requestId, String reason) {
    Long companyId = requireCompanyContext();
    Long rejectedById = SecurityUtils.getCurrentUserId();

    AuditPurgeRequest request = purgeRequestRepository.findByIdAndCompanyId(requestId, companyId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Purge request not found"));

    if (request.getStatus() != PurgeStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Purge request already processed");
    }

    request.reject(rejectedById, reason);
    purgeRequestRepository.save(request);

    // Log the rejection
    logPurgeRejection(request);

    logger.info("Purge rejected: {} by user {}", requestId, rejectedById);

    return PurgeResponseDTO.rejected(requestId, request.getRequesterId(), rejectedById);
  }

  @Override
  @Transactional(readOnly = true)
  public PurgeResponseDTO getPurgeRequest(UUID requestId) {
    Long companyId = requireCompanyContext();

    return purgeRequestRepository.findByIdAndCompanyId(requestId, companyId)
        .map(this::toPurgeResponseDto)
        .orElse(null);
  }

  // === Private Helper Methods ===

  private Long requireCompanyContext() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "X-Company-Id header required");
    }
    return companyId;
  }

  private void validateFilter(CashAuditQueryDTO filter) {
    if (filter.getDateFrom() == null || filter.getDateTo() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "dateFrom and dateTo are required");
    }
    if (filter.getDateFrom().isAfter(filter.getDateTo())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "dateFrom must be before dateTo");
    }
    if (!filter.isDateRangeValid()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Date range cannot exceed 12 months");
    }
    if (!filter.isSizeValid()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "size must be 10, 20, or 50");
    }
  }

  private Specification<AuditLog> buildSpecification(Long companyId, CashAuditQueryDTO filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // CRITICAL: Always filter by company
      predicates.add(cb.equal(root.get("companyId"), companyId));

      // Date range
      Instant from = filter.getDateFrom().atStartOfDay().toInstant(ZoneOffset.UTC);
      Instant to = filter.getDateTo().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
      predicates.add(cb.between(root.get("createdAt"), from, to));

      // Optional filters
      if (filter.getBankAccountId() != null) {
        // Filter by entity_id containing bank account reference
        predicates.add(cb.like(root.get("entityId"), "%" + filter.getBankAccountId() + "%"));
      }

      if (filter.getActionTypes() != null && !filter.getActionTypes().isEmpty()) {
        predicates.add(root.get("action").in(filter.getActionTypes()));
      }

      if (filter.getUserId() != null) {
        predicates.add(cb.equal(root.get("userId"), filter.getUserId()));
      }

      if (filter.getEntityType() != null) {
        predicates.add(cb.equal(root.get("entityType"), filter.getEntityType()));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Specification<AuditLog> buildSpecificationForDateRange(
      Long companyId, Instant from, Instant to) {
    return (root, query, cb) -> cb.and(
        cb.equal(root.get("companyId"), companyId),
        cb.between(root.get("createdAt"), from, to));
  }

  private CashAuditLogDTO toDto(AuditLog log) {
    return CashAuditLogDTO.builder()
        .id(log.getId())
        .action(log.getAction())
        .entityType(log.getEntityType())
        .entityId(log.getEntityId())
        .entityDisplay(log.getEntityDisplay())
        .userId(log.getUserId())
        .userEmail(log.getEmail())
        .actorRole(log.getActorRole())
        .timestamp(log.getCreatedAt())
        .details(log.getChanges())
        .metadata(log.getMetadata())
        .success(log.getSuccess())
        .failureReason(log.getFailureReason())
        .ipAddress(log.getIpAddress())
        .traceId(log.getTraceId())
        .build();
  }

  private PurgeResponseDTO toPurgeResponseDto(AuditPurgeRequest request) {
    PurgeResponseDTO dto = new PurgeResponseDTO();
    dto.setRequestId(request.getId());
    dto.setStatus(request.getStatus().name());
    dto.setRequestedBy(request.getRequesterId());
    dto.setApprovedBy(request.getApproverId());
    dto.setDateFrom(request.getDateFrom());
    dto.setDateTo(request.getDateTo());
    dto.setReason(request.getReason());
    dto.setEstimatedRecords(request.getEstimatedRecords());
    dto.setRecordsPurged(request.getRecordsPurged());
    dto.setCreatedAt(request.getCreatedAt());
    dto.setCompletedAt(request.getProcessedAt());
    return dto;
  }

  // === Export Methods ===

  private byte[] exportToJson(List<CashAuditLogDTO> data) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter()
          .writeValueAsBytes(data);
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize audit logs", e);
    }
  }

  private byte[] exportToCsv(List<CashAuditLogDTO> data) {
    StringBuilder sb = new StringBuilder();
    sb.append("id,timestamp,action,entityType,entityId,userId,userEmail,actorRole,success,ipAddress\n");

    for (CashAuditLogDTO log : data) {
      appendCsvField(sb, log.getId() != null ? log.getId().toString() : "");
      appendCsvField(sb, log.getTimestamp() != null ? log.getTimestamp().toString() : "");
      appendCsvField(sb, log.getAction());
      appendCsvField(sb, log.getEntityType());
      appendCsvField(sb, log.getEntityId());
      appendCsvField(sb, log.getUserId() != null ? log.getUserId().toString() : "");
      appendCsvField(sb, log.getUserEmail());
      appendCsvField(sb, log.getActorRole());
      appendCsvField(sb, log.getSuccess() != null ? log.getSuccess().toString() : "");
      appendCsvField(sb, log.getIpAddress());
      sb.setCharAt(sb.length() - 1, '\n');
    }

    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private byte[] exportToPdf(List<CashAuditLogDTO> data, CashAuditQueryDTO filter) {
    // PDF export requires OpenPDF/iText library
    // For now, return a placeholder - will be implemented in ComplianceExportService
    String content = "PDF Export - " + data.size() + " records\n";
    content += "Period: " + filter.getDateFrom() + " to " + filter.getDateTo() + "\n";
    content += "Generated: " + Instant.now() + "\n";
    return content.getBytes(StandardCharsets.UTF_8);
  }

  private void appendCsvField(StringBuilder sb, String value) {
    sb.append('"');
    if (value != null) {
      sb.append(value.replace("\"", "\"\""));
    }
    sb.append('"').append(',');
  }

  private String calculateHash(byte[] content) {
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

  // === Audit Logging Methods ===

  private void logAuditExplorerQuery(CashAuditQueryDTO filter, long resultCount) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      ObjectNode filters = objectMapper.createObjectNode();
      filters.put("dateFrom", filter.getDateFrom().toString());
      filters.put("dateTo", filter.getDateTo().toString());
      if (filter.getBankAccountId() != null) {
        filters.put("bankAccountId", filter.getBankAccountId());
      }
      if (filter.getActionTypes() != null) {
        filters.set("actionTypes", objectMapper.valueToTree(filter.getActionTypes()));
      }
      if (filter.getUserId() != null) {
        filters.put("userId", filter.getUserId());
      }
      metadata.set("filters", filters);
      metadata.put("resultCount", resultCount);
      metadata.put("page", filter.getPage());
      metadata.put("size", filter.getSize());

      logCashBankAuditAction(CashBankAuditAction.AUDIT_EXPLORER_QUERY, metadata);
    } catch (Exception e) {
      logger.warn("Failed to log audit explorer query", e);
    }
  }

  private void logComplianceExport(CashAuditQueryDTO filter, ExportFormat format,
      int recordCount, String hash) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("exportType", "AUDIT_LOG");
      metadata.put("format", format.name());
      metadata.put("recordCount", recordCount);
      metadata.put("documentHash", hash);
      metadata.put("dateFrom", filter.getDateFrom().toString());
      metadata.put("dateTo", filter.getDateTo().toString());

      logCashBankAuditAction(CashBankAuditAction.COMPLIANCE_EXPORT, metadata);
    } catch (Exception e) {
      logger.warn("Failed to log compliance export", e);
    }
  }

  private void logPurgeRequest(AuditPurgeRequest request) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("requestId", request.getId().toString());
      metadata.put("dateFrom", request.getDateFrom().toString());
      metadata.put("dateTo", request.getDateTo().toString());
      metadata.put("reason", request.getReason());
      metadata.put("estimatedRecords", request.getEstimatedRecords());

      logCashBankAuditAction(CashBankAuditAction.PURGE_REQUEST, metadata);
    } catch (Exception e) {
      logger.warn("Failed to log purge request", e);
    }
  }

  private void logPurgeApproval(AuditPurgeRequest request) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("requestId", request.getId().toString());
      metadata.put("requesterId", request.getRequesterId());
      metadata.put("approverId", request.getApproverId());
      metadata.put("recordsPurged", request.getRecordsPurged());
      metadata.put("dateFrom", request.getDateFrom().toString());
      metadata.put("dateTo", request.getDateTo().toString());

      logCashBankAuditAction(CashBankAuditAction.PURGE_APPROVE, metadata);
    } catch (Exception e) {
      logger.warn("Failed to log purge approval", e);
    }
  }

  private void logPurgeRejection(AuditPurgeRequest request) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("requestId", request.getId().toString());
      metadata.put("requesterId", request.getRequesterId());
      metadata.put("rejectedById", request.getApproverId());
      metadata.put("rejectionReason", request.getRejectionReason());

      logCashBankAuditAction(CashBankAuditAction.PURGE_REJECT, metadata);
    } catch (Exception e) {
      logger.warn("Failed to log purge rejection", e);
    }
  }

  private void logCashBankAuditAction(CashBankAuditAction action, JsonNode metadata) {
    AuditLog log = new AuditLog();
    log.setAction(action.getValue());
    log.setEventType("CASH_BANK_AUDIT");
    log.setCompanyId(CompanyContext.getCompanyId());
    log.setUserId(SecurityUtils.getCurrentUserId());
    log.setMetadata(metadata);
    log.setSuccess(true);
    log.setCreatedAt(Instant.now());

    auditLogRepository.save(log);
  }
}

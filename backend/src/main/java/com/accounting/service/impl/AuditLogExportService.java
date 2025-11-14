package com.accounting.service.impl;

import com.accounting.dto.audit.AuditLogFilter;
import com.accounting.dto.audit.AuditLogListItemDTO;
import com.accounting.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.Formatter;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AuditLogExportService {

  private static final int EXPORT_LIMIT = 10000;

  public static int EXPORT_LIMIT() {
    return EXPORT_LIMIT;
  }
  private static final DateTimeFormatter FILENAME_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final AuditLogRepository auditLogRepository;
  private final AuditLogQueryService queryService;
  private final ObjectMapper objectMapper;

  public AuditLogExportService(
      AuditLogRepository auditLogRepository,
      AuditLogQueryService queryService,
      ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.queryService = queryService;
    this.objectMapper = objectMapper;
  }

  public AuditLogExportResult export(AuditLogFilter filter) {
    Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
    List<AuditLogListItemDTO> entries = auditLogRepository
        .findAll(queryService.specificationFor(filter), PageRequest.of(0, EXPORT_LIMIT, sort))
        .map(queryService::toDto)
        .getContent();

    String csv = buildCsv(entries);
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
    String hash = sha256Hex(bytes);
    String filename = "audit-logs-" + FILENAME_TS.format(java.time.OffsetDateTime.now()) + ".csv";
    return new AuditLogExportResult(bytes, filename, hash, entries.size());
  }

  private String buildCsv(List<AuditLogListItemDTO> entries) {
    StringBuilder sb = new StringBuilder();
    sb.append("occurredAt,entityType,entityId,entityDisplay,action,eventType,success,failureReason,actorEmail,actorRole,ipAddress,userAgent,changes,metadata,traceId\n");
    for (AuditLogListItemDTO entry : entries) {
      int rowStart = sb.length();
      appendCsvField(sb, entry.occurredAt() != null ? entry.occurredAt().toString() : "");
      appendCsvField(sb, entry.entityType());
      appendCsvField(sb, entry.entityId());
      appendCsvField(sb, entry.entityDisplay());
      appendCsvField(sb, entry.action());
      appendCsvField(sb, entry.eventType());
      appendCsvField(sb, entry.success() != null ? entry.success().toString() : "");
      appendCsvField(sb, entry.failureReason());
      appendCsvField(sb, entry.actor() != null ? entry.actor().email() : "");
      appendCsvField(sb, entry.actor() != null ? entry.actor().role() : "");
      appendCsvField(sb, entry.ipAddress());
      appendCsvField(sb, entry.userAgent());
      appendCsvField(sb, toJson(entry.changes()));
      appendCsvField(sb, toJson(entry.metadata()));
      appendCsvField(sb, entry.traceId());
      if (sb.length() > rowStart) {
        sb.setCharAt(sb.length() - 1, '\n');
      } else {
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  private void appendCsvField(StringBuilder sb, String value) {
    sb.append('"');
    if (value != null) {
      sb.append(value.replace("\"", "\"\""));
    }
    sb.append('"').append(',');
  }

  private String toJson(Object value) {
    if (value == null) {
      return "";
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "";
    }
  }

  private String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
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

  public record AuditLogExportResult(byte[] content, String filename, String hash, int count) {}
}


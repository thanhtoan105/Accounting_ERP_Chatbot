package com.accounting.service.impl;

import com.accounting.dto.VoucherHistoryEntryDTO;
import com.accounting.entity.AuditLog;
import com.accounting.repository.AuditLogRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.VoucherHistoryService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of VoucherHistoryService.
 * Provides voucher history retrieval and diff generation functionality.
 */
@Service
@Transactional(readOnly = true)
public class VoucherHistoryServiceImpl implements VoucherHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(VoucherHistoryServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    private final AuditLogRepository auditLogRepository;

    public VoucherHistoryServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public List<VoucherHistoryEntryDTO> getVoucherHistory(UUID voucherId) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        // Query audit logs for this voucher, scoped to current company
        List<AuditLog> auditLogs = auditLogRepository.findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc(
                "VOUCHER",
                voucherId.toString(),
                companyId);

        List<VoucherHistoryEntryDTO> historyEntries = new ArrayList<>();
        for (AuditLog auditLog : auditLogs) {
            try {
                // Generate field-level diff
                Map<String, FieldDiff> diff = null;
                if (auditLog.getChanges() != null) {
                    JsonNode changes = auditLog.getChanges();
                    JsonNode before = changes.get("before");
                    JsonNode after = changes.get("after");
                    diff = generateFieldDiff(before, after);
                }

                // Generate plain English summary
                String summary = generatePlainEnglishSummary(auditLog, diff);

                // Build DTO
                VoucherHistoryEntryDTO entry = new VoucherHistoryEntryDTO();
                entry.setId(auditLog.getId());
                entry.setAction(auditLog.getAction());
                entry.setTimestamp(auditLog.getCreatedAt());
                entry.setUserId(auditLog.getUserId());
                entry.setUserEmail(auditLog.getEmail());
                entry.setUserRole(auditLog.getActorRole());
                entry.setIpAddress(auditLog.getIpAddress());
                entry.setUserAgent(auditLog.getUserAgent());
                entry.setSuccess(auditLog.getSuccess());
                entry.setFailureReason(auditLog.getFailureReason());
                entry.setSummary(summary);
                entry.setDiff(diff);
                entry.setDiffHash(auditLog.getMetadata() != null && auditLog.getMetadata().has("diffHash")
                        ? auditLog.getMetadata().get("diffHash").asText()
                        : null);
                entry.setChanges(auditLog.getChanges());

                historyEntries.add(entry);
            } catch (Exception e) {
                logger.error("Failed to process audit log entry {}: {}", auditLog.getId(), e.getMessage(), e);
            }
        }

        return historyEntries;
    }

    @Override
    public Map<String, FieldDiff> generateFieldDiff(JsonNode beforeSnapshot, JsonNode afterSnapshot) {
        Map<String, FieldDiff> diffMap = new HashMap<>();

        if (beforeSnapshot == null && afterSnapshot == null) {
            return diffMap;
        }

        // Handle create case (before is null)
        if (beforeSnapshot == null && afterSnapshot != null) {
            afterSnapshot.fieldNames().forEachRemaining(fieldName -> {
                diffMap.put(fieldName, new FieldDiff(null, afterSnapshot.get(fieldName), ChangeType.ADDED));
            });
            return diffMap;
        }

        // Handle delete case (after is null)
        if (beforeSnapshot != null && afterSnapshot == null) {
            beforeSnapshot.fieldNames().forEachRemaining(fieldName -> {
                diffMap.put(fieldName, new FieldDiff(beforeSnapshot.get(fieldName), null, ChangeType.REMOVED));
            });
            return diffMap;
        }

        // Both exist - compare field by field
        // At this point, both beforeSnapshot and afterSnapshot are non-null (guaranteed by earlier checks)
        assert beforeSnapshot != null && afterSnapshot != null;
        
        Map<String, JsonNode> beforeFields = new HashMap<>();
        beforeSnapshot.fieldNames().forEachRemaining(fieldName -> 
            beforeFields.put(fieldName, beforeSnapshot.get(fieldName)));

        Map<String, JsonNode> afterFields = new HashMap<>();
        afterSnapshot.fieldNames().forEachRemaining(fieldName -> 
            afterFields.put(fieldName, afterSnapshot.get(fieldName)));

        // Find added and changed fields
        for (Map.Entry<String, JsonNode> afterEntry : afterFields.entrySet()) {
            String fieldName = afterEntry.getKey();
            JsonNode afterValue = afterEntry.getValue();
            JsonNode beforeValue = beforeFields.get(fieldName);

            if (beforeValue == null) {
                // Field was added
                diffMap.put(fieldName, new FieldDiff(null, afterValue, ChangeType.ADDED));
            } else if (!beforeValue.equals(afterValue)) {
                // Field value changed
                diffMap.put(fieldName, new FieldDiff(beforeValue, afterValue, ChangeType.CHANGED));
            }
            // If values are equal, no diff entry (field unchanged)
        }

        // Find removed fields
        for (Map.Entry<String, JsonNode> beforeEntry : beforeFields.entrySet()) {
            String fieldName = beforeEntry.getKey();
            if (!afterFields.containsKey(fieldName) && !diffMap.containsKey(fieldName)) {
                // Field was removed
                diffMap.put(fieldName, new FieldDiff(beforeEntry.getValue(), null, ChangeType.REMOVED));
            }
        }

        return diffMap;
    }

    @Override
    public String generatePlainEnglishSummary(AuditLog auditLog, Map<String, FieldDiff> diff) {
        String action = auditLog.getAction();
        String userEmail = auditLog.getEmail() != null ? auditLog.getEmail() : "Unknown User";
        String date = DATE_FORMATTER.format(auditLog.getCreatedAt());

        // Generate summary based on action type
        switch (action) {
            case "VOUCHER_CREATED":
                return String.format("Voucher created by %s on %s", userEmail, date);
            case "VOUCHER_UPDATED":
                if (diff != null && !diff.isEmpty()) {
                    String fieldsChanged = String.join(", ", diff.keySet());
                    return String.format("Voucher updated by %s on %s. Fields changed: %s", userEmail, date, fieldsChanged);
                }
                return String.format("Voucher updated by %s on %s", userEmail, date);
            case "VOUCHER_POSTED":
                return String.format("Voucher posted by %s on %s", userEmail, date);
            case "VOUCHER_UNPOSTED":
                return String.format("Voucher unposted by %s on %s", userEmail, date);
            case "VOUCHER_REVERSED":
                return String.format("Voucher reversed by %s on %s", userEmail, date);
            case "VOUCHER_REVERSAL_CREATED":
                return String.format("Reversal voucher created by %s on %s", userEmail, date);
            case "VOUCHER_DELETED":
                return String.format("Voucher deleted by %s on %s", userEmail, date);
            default:
                return String.format("Voucher %s by %s on %s", action.toLowerCase().replace("_", " "), userEmail, date);
        }
    }
}


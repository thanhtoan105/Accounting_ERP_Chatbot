package com.accounting.service;

import java.util.List;
import java.util.UUID;

import com.accounting.dto.VoucherHistoryEntryDTO;

/**
 * Service for retrieving voucher history and generating field-level diffs.
 */
public interface VoucherHistoryService {

    /**
     * Get voucher history (list of audit log entries) for a specific voucher.
     *
     * @param voucherId voucher ID
     * @return list of voucher history entries in chronological order (newest first)
     */
    List<VoucherHistoryEntryDTO> getVoucherHistory(UUID voucherId);

    /**
     * Generate field-by-field diff between before and after JSON snapshots.
     *
     * @param beforeSnapshot JSON snapshot before the change
     * @param afterSnapshot  JSON snapshot after the change
     * @return map of field diffs with change type (added/removed/changed)
     */
    java.util.Map<String, FieldDiff> generateFieldDiff(
            com.fasterxml.jackson.databind.JsonNode beforeSnapshot,
            com.fasterxml.jackson.databind.JsonNode afterSnapshot);

    /**
     * Generate plain English summary for an audit log entry.
     *
     * @param auditLog audit log entry
     * @param diff     field-level diff map
     * @return human-readable summary (e.g., "Voucher posted by John Doe on 2025-11-13")
     */
    String generatePlainEnglishSummary(
            com.accounting.entity.AuditLog auditLog,
            java.util.Map<String, FieldDiff> diff);

    /**
     * DTO for field-level diff information.
     */
    class FieldDiff {
        private final Object beforeValue;
        private final Object afterValue;
        private final ChangeType changeType;

        public FieldDiff(Object beforeValue, Object afterValue, ChangeType changeType) {
            this.beforeValue = beforeValue;
            this.afterValue = afterValue;
            this.changeType = changeType;
        }

        public Object getBeforeValue() {
            return beforeValue;
        }

        public Object getAfterValue() {
            return afterValue;
        }

        public ChangeType getChangeType() {
            return changeType;
        }
    }

    /**
     * Enum for change types.
     */
    enum ChangeType {
        ADDED,    // Field was added (exists in after, not in before)
        REMOVED,  // Field was removed (exists in before, not in after)
        CHANGED   // Field value changed (exists in both, values differ)
    }
}

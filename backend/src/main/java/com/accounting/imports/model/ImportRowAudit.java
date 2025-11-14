package com.accounting.imports.model;

/**
 * Represents a single row-level audit event for an import attempt.
 *
 * @param rowNumber     the row number from the uploaded template (1-indexed)
 * @param beforePayload payload describing the row data before processing (if
 *                      applicable)
 * @param afterPayload  payload describing the resultant entity state (if
 *                      applicable)
 * @param status        outcome status (e.g., SUCCESS, ERROR, ROLLED_BACK,
 *                      VALIDATION_ERROR)
 * @param message       optional descriptive message or error detail
 */
public record ImportRowAudit(
        int rowNumber,
        Object beforePayload,
        Object afterPayload,
        String status,
        String message) {
}

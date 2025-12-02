package com.accounting.service;

import com.accounting.dto.reconciliation.BankStatementFormatDTO;
import com.accounting.dto.reconciliation.ColumnMappingSuggestionDTO;
import com.accounting.dto.reconciliation.StatementImportRequestDTO;
import com.accounting.dto.reconciliation.StatementImportResultDTO;
import java.io.InputStream;
import java.util.UUID;

/**
 * Service for importing and parsing bank statements.
 * Handles CSV/Excel file parsing, column mapping, and duplicate detection.
 *
 * <p>
 * AC6.5-01: Statement Import with Column Mapping Wizard
 * <p>
 * AC6.5-02: Duplicate Import Detection
 * <p>
 * AC6.5-07: Error Handling for Import
 */
public interface StatementImportService {

    /**
     * Analyze file headers and suggest column mappings.
     * Also retrieves any saved format profile for the bank account.
     *
     * @param reconciliationId reconciliation ID
     * @param fileInputStream  file input stream
     * @param fileName         original file name (for extension detection)
     * @return column mapping suggestions and saved profile
     */
    ColumnMappingSuggestionDTO analyzeFileHeaders(UUID reconciliationId, InputStream fileInputStream, String fileName);

    /**
     * Import statement file with provided column mappings.
     * Validates data, detects duplicates, and creates statement lines.
     *
     * @param reconciliationId reconciliation ID
     * @param fileInputStream  file input stream
     * @param fileName         original file name (for extension detection)
     * @param importRequest    column mapping configuration
     * @return import result with success/error details
     */
    StatementImportResultDTO importStatement(UUID reconciliationId, InputStream fileInputStream, String fileName,
            StatementImportRequestDTO importRequest);

    /**
     * Calculate SHA256 hash of file for duplicate detection.
     *
     * @param fileInputStream file input stream
     * @return SHA256 hash as hex string
     */
    String calculateFileHash(InputStream fileInputStream);

    /**
     * Check if file has already been imported (by hash).
     *
     * @param companyId company ID
     * @param fileHash  SHA256 file hash
     * @return true if duplicate
     */
    boolean isDuplicateFile(Long companyId, String fileHash);

    /**
     * Get saved format profile for a bank account.
     *
     * @param bankAccountId bank account ID
     * @return format profile or null if not found
     */
    BankStatementFormatDTO getFormatProfile(Long bankAccountId);

    /**
     * Save format profile for a bank account.
     *
     * @param bankAccountId bank account ID
     * @param format        format configuration
     * @return saved format profile
     */
    BankStatementFormatDTO saveFormatProfile(Long bankAccountId, StatementImportRequestDTO format);

    /**
     * Delete format profile for a bank account.
     *
     * @param bankAccountId bank account ID
     */
    void deleteFormatProfile(Long bankAccountId);

    /**
     * Download error report as CSV for a failed import.
     *
     * @param errorReportId error report ID
     * @return CSV content as bytes
     */
    byte[] downloadErrorReport(String errorReportId);
}

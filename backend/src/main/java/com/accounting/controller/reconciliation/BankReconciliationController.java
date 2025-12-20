package com.accounting.controller.reconciliation;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.reconciliation.AutoMatchConfigDTO;
import com.accounting.dto.reconciliation.AutoMatchResultDTO;
import com.accounting.dto.reconciliation.BankReconciliationDTO;
import com.accounting.dto.reconciliation.BankReconciliationListDTO;
import com.accounting.dto.reconciliation.BankStatementFormatDTO;
import com.accounting.dto.reconciliation.BankStatementLineDTO;
import com.accounting.dto.reconciliation.ColumnMappingSuggestionDTO;
import com.accounting.dto.reconciliation.CreateAdjustmentRequestDTO;
import com.accounting.dto.reconciliation.CreateReconciliationRequestDTO;
import com.accounting.dto.reconciliation.LedgerTransactionDTO;
import com.accounting.dto.reconciliation.MatchRequestDTO;
import com.accounting.dto.reconciliation.ReconciliationAdjustmentDTO;
import com.accounting.dto.reconciliation.StatementImportRequestDTO;
import com.accounting.dto.reconciliation.StatementImportResultDTO;
import com.accounting.entity.reconciliation.MatchStatus;
import com.accounting.service.AuditService;
import com.accounting.service.BankReconciliationService;
import com.accounting.service.ReconciliationMatcherService;
import com.accounting.service.StatementImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for Bank Reconciliation operations.
 * Provides complete reconciliation workflow: create, import, match, adjust, complete.
 *
 * <p>AC6.5-01: Statement Import with Column Mapping
 * <p>AC6.5-02: Duplicate Import Detection
 * <p>AC6.5-03: Auto-Suggest Matches
 * <p>AC6.5-04: Manual Match/Unmatch
 * <p>AC6.5-05: Adjustment Workflow with Approval
 * <p>AC6.5-06: Balance Verification on Completion
 * <p>AC6.5-07: Error Handling
 * <p>AC6.5-08: Audit Trail
 * <p>AC6.5-09: Export
 */
@RestController
@RequestMapping("/api/v1/bank-reconciliations")
@Tag(name = "Bank Reconciliation", description = "Bank reconciliation workflow operations")
public class BankReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(BankReconciliationController.class);

    private final BankReconciliationService reconciliationService;
    private final StatementImportService importService;
    private final ReconciliationMatcherService matcherService;
    private final AuditService auditService;

    public BankReconciliationController(
            BankReconciliationService reconciliationService,
            StatementImportService importService,
            ReconciliationMatcherService matcherService,
            AuditService auditService) {
        this.reconciliationService = reconciliationService;
        this.importService = importService;
        this.matcherService = matcherService;
        this.auditService = auditService;
    }

    // ========== Reconciliation CRUD ==========

    /**
     * Create a new bank reconciliation.
     *
     * @param request creation request with bank account ID, period, and balance
     * @param httpRequest HTTP request for audit logging
     * @return created reconciliation
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Create reconciliation", description = "Create a new bank reconciliation for a period")
    public ResponseEntity<Map<String, Object>> createReconciliation(
            @Valid @RequestBody CreateReconciliationRequestDTO request,
            HttpServletRequest httpRequest) {

        BankReconciliationDTO result = reconciliationService.createReconciliation(request);

        logAuditEvent("RECONCILIATION_CREATE", result.getId(), "Created reconciliation", httpRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(wrapData(result));
    }

    /**
     * Get reconciliation by ID with full details.
     *
     * @param id reconciliation ID
     * @return reconciliation with statement lines and adjustments
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Get reconciliation", description = "Get reconciliation with full details")
    public ResponseEntity<Map<String, Object>> getReconciliation(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id) {

        BankReconciliationDTO result = reconciliationService.getReconciliation(id);
        return ResponseEntity.ok(wrapData(result));
    }

    /**
     * List reconciliations with filtering and pagination.
     *
     * @param bankAccountId bank account filter (optional)
     * @param status status filter (optional)
     * @param page page number (0-based)
     * @param size page size
     * @param sortBy sort field
     * @param sortDir sort direction
     * @return paginated list of reconciliations
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "List reconciliations", description = "List reconciliations with filtering and pagination")
    public ResponseEntity<Map<String, Object>> listReconciliations(
            @Parameter(description = "Bank account ID filter") @RequestParam(required = false) Long bankAccountId,
            @Parameter(description = "Status filter") @RequestParam(required = false) String status,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);

        Page<BankReconciliationListDTO> resultPage = reconciliationService.listReconciliations(
                bankAccountId, status, pageable);

        return ResponseEntity.ok(wrapPageData(resultPage));
    }

    /**
     * Delete a reconciliation (only if not completed).
     *
     * @param id reconciliation ID
     * @param httpRequest HTTP request for audit logging
     * @return no content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Delete reconciliation", description = "Delete a non-completed reconciliation")
    public ResponseEntity<Void> deleteReconciliation(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        reconciliationService.deleteReconciliation(id);

        logAuditEvent("RECONCILIATION_DELETE", id, "Deleted reconciliation", httpRequest);

        return ResponseEntity.noContent().build();
    }

    // ========== Statement Import ==========

    /**
     * Analyze file headers and suggest column mappings.
     * First step of import wizard.
     *
     * @param id reconciliation ID
     * @param file statement file (CSV or Excel)
     * @return column mapping suggestions
     */
    @PostMapping("/{id}/import/analyze")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Analyze file headers", description = "Analyze file and suggest column mappings")
    public ResponseEntity<Map<String, Object>> analyzeFileHeaders(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {

        validateFile(file);

        try {
            ColumnMappingSuggestionDTO result = importService.analyzeFileHeaders(
                    id, file.getInputStream(), file.getOriginalFilename());
            return ResponseEntity.ok(wrapData(result));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Import statement file with column mappings.
     * Second step of import wizard.
     *
     * @param id reconciliation ID
     * @param file statement file
     * @param importRequest column mapping configuration
     * @param httpRequest HTTP request for audit logging
     * @return import result with success/error details
     */
    @PostMapping("/{id}/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Import statement", description = "Import bank statement with column mappings")
    public ResponseEntity<Map<String, Object>> importStatement(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestPart("config") @Valid StatementImportRequestDTO importRequest,
            HttpServletRequest httpRequest) {

        validateFile(file);

        try {
            StatementImportResultDTO result = importService.importStatement(
                    id, file.getInputStream(), file.getOriginalFilename(), importRequest);

            logAuditEvent("STATEMENT_IMPORT", id,
                    String.format("Imported %d lines, %d errors",
                            result.getImportedRows(), result.getErrorRows()),
                    httpRequest);

            return ResponseEntity.ok(wrapData(result));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Download import error report.
     *
     * @param id reconciliation ID
     * @param errorReportId error report ID from import result
     * @return CSV file with errors
     */
    @GetMapping("/{id}/import-errors/{errorReportId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Download error report", description = "Download import error report as CSV")
    public ResponseEntity<byte[]> downloadImportErrors(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Parameter(description = "Error report ID") @PathVariable String errorReportId) {

        byte[] csvBytes = importService.downloadErrorReport(errorReportId);

        String filename = String.format("import_errors_%s_%s.csv", id, LocalDate.now());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }

    /**
     * Get saved format profile for a bank account.
     *
     * @param bankAccountId bank account ID
     * @return format profile or 404
     */
    @GetMapping("/format-profiles/{bankAccountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Get format profile", description = "Get saved column mapping profile")
    public ResponseEntity<Map<String, Object>> getFormatProfile(
            @Parameter(description = "Bank account ID") @PathVariable Long bankAccountId) {

        BankStatementFormatDTO profile = importService.getFormatProfile(bankAccountId);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No format profile found");
        }
        return ResponseEntity.ok(wrapData(profile));
    }

    // ========== Statement Lines ==========

    /**
     * Get statement lines for a reconciliation.
     *
     * @param id reconciliation ID
     * @param matchStatus match status filter
     * @param page page number
     * @param size page size
     * @return paginated statement lines
     */
    @GetMapping("/{id}/lines")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Get statement lines", description = "Get paginated statement lines")
    public ResponseEntity<Map<String, Object>> getStatementLines(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Parameter(description = "Match status filter") @RequestParam(required = false) MatchStatus matchStatus,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<BankStatementLineDTO> resultPage = reconciliationService.getStatementLines(id, matchStatus, pageable);

        return ResponseEntity.ok(wrapPageData(resultPage));
    }

    /**
     * Get a single statement line.
     *
     * @param id reconciliation ID
     * @param lineId statement line ID
     * @return statement line
     */
    @GetMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Get statement line", description = "Get single statement line details")
    public ResponseEntity<Map<String, Object>> getStatementLine(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Parameter(description = "Statement line ID") @PathVariable UUID lineId) {

        BankStatementLineDTO line = reconciliationService.getStatementLine(id, lineId);
        return ResponseEntity.ok(wrapData(line));
    }

    /**
     * Update notes for a statement line.
     *
     * @param id reconciliation ID
     * @param lineId statement line ID
     * @param body request body with notes
     * @return updated statement line
     */
    @PatchMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Update line notes", description = "Update notes for a statement line")
    public ResponseEntity<Map<String, Object>> updateLineNotes(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Parameter(description = "Statement line ID") @PathVariable UUID lineId,
            @RequestBody Map<String, String> body) {

        String notes = body.get("notes");
        BankStatementLineDTO line = reconciliationService.updateLineNotes(id, lineId, notes);
        return ResponseEntity.ok(wrapData(line));
    }

    // ========== Matching Operations ==========

    /**
     * Get ledger transactions for matching.
     *
     * @param id reconciliation ID
     * @return list of ledger transactions
     */
    @GetMapping("/{id}/ledger-transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Get ledger transactions", description = "Get ledger transactions available for matching")
    public ResponseEntity<Map<String, Object>> getLedgerTransactions(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id) {

        List<LedgerTransactionDTO> transactions = matcherService.getLedgerTransactions(id);
        return ResponseEntity.ok(wrapData(transactions));
    }

    /**
     * Run auto-match algorithm.
     *
     * @param id reconciliation ID
     * @param config auto-match configuration (optional)
     * @param httpRequest HTTP request for audit logging
     * @return auto-match results with suggestions
     */
    @PostMapping("/{id}/auto-match")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Run auto-match", description = "Run auto-match algorithm on unmatched lines")
    public ResponseEntity<Map<String, Object>> runAutoMatch(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @RequestBody(required = false) AutoMatchConfigDTO config,
            HttpServletRequest httpRequest) {

        AutoMatchResultDTO result = matcherService.runAutoMatch(id, config);

        logAuditEvent("AUTO_MATCH", id,
                String.format("Found %d matches, applied %d",
                        result.getMatchesFound(), result.getMatchesApplied()),
                httpRequest);

        return ResponseEntity.ok(wrapData(result));
    }

    /**
     * Manually match a statement line to a voucher.
     *
     * @param id reconciliation ID
     * @param matchRequest match request with line ID and voucher ID
     * @param httpRequest HTTP request for audit logging
     * @return updated statement line
     */
    @PostMapping("/{id}/match")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Manual match", description = "Manually match a statement line to a voucher")
    public ResponseEntity<Map<String, Object>> manualMatch(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Valid @RequestBody MatchRequestDTO matchRequest,
            HttpServletRequest httpRequest) {

        BankStatementLineDTO result = reconciliationService.manualMatch(id, matchRequest);

        logAuditEvent("MANUAL_MATCH", id,
                String.format("Matched line %s to voucher %s",
                        matchRequest.getStatementLineId(), matchRequest.getVoucherId()),
                httpRequest);

        return ResponseEntity.ok(wrapData(result));
    }

    /**
     * Unmatch a statement line.
     *
     * @param id reconciliation ID
     * @param lineId statement line ID
     * @param httpRequest HTTP request for audit logging
     * @return updated statement line
     */
    @PostMapping("/{id}/lines/{lineId}/unmatch")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Unmatch line", description = "Unmatch a statement line from its voucher")
    public ResponseEntity<Map<String, Object>> unmatch(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Parameter(description = "Statement line ID") @PathVariable UUID lineId,
            HttpServletRequest httpRequest) {

        BankStatementLineDTO result = reconciliationService.unmatch(id, lineId);

        logAuditEvent("UNMATCH", id, "Unmatched line " + lineId, httpRequest);

        return ResponseEntity.ok(wrapData(result));
    }

    // ========== Adjustments ==========

    /**
     * Create an adjustment.
     *
     * @param id reconciliation ID
     * @param request adjustment creation request
     * @param httpRequest HTTP request for audit logging
     * @return created adjustment
     */
    @PostMapping("/{id}/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Create adjustment", description = "Create a reconciliation adjustment")
    public ResponseEntity<Map<String, Object>> createAdjustment(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Valid @RequestBody CreateAdjustmentRequestDTO request,
            HttpServletRequest httpRequest) {

        ReconciliationAdjustmentDTO result = reconciliationService.createAdjustment(id, request);

        logAuditEvent("ADJUSTMENT_CREATE", id,
                String.format("Created %s adjustment: %s",
                        request.getAdjustmentType(), request.getAmount()),
                httpRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(wrapData(result));
    }

    /**
     * Approve an adjustment.
     *
     * @param id reconciliation ID
     * @param adjustmentId adjustment ID
     * @param httpRequest HTTP request for audit logging
     * @return updated adjustment
     */
    @PostMapping("/{id}/adjustments/{adjustmentId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Approve adjustment", description = "Approve a pending adjustment")
    public ResponseEntity<Map<String, Object>> approveAdjustment(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Parameter(description = "Adjustment ID") @PathVariable UUID adjustmentId,
            HttpServletRequest httpRequest) {

        ReconciliationAdjustmentDTO result = reconciliationService.approveAdjustment(id, adjustmentId);

        logAuditEvent("ADJUSTMENT_APPROVE", id, "Approved adjustment " + adjustmentId, httpRequest);

        return ResponseEntity.ok(wrapData(result));
    }

    /**
     * Reject an adjustment.
     *
     * @param id reconciliation ID
     * @param adjustmentId adjustment ID
     * @param body request body with rejection reason
     * @param httpRequest HTTP request for audit logging
     * @return updated adjustment
     */
    @PostMapping("/{id}/adjustments/{adjustmentId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Reject adjustment", description = "Reject a pending adjustment")
    public ResponseEntity<Map<String, Object>> rejectAdjustment(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Parameter(description = "Adjustment ID") @PathVariable UUID adjustmentId,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {

        String reason = body.get("reason");
        ReconciliationAdjustmentDTO result = reconciliationService.rejectAdjustment(id, adjustmentId, reason);

        logAuditEvent("ADJUSTMENT_REJECT", id,
                String.format("Rejected adjustment %s: %s", adjustmentId, reason),
                httpRequest);

        return ResponseEntity.ok(wrapData(result));
    }

    /**
     * Post an approved adjustment.
     *
     * @param id reconciliation ID
     * @param adjustmentId adjustment ID
     * @param httpRequest HTTP request for audit logging
     * @return updated adjustment with voucher ID
     */
    @PostMapping("/{id}/adjustments/{adjustmentId}/post")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Post adjustment", description = "Post an approved adjustment (creates voucher)")
    public ResponseEntity<Map<String, Object>> postAdjustment(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Parameter(description = "Adjustment ID") @PathVariable UUID adjustmentId,
            HttpServletRequest httpRequest) {

        ReconciliationAdjustmentDTO result = reconciliationService.postAdjustment(id, adjustmentId);

        logAuditEvent("ADJUSTMENT_POST", id, "Posted adjustment " + adjustmentId, httpRequest);

        return ResponseEntity.ok(wrapData(result));
    }

    /**
     * Delete a pending adjustment.
     *
     * @param id reconciliation ID
     * @param adjustmentId adjustment ID
     * @param httpRequest HTTP request for audit logging
     * @return no content
     */
    @DeleteMapping("/{id}/adjustments/{adjustmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Delete adjustment", description = "Delete a pending adjustment")
    public ResponseEntity<Void> deleteAdjustment(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @Parameter(description = "Adjustment ID") @PathVariable UUID adjustmentId,
            HttpServletRequest httpRequest) {

        reconciliationService.deleteAdjustment(id, adjustmentId);

        logAuditEvent("ADJUSTMENT_DELETE", id, "Deleted adjustment " + adjustmentId, httpRequest);

        return ResponseEntity.noContent().build();
    }

    // ========== Completion ==========

    /**
     * Complete the reconciliation.
     *
     * @param id reconciliation ID
     * @param body request body with optional notes
     * @param httpRequest HTTP request for audit logging
     * @return completed reconciliation
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Complete reconciliation", description = "Complete and finalize the reconciliation")
    public ResponseEntity<Map<String, Object>> completeReconciliation(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest httpRequest) {

        String notes = body != null ? body.get("notes") : null;
        BankReconciliationDTO result = reconciliationService.completeReconciliation(id, notes);

        logAuditEvent("RECONCILIATION_COMPLETE", id, "Completed reconciliation", httpRequest);

        return ResponseEntity.ok(wrapData(result));
    }

    /**
     * Reopen a completed reconciliation.
     *
     * @param id reconciliation ID
     * @param httpRequest HTTP request for audit logging
     * @return reopened reconciliation
     */
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Reopen reconciliation", description = "Reopen a completed reconciliation")
    public ResponseEntity<Map<String, Object>> reopenReconciliation(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        BankReconciliationDTO result = reconciliationService.reopenReconciliation(id);

        logAuditEvent("RECONCILIATION_REOPEN", id, "Reopened reconciliation", httpRequest);

        return ResponseEntity.ok(wrapData(result));
    }

    // ========== Export ==========

    /**
     * Export reconciliation to Excel.
     *
     * @param id reconciliation ID
     * @param httpRequest HTTP request for audit logging
     * @return Excel file
     */
    @GetMapping("/{id}/export/excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Export to Excel", description = "Export reconciliation to Excel file")
    public ResponseEntity<byte[]> exportToExcel(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        byte[] excelBytes = reconciliationService.exportToExcel(id);

        logAuditEvent("RECONCILIATION_EXPORT", id, "Exported to Excel", httpRequest);

        String filename = String.format("bank_reconciliation_%s_%s.xlsx", id, LocalDate.now());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(excelBytes.length)
                .body(excelBytes);
    }

    /**
     * Export reconciliation to PDF.
     *
     * @param id reconciliation ID
     * @param httpRequest HTTP request for audit logging
     * @return PDF file
     */
    @GetMapping("/{id}/export/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Export to PDF", description = "Export reconciliation to PDF file")
    public ResponseEntity<byte[]> exportToPdf(
            @Parameter(description = "Reconciliation ID") @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        byte[] pdfBytes = reconciliationService.exportToPdf(id);

        logAuditEvent("RECONCILIATION_EXPORT", id, "Exported to PDF", httpRequest);

        String filename = String.format("bank_reconciliation_%s_%s.pdf", id, LocalDate.now());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }

    // ========== Helper Methods ==========

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name is required");
        }

        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!extension.equals("csv") && !extension.equals("xlsx") && !extension.equals("xls")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file format. Please upload CSV or Excel file");
        }

        // Max 10MB
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File too large. Maximum size is 10MB");
        }
    }

    private Map<String, Object> wrapData(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        return response;
    }

    private Map<String, Object> wrapPageData(Page<?> page) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", page.getContent());

        Map<String, Object> meta = new HashMap<>();
        meta.put("page", page.getNumber());
        meta.put("size", page.getSize());
        meta.put("totalElements", page.getTotalElements());
        meta.put("totalPages", page.getTotalPages());
        response.put("meta", meta);

        return response;
    }

    private void logAuditEvent(String action, UUID reconciliationId, String details, HttpServletRequest request) {
        try {
            String fullDetails = String.format("reconciliationId=%s, %s", reconciliationId, details);
            auditService.logReconciliationOperation(action, reconciliationId, fullDetails, getClientIp(request));
        } catch (Exception e) {
            log.warn("Failed to log audit event for {}: {}", action, e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

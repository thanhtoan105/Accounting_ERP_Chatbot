package com.accounting.controller.cashbook;

import com.accounting.dto.VoucherDTO;
import com.accounting.dto.cashbook.CashBookExportJobDTO;
import com.accounting.dto.cashbook.CashBookFilterDTO;
import com.accounting.dto.cashbook.CashBookResponseDTO;
import com.accounting.dto.cashbook.CashBookSummaryDTO;
import com.accounting.service.AuditService;
import com.accounting.service.CashBookAsyncExportService;
import com.accounting.service.CashBookExportService;
import com.accounting.service.CashBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Cash Book / Bank Book viewing operations.
 * Provides per-account ledger view, multi-account summary, voucher drill-down,
 * and export.
 *
 * <p>
 * AC6.4-01: Per-account view with running balance
 * <p>
 * AC6.4-02: Drill-down to voucher detail
 * <p>
 * AC6.4-03: Multi-account aggregated view
 * <p>
 * AC6.4-06: RBAC enforced for all queries/exports
 * <p>
 * AC6.4-08: All views/exports logged in audit
 */
@RestController
@RequestMapping("/api/v1/cash-book")
@Tag(name = "Cash Book", description = "Cash Book / Bank Book viewing and export operations")
public class CashBookController {

    private static final Logger logger = LoggerFactory.getLogger(CashBookController.class);
    private static final int ASYNC_EXPORT_THRESHOLD = 10000;

    private final CashBookService cashBookService;
    private final CashBookExportService cashBookExportService;
    private final CashBookAsyncExportService cashBookAsyncExportService;
    private final AuditService auditService;

    public CashBookController(
            CashBookService cashBookService,
            CashBookExportService cashBookExportService,
            CashBookAsyncExportService cashBookAsyncExportService,
            AuditService auditService) {
        this.cashBookService = cashBookService;
        this.cashBookExportService = cashBookExportService;
        this.cashBookAsyncExportService = cashBookAsyncExportService;
        this.auditService = auditService;
    }

    /**
     * Get cash book transactions for a specific bank/cash account.
     * Returns transaction ledger with running balances (AC6.4-01).
     *
     * @param bankAccountId bank account ID
     * @param dateFrom      start date filter (inclusive, optional)
     * @param dateTo        end date filter (inclusive, optional)
     * @param type          filter by type: "receipt", "payment", or "all"
     *                      (optional, default: all)
     * @param reference     reference search filter (optional)
     * @param page          page number (0-based, default: 0)
     * @param size          page size (default: 20, max: 100)
     * @param request       HTTP request for audit logging
     * @return cash book response with transactions and running balances
     */
    @GetMapping("/{bankAccountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Get cash book for account", description = "Returns transaction ledger with running balances for a specific bank/cash account")
    public ResponseEntity<Map<String, Object>> getCashBook(
            @Parameter(description = "Bank account ID") @PathVariable Long bankAccountId,
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateFrom,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateTo,
            @Parameter(description = "Transaction type: receipt, payment, or all") @RequestParam(required = false, defaultValue = "all") String type,
            @Parameter(description = "Reference search term") @RequestParam(required = false) String reference,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(required = false, defaultValue = "20") int size,
            HttpServletRequest request) {

        // Validate page size
        if (size > 100) {
            size = 100;
        }

        // Build filter for audit logging
        CashBookFilterDTO filter = new CashBookFilterDTO()
                .withBankAccountId(bankAccountId)
                .withDateRange(dateFrom, dateTo)
                .withTransactionType(type)
                .withReference(reference)
                .withPagination(page, size);

        // Get cash book data
        CashBookResponseDTO response = cashBookService.getCashBook(filter);

        // AC6.4-08: Log view operation
        logViewOperation("CASH_BOOK_VIEW", bankAccountId, filter, request);

        // Build response in standard format
        Map<String, Object> body = new HashMap<>();
        body.put("data", response);

        Map<String, Object> meta = new HashMap<>();
        meta.put("page", response.getPage());
        meta.put("size", response.getSize());
        meta.put("totalElements", response.getTotalCount());
        meta.put("totalPages", (int) Math.ceil((double) response.getTotalCount() / response.getSize()));
        body.put("meta", meta);

        return ResponseEntity.ok(body);
    }

    /**
     * Get voucher detail for drill-down (AC6.4-02).
     * Returns full voucher with lines and attachments.
     *
     * @param bankAccountId bank account ID (for context validation)
     * @param voucherId     voucher ID
     * @param request       HTTP request for audit logging
     * @return voucher detail with lines and attachments
     */
    @GetMapping("/{bankAccountId}/transactions/{voucherId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Get voucher detail", description = "Drill-down to voucher detail with lines and attachments")
    public ResponseEntity<Map<String, Object>> getVoucherDetail(
            @Parameter(description = "Bank account ID") @PathVariable Long bankAccountId,
            @Parameter(description = "Voucher ID") @PathVariable UUID voucherId,
            HttpServletRequest request) {

        VoucherDTO voucher = cashBookService.getVoucherDetail(bankAccountId, voucherId);

        // AC6.4-08: Log drill-down operation
        logDrillDownOperation("CASH_BOOK_DRILL_DOWN", bankAccountId, voucherId, request);

        Map<String, Object> body = new HashMap<>();
        body.put("data", voucher);

        return ResponseEntity.ok(body);
    }

    /**
     * Get multi-account cash book summary (AC6.4-03).
     * Returns aggregated totals for multiple bank/cash accounts.
     *
     * @param accountIds list of bank account IDs (optional, null for all active
     *                   accounts)
     * @param dateFrom   start date filter (inclusive, optional)
     * @param dateTo     end date filter (inclusive, optional)
     * @param request    HTTP request for audit logging
     * @return summary with account totals and grand totals
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Get cash book summary", description = "Multi-account aggregated view with grand totals")
    public ResponseEntity<Map<String, Object>> getCashBookSummary(
            @Parameter(description = "Bank account IDs (comma-separated, optional)") @RequestParam(required = false) List<Long> accountIds,
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateFrom,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateTo,
            HttpServletRequest request) {

        CashBookSummaryDTO summary = cashBookService.getCashBookSummary(accountIds, dateFrom, dateTo);

        // AC6.4-08: Log summary view operation
        CashBookFilterDTO filter = new CashBookFilterDTO()
                .withAccountIds(accountIds)
                .withDateRange(dateFrom, dateTo);
        logViewOperation("CASH_BOOK_SUMMARY_VIEW", null, filter, request);

        Map<String, Object> body = new HashMap<>();
        body.put("data", summary);

        return ResponseEntity.ok(body);
    }

    /**
     * Count transactions for a bank account (for pagination and async export
     * decision).
     *
     * @param bankAccountId bank account ID
     * @param dateFrom      start date filter (inclusive, optional)
     * @param dateTo        end date filter (inclusive, optional)
     * @param type          filter by type: "receipt", "payment", or "all"
     *                      (optional)
     * @return transaction count
     */
    @GetMapping("/{bankAccountId}/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Count transactions", description = "Count transactions for pagination and async export decision")
    public ResponseEntity<Map<String, Object>> countTransactions(
            @Parameter(description = "Bank account ID") @PathVariable Long bankAccountId,
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateFrom,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateTo,
            @Parameter(description = "Transaction type: receipt, payment, or all") @RequestParam(required = false, defaultValue = "all") String type) {

        long count = cashBookService.countTransactions(bankAccountId, dateFrom, dateTo, type);

        Map<String, Object> body = new HashMap<>();
        body.put("data", Map.of("count", count));

        return ResponseEntity.ok(body);
    }

    /**
     * Export cash book to Excel or PDF (AC6.4-04).
     * For datasets > 10,000 records, returns 202 with async job info (AC6.4-05).
     *
     * @param bankAccountId bank account ID
     * @param format        export format: "excel" or "pdf"
     * @param dateFrom      start date filter (optional)
     * @param dateTo        end date filter (optional)
     * @param type          transaction type filter (optional)
     * @param request       HTTP request for audit logging
     * @return file bytes or async job info
     */
    @GetMapping("/{bankAccountId}/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Export cash book", description = "Export cash book to Excel or PDF with company branding")
    public ResponseEntity<?> exportCashBook(
            @Parameter(description = "Bank account ID") @PathVariable Long bankAccountId,
            @Parameter(description = "Export format: excel or pdf") @RequestParam(defaultValue = "excel") String format,
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateFrom,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateTo,
            @Parameter(description = "Transaction type: receipt, payment, or all") @RequestParam(required = false, defaultValue = "all") String type,
            HttpServletRequest request) {

        CashBookFilterDTO filter = new CashBookFilterDTO()
                .withBankAccountId(bankAccountId)
                .withDateRange(dateFrom, dateTo)
                .withTransactionType(type);

        // Check record count for async decision (AC6.4-05)
        long count = cashBookService.countTransactions(bankAccountId, dateFrom, dateTo, type);
        if (count > ASYNC_EXPORT_THRESHOLD) {
            // Queue async export job
            CashBookExportJobDTO job = cashBookAsyncExportService.queueExportJob(
                    bankAccountId, filter, format, count);

            Map<String, Object> asyncResponse = new HashMap<>();
            asyncResponse.put("jobId", job.getJobId());
            asyncResponse.put("message", "Export queued for async processing");
            asyncResponse.put("recordCount", count);
            asyncResponse.put("status", job.getStatus());
            asyncResponse.put("statusUrl", "/api/v1/cash-book/exports/" + job.getJobId() + "/status");

            // AC6.4-08: Log export request
            logExportOperation("CASH_BOOK_EXPORT_ASYNC", bankAccountId, filter, format, request);
            return ResponseEntity.accepted().body(asyncResponse);
        }

        // Get all transactions for export (no pagination)
        CashBookFilterDTO exportFilter = filter.withPagination(0, (int) count + 1);
        CashBookResponseDTO cashBookData = cashBookService.getCashBook(exportFilter);

        // Generate export
        byte[] exportBytes;
        String contentType;
        String filename;

        if ("pdf".equalsIgnoreCase(format)) {
            exportBytes = cashBookExportService.exportToPdf(cashBookData, filter);
            contentType = "application/pdf";
            filename = String.format("cash_book_%d_%s.pdf", bankAccountId, LocalDate.now());
        } else {
            exportBytes = cashBookExportService.exportToExcel(cashBookData, filter);
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            filename = String.format("cash_book_%d_%s.xlsx", bankAccountId, LocalDate.now());
        }

        // AC6.4-08: Log export operation
        logExportOperation("CASH_BOOK_EXPORT", bankAccountId, filter, format, request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(exportBytes.length)
                .body(exportBytes);
    }

    /**
     * Export multi-account summary to Excel or PDF (AC6.4-04).
     *
     * @param format     export format: "excel" or "pdf"
     * @param accountIds bank account IDs (optional, null for all)
     * @param dateFrom   start date filter (optional)
     * @param dateTo     end date filter (optional)
     * @param request    HTTP request for audit logging
     * @return file bytes
     */
    @GetMapping("/summary/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Export cash book summary", description = "Export multi-account summary to Excel or PDF")
    public ResponseEntity<byte[]> exportCashBookSummary(
            @Parameter(description = "Export format: excel or pdf") @RequestParam(defaultValue = "excel") String format,
            @Parameter(description = "Bank account IDs (comma-separated)") @RequestParam(required = false) List<Long> accountIds,
            @Parameter(description = "Start date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateFrom,
            @Parameter(description = "End date (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateTo,
            HttpServletRequest request) {

        CashBookFilterDTO filter = new CashBookFilterDTO()
                .withAccountIds(accountIds)
                .withDateRange(dateFrom, dateTo);

        CashBookSummaryDTO summaryData = cashBookService.getCashBookSummary(accountIds, dateFrom, dateTo);

        byte[] exportBytes;
        String contentType;
        String filename;

        if ("pdf".equalsIgnoreCase(format)) {
            exportBytes = cashBookExportService.exportSummaryToPdf(summaryData, filter);
            contentType = "application/pdf";
            filename = String.format("cash_book_summary_%s.pdf", LocalDate.now());
        } else {
            exportBytes = cashBookExportService.exportSummaryToExcel(summaryData, filter);
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            filename = String.format("cash_book_summary_%s.xlsx", LocalDate.now());
        }

        // AC6.4-08: Log export operation
        logExportOperation("CASH_BOOK_SUMMARY_EXPORT", null, filter, format, request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(exportBytes.length)
                .body(exportBytes);
    }

    /**
     * Get async export job status (AC6.4-05).
     *
     * @param jobId export job ID
     * @return job status with progress
     */
    @GetMapping("/exports/{jobId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Get export job status", description = "Get status of an async export job")
    public ResponseEntity<Map<String, Object>> getExportJobStatus(
            @Parameter(description = "Export job ID") @PathVariable String jobId) {

        return cashBookAsyncExportService.getJobStatus(jobId)
                .map(job -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("jobId", job.getJobId());
                    response.put("status", job.getStatus());
                    response.put("progress", job.getProgress());
                    response.put("recordCount", job.getRecordCount());
                    response.put("createdAt", job.getCreatedAt());
                    response.put("completedAt", job.getCompletedAt());
                    if ("COMPLETED".equals(job.getStatus())) {
                        response.put("downloadUrl", job.getDownloadUrl());
                    }
                    if (job.getErrorMessage() != null) {
                        response.put("errorMessage", job.getErrorMessage());
                    }
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Download completed async export file (AC6.4-05).
     *
     * @param jobId export job ID
     * @return file bytes
     */
    @GetMapping("/exports/{jobId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
    @Operation(summary = "Download export file", description = "Download completed async export file")
    public ResponseEntity<byte[]> downloadExportFile(
            @Parameter(description = "Export job ID") @PathVariable String jobId) {

        return cashBookAsyncExportService.getJobStatus(jobId)
                .filter(job -> "COMPLETED".equals(job.getStatus()))
                .flatMap(job -> cashBookAsyncExportService.getExportedFile(jobId)
                        .map(bytes -> {
                            String contentType = "pdf".equalsIgnoreCase(job.getFormat())
                                    ? "application/pdf"
                                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                            String extension = "pdf".equalsIgnoreCase(job.getFormat()) ? ".pdf" : ".xlsx";
                            String filename = String.format("cash_book_%d_%s%s",
                                    job.getBankAccountId(), LocalDate.now(), extension);

                            return ResponseEntity.ok()
                                    .header(HttpHeaders.CONTENT_DISPOSITION,
                                            "attachment; filename=\"" + filename + "\"")
                                    .contentType(MediaType.parseMediaType(contentType))
                                    .contentLength(bytes.length)
                                    .body(bytes);
                        }))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Log view operation for audit trail (AC6.4-08).
     */
    private void logViewOperation(String action, Long bankAccountId, CashBookFilterDTO filter,
            HttpServletRequest request) {
        try {
            String details = String.format("bankAccountId=%s, filters=%s",
                    bankAccountId != null ? bankAccountId : "all",
                    filter.toAuditString());

            auditService.logCashBookOperation(
                    action,
                    bankAccountId,
                    details,
                    getClientIp(request));
        } catch (Exception e) {
            logger.warn("Failed to log audit event for {}: {}", action, e.getMessage());
        }
    }

    /**
     * Log drill-down operation for audit trail (AC6.4-08).
     */
    private void logDrillDownOperation(String action, Long bankAccountId, UUID voucherId, HttpServletRequest request) {
        try {
            String details = String.format("bankAccountId=%s, voucherId=%s", bankAccountId, voucherId);

            auditService.logCashBookOperation(
                    action,
                    bankAccountId,
                    details,
                    getClientIp(request));
        } catch (Exception e) {
            logger.warn("Failed to log audit event for {}: {}", action, e.getMessage());
        }
    }

    /**
     * Log export operation for audit trail (AC6.4-08).
     */
    private void logExportOperation(String action, Long bankAccountId, CashBookFilterDTO filter,
            String format, HttpServletRequest request) {
        try {
            String details = String.format("bankAccountId=%s, format=%s, filters=%s",
                    bankAccountId != null ? bankAccountId : "all",
                    format,
                    filter.toAuditString());

            auditService.logCashBookOperation(
                    action,
                    bankAccountId,
                    details,
                    getClientIp(request));
        } catch (Exception e) {
            logger.warn("Failed to log audit event for {}: {}", action, e.getMessage());
        }
    }

    /**
     * Get client IP address from request.
     */
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

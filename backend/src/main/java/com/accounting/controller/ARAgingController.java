package com.accounting.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.ARAgingDrillDownDTO;
import com.accounting.dto.ARAgingReportDTO;
import com.accounting.dto.ARAgingReportResponse;
import com.accounting.dto.ARDashboardMetricsDTO;
import com.accounting.dto.ARInvoiceDetailDTO;
import com.accounting.dto.ARReminderConfigDTO;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ARAgingExportService;
import com.accounting.service.ARAgingService;
import com.accounting.service.ARDashboardMetricsService;
import com.accounting.service.ARReminderService;
import com.accounting.service.AuditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * REST controller for AR aging report operations.
 * Provides endpoints for aging report generation with caching and RBAC.
 */
@RestController
@RequestMapping("/api/v1/ar-aging")
@Tag(name = "AR Aging", description = "AR aging report and analysis endpoints")
public class ARAgingController {

    private final ARAgingService arAgingService;
    private final ARAgingExportService exportService;
    private final ARDashboardMetricsService dashboardMetricsService;
    private final ARReminderService reminderService;
    private final AuditService auditService;

    @Autowired
    public ARAgingController(
            ARAgingService arAgingService,
            ARAgingExportService exportService,
            ARDashboardMetricsService dashboardMetricsService,
            ARReminderService reminderService,
            AuditService auditService) {
        this.arAgingService = arAgingService;
        this.exportService = exportService;
        this.dashboardMetricsService = dashboardMetricsService;
        this.reminderService = reminderService;
        this.auditService = auditService;
    }

    /**
     * Get AR aging report with optional filters.
     * Results are cached in Redis with 1-hour TTL.
     * Requires ADMIN, CFO, CHIEF_ACCOUNTANT, or ACCOUNTANT role.
     *
     * @param customerId optional customer ID filter
     * @param asOfDate   as-of date for aging calculation (defaults to today)
     * @param status     optional invoice status filter
     * @param bucket     optional aging bucket filter
     * @param page       page number (0-indexed)
     * @param size       page size
     * @param sortBy     sort field (default: customerName)
     * @param sortDir    sort direction (asc/desc)
     * @return paginated aging report
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(summary = "Get AR aging report", description = "Returns AR aging report with aging buckets per customer. Results are cached for 1 hour.")
    public ResponseEntity<ARAgingReportResponse> getAgingReport(
            @Parameter(description = "Customer ID filter (optional)") @RequestParam(required = false) Long customerId,
            @Parameter(description = "As-of date for aging calculation (defaults to today)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @Parameter(description = "Invoice status filter (optional)") @RequestParam(required = false) String status,
            @Parameter(description = "Aging bucket filter: CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90 (optional)") @RequestParam(required = false) String bucket,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "customerName") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = "asc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        LocalDate snapshotDate = asOfDate != null ? asOfDate : LocalDate.now();
        Page<ARAgingReportDTO> result = arAgingService.getAgingReport(customerId, snapshotDate, status, bucket,
                pageable);

        // Create response with snapshot metadata
        ARAgingReportResponse response = new ARAgingReportResponse();
        response.setData(result);

        ARAgingReportResponse.SnapshotMetadata metadata = new ARAgingReportResponse.SnapshotMetadata();
        metadata.setComputedAt(LocalDateTime.now());
        metadata.setSnapshotDate(snapshotDate);
        metadata.setCacheStatus("HIT"); // Assume cache hit; actual status depends on cache interceptor
        response.setMetadata(metadata);

        return ResponseEntity.ok(response);
    }

    /**
     * Manually refresh AR aging cache.
     * Forces recalculation of aging buckets for all customers.
     * Requires ADMIN or CHIEF_ACCOUNTANT role.
     *
     * @param asOfDate as-of date for aging calculation (defaults to today)
     * @return success message
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Refresh AR aging cache", description = "Manually refreshes the AR aging cache by recalculating all aging buckets.")
    public ResponseEntity<String> refreshCache(
            @Parameter(description = "As-of date for aging calculation (defaults to today)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {

        arAgingService.refreshAgingCache(asOfDate);
        return ResponseEntity.ok("AR aging cache refreshed successfully");
    }

    /**
     * Get drill-down invoice list for a specific customer and aging bucket.
     * Shows detailed invoice information for invoices in the selected bucket.
     * Requires ADMIN, CFO, CHIEF_ACCOUNTANT, or ACCOUNTANT role.
     *
     * @param customerId     customer ID
     * @param agingBucketKey aging bucket key (CURRENT, DAYS_1_30, DAYS_31_60,
     *                       DAYS_61_90, DAYS_OVER_90)
     * @param asOfDate       as-of date for aging calculation (defaults to today)
     * @param page           page number (0-indexed)
     * @param size           page size
     * @return paginated invoice list
     */
    @GetMapping("/{customerId}/detail")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(summary = "Get aging drill-down detail", description = "Returns invoice list for a specific customer and aging bucket.")
    public ResponseEntity<Page<ARAgingDrillDownDTO>> getDrillDownDetail(
            @Parameter(description = "Customer ID") @PathVariable Long customerId,
            @Parameter(description = "Aging bucket key: CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90") @RequestParam String agingBucketKey,
            @Parameter(description = "As-of date for aging calculation (defaults to today)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ARAgingDrillDownDTO> result = arAgingService.getDrillDownDetail(customerId, agingBucketKey, asOfDate,
                pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Get detailed invoice information with payment history.
     * Used for read-only invoice view from aging report.
     * Requires ADMIN, CFO, CHIEF_ACCOUNTANT, or ACCOUNTANT role.
     *
     * @param invoiceId invoice ID
     * @return invoice detail with payment history
     */
    @GetMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(summary = "Get invoice detail", description = "Returns detailed invoice information with payment history.")
    public ResponseEntity<ARInvoiceDetailDTO> getInvoiceDetail(
            @Parameter(description = "Invoice ID") @PathVariable UUID invoiceId) {

        ARInvoiceDetailDTO result = arAgingService.getInvoiceDetail(invoiceId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Export AR aging report to Excel or PDF.
     * Requires ADMIN, CFO, or CHIEF_ACCOUNTANT role.
     *
     * @param format     export format (EXCEL or PDF)
     * @param customerId optional customer ID filter
     * @param asOfDate   as-of date for aging calculation
     * @return file download
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Export AR aging report", description = "Exports AR aging report to Excel or PDF format.")
    public ResponseEntity<byte[]> exportReport(
            @Parameter(description = "Export format: EXCEL or PDF") @RequestParam String format,
            @Parameter(description = "Customer ID filter (optional)") @RequestParam(required = false) Long customerId,
            @Parameter(description = "As-of date for aging calculation (defaults to today)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            HttpServletRequest request) {

        byte[] data;
        String filename;
        MediaType mediaType;

        if ("PDF".equalsIgnoreCase(format)) {
            data = exportService.exportToPDF(customerId, asOfDate);
            filename = "ar-aging-report.pdf";
            mediaType = MediaType.APPLICATION_PDF;
        } else {
            data = exportService.exportToExcel(customerId, asOfDate);
            filename = "ar-aging-report.xlsx";
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        // Log export to audit trail
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDate exportDate = asOfDate != null ? asOfDate : LocalDate.now();

        auditService.logARAgingExport(companyId, userId, format, customerId, exportDate, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok().headers(headers).body(data);
    }

    /**
     * Get dashboard metrics for AR aging.
     * Returns total overdue, count, and top overdue customers.
     * Results are cached for 5 minutes.
     * Requires ADMIN, CFO, CHIEF_ACCOUNTANT, or ACCOUNTANT role.
     *
     * @return dashboard metrics
     */
    @GetMapping("/dashboard-metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(summary = "Get dashboard metrics", description = "Returns AR aging dashboard metrics including total overdue and top customers.")
    public ResponseEntity<ARDashboardMetricsDTO> getDashboardMetrics() {
        ARDashboardMetricsDTO metrics = dashboardMetricsService.getDashboardMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/reminder-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Get reminder configuration", description = "Returns AR reminder configuration for current company.")
    public ResponseEntity<ARReminderConfigDTO> getReminderConfig() {
        ARReminderConfigDTO config = reminderService.getConfiguration();
        return ResponseEntity.ok(config);
    }

    @org.springframework.web.bind.annotation.PutMapping("/reminder-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Update reminder configuration", description = "Updates AR reminder configuration for current company.")
    public ResponseEntity<ARReminderConfigDTO> updateReminderConfig(
            @org.springframework.web.bind.annotation.RequestBody ARReminderConfigDTO config) {
        ARReminderConfigDTO updated = reminderService.updateConfiguration(config);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/trigger-reminders")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(summary = "Trigger manual reminders", description = "Manually triggers AR reminders for specified customers/invoices.")
    public ResponseEntity<String> triggerReminders(
            @RequestParam(required = false) java.util.List<Long> customerIds,
            @RequestParam(required = false) java.util.List<UUID> invoiceIds) {
        reminderService.triggerManualReminders(customerIds, invoiceIds);
        return ResponseEntity.ok("Reminders triggered successfully");
    }

    /**
     * Diagnostic endpoint to check authentication and role for AR aging access.
     * This helps debug 403 errors.
     */
    @GetMapping("/debug-auth")
    @Operation(summary = "Debug authentication", description = "Returns current authentication and role information for debugging.")
    public ResponseEntity<java.util.Map<String, Object>> debugAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        
        if (auth == null) {
            response.put("authenticated", false);
            response.put("message", "Not authenticated");
            return ResponseEntity.ok(response);
        }
        
        response.put("authenticated", true);
        response.put("userId", SecurityUtils.getCurrentUserId());
        response.put("principal", auth.getPrincipal());
        response.put("authorities", auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList()));
        response.put("hasAdminRole", SecurityUtils.hasRole("ADMIN"));
        response.put("hasAccountantRole", SecurityUtils.hasRole("ACCOUNTANT"));
        response.put("hasChiefAccountantRole", SecurityUtils.hasRole("CHIEF_ACCOUNTANT"));
        response.put("hasCFORole", SecurityUtils.hasRole("CFO"));
        response.put("canAccessAgingReport", 
            SecurityUtils.hasRole("ADMIN") || 
            SecurityUtils.hasRole("CFO") || 
            SecurityUtils.hasRole("CHIEF_ACCOUNTANT") || 
            SecurityUtils.hasRole("ACCOUNTANT"));
        
        return ResponseEntity.ok(response);
    }
}

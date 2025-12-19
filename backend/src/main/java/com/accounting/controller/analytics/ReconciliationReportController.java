package com.accounting.controller.analytics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.analytics.ReconciliationReportDTO;
import com.accounting.entity.Company;
import com.accounting.repository.CompanyRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.analytics.AnalyticsAuditService;
import com.accounting.service.analytics.AnalyticsAuditService.AuditAction;
import com.accounting.service.analytics.AnalyticsAuditService.ResourceType;
import com.accounting.service.analytics.ReconciliationReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/dashboard/reconciliation")
@Tag(name = "Reconciliation Report", description = "Monthly reconciliation report APIs for auditor review")
public class ReconciliationReportController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationReportController.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "reconciliation_export:";
    private static final int MAX_EXPORTS_PER_HOUR = 10;

    private final ReconciliationReportService reportService;
    private final AnalyticsAuditService auditService;
    private final CompanyRepository companyRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${analytics.reconciliation.rate-limit-window-seconds:3600}")
    private int rateLimitWindowSeconds;

    public ReconciliationReportController(
            ReconciliationReportService reportService,
            AnalyticsAuditService auditService,
            CompanyRepository companyRepository,
            StringRedisTemplate redisTemplate) {
        this.reportService = reportService;
        this.auditService = auditService;
        this.companyRepository = companyRepository;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(
            summary = "Get reconciliation report",
            description = "Generate a monthly reconciliation report comparing MV data with GL totals for auditor review")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Company or period not found")
    })
    public ResponseEntity<?> getReport(
            @Parameter(description = "Accounting period ID", required = true)
            @RequestParam UUID periodId,
            HttpServletRequest httpRequest) {

        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        try {
            ReconciliationReportDTO report = reportService.generateReport(companyId, periodId);

            auditService.logAction(
                    companyId,
                    userId,
                    AuditAction.WIDGET_ACCESSED,
                    ResourceType.DASHBOARD,
                    "RECONCILIATION_REPORT",
                    Map.of(
                            "periodId", periodId.toString(),
                            "status", report.overallStatus(),
                            "lineCount", report.lines().size()),
                    httpRequest);

            log.info("Reconciliation report generated: company={}, period={}, status={}",
                    companyId, periodId, report.overallStatus());

            return ResponseEntity.ok(report);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid reconciliation report request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid request", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to generate reconciliation report: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Report generation failed",
                            "message", "An error occurred while generating the reconciliation report"));
        }
    }

    @GetMapping("/report/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(
            summary = "Export reconciliation report to Excel",
            description = "Export monthly reconciliation report to Excel with company watermark for auditor review")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Company or period not found"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<?> exportReport(
            @Parameter(description = "Accounting period ID", required = true)
            @RequestParam UUID periodId,
            HttpServletRequest httpRequest) {

        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        if (!checkRateLimit(userId)) {
            log.warn("Rate limit exceeded for user {} on reconciliation export", userId);
            auditService.logAction(
                    companyId,
                    userId,
                    AuditAction.EXPORT_TRIGGERED,
                    ResourceType.EXPORT,
                    "RECONCILIATION_REPORT",
                    Map.of(
                            "periodId", periodId.toString(),
                            "status", "RATE_LIMITED"),
                    httpRequest);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded",
                            "message", "Maximum " + MAX_EXPORTS_PER_HOUR + " exports per hour allowed"));
        }

        try {
            Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new IllegalStateException("Company not found"));

            byte[] exportData = reportService.exportToExcel(companyId, periodId);

            String filename = String.format(
                    "%s_reconciliation_%s.xlsx",
                    company.getCode(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            auditService.logAction(
                    companyId,
                    userId,
                    AuditAction.EXPORT_TRIGGERED,
                    ResourceType.EXPORT,
                    "RECONCILIATION_REPORT",
                    Map.of(
                            "periodId", periodId.toString(),
                            "format", "EXCEL",
                            "fileSize", exportData.length,
                            "filename", filename),
                    httpRequest);

            log.info("Reconciliation report exported: company={}, period={}, file={}, size={}",
                    companyId, periodId, filename, exportData.length);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(exportData.length);

            return new ResponseEntity<>(exportData, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid reconciliation export request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid request", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to export reconciliation report: {}", e.getMessage(), e);

            auditService.logAction(
                    companyId,
                    userId,
                    AuditAction.EXPORT_TRIGGERED,
                    ResourceType.EXPORT,
                    "RECONCILIATION_REPORT",
                    Map.of(
                            "periodId", periodId.toString(),
                            "status", "FAILED",
                            "error", e.getMessage()),
                    httpRequest);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Export failed",
                            "message", "An error occurred while exporting the reconciliation report"));
        }
    }

    private boolean checkRateLimit(Long userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        String currentCount = redisTemplate.opsForValue().get(key);

        if (currentCount == null) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(rateLimitWindowSeconds));
            return true;
        }

        int count = Integer.parseInt(currentCount);
        if (count >= MAX_EXPORTS_PER_HOUR) {
            return false;
        }

        redisTemplate.opsForValue().increment(key);
        return true;
    }
}

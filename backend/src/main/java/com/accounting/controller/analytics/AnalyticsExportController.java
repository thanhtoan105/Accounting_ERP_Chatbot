package com.accounting.controller.analytics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.analytics.ExportMetadata;
import com.accounting.dto.analytics.ExportRequest;
import com.accounting.dto.analytics.ExportResponse;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.analytics.AnalyticsAuditService;
import com.accounting.service.analytics.AnalyticsAuditService.AuditAction;
import com.accounting.service.analytics.AnalyticsAuditService.ResourceType;
import com.accounting.service.analytics.AnalyticsExportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics Export", description = "Analytics Dashboard Export APIs for TT200 compliance")
public class AnalyticsExportController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsExportController.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "analytics_export:";
    private static final int MAX_EXPORTS_PER_HOUR = 10;

    private final AnalyticsExportService exportService;
    private final AnalyticsAuditService auditService;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${analytics.export.rate-limit-window-seconds:3600}")
    private int rateLimitWindowSeconds;

    public AnalyticsExportController(
            AnalyticsExportService exportService,
            AnalyticsAuditService auditService,
            CompanyRepository companyRepository,
            UserRepository userRepository,
            StringRedisTemplate redisTemplate) {
        this.exportService = exportService;
        this.auditService = auditService;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/dashboard/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(
            summary = "Export analytics dashboard",
            description = "Export dashboard data to Excel or PDF format with TT200 compliance watermarks")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Export generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<?> exportDashboard(@Valid @RequestBody ExportRequest request, HttpServletRequest httpRequest) {
        Long companyIdLong = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        if (!checkRateLimit(userId)) {
            log.warn("Rate limit exceeded for user {} on analytics export", userId);
            auditService.logAction(
                    companyIdLong,
                    userId,
                    AuditAction.EXPORT_TRIGGERED,
                    ResourceType.EXPORT,
                    request.dashboardType(),
                    Map.of("status", "RATE_LIMITED", "format", request.format()),
                    httpRequest);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "Rate limit exceeded",
                            "message", "Maximum " + MAX_EXPORTS_PER_HOUR + " exports per hour. Please try again later.",
                            "retryAfterSeconds", rateLimitWindowSeconds));
        }

        try {
            Company company = companyRepository
                    .findById(companyIdLong)
                    .orElseThrow(() -> new IllegalStateException("Company not found"));
            User user = userRepository
                    .findById(userId)
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            byte[] exportData;
            String contentType;
            String fileExtension;

            if ("EXCEL".equalsIgnoreCase(request.format())) {
                exportData = exportService.exportDashboardToExcel(companyIdLong, request.periodId(), request.dashboardType());
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                fileExtension = "xlsx";
            } else {
                exportData = exportService.exportDashboardToPdf(companyIdLong, request.periodId(), request.dashboardType());
                contentType = "application/pdf";
                fileExtension = "pdf";
            }

            String filename = String.format(
                    "%s_%s_%s.%s",
                    company.getCode(),
                    request.dashboardType().toLowerCase(),
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
                    fileExtension);

            auditService.logAction(
                    companyIdLong,
                    userId,
                    AuditAction.EXPORT_TRIGGERED,
                    ResourceType.EXPORT,
                    request.dashboardType(),
                    Map.of(
                            "format", request.format(),
                            "periodId", request.periodId().toString(),
                            "fileSize", exportData.length,
                            "filename", filename),
                    httpRequest);

            log.info(
                    "Analytics export completed: user={}, company={}, dashboard={}, format={}, size={}",
                    userId,
                    companyIdLong,
                    request.dashboardType(),
                    request.format(),
                    exportData.length);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(exportData.length);

            return new ResponseEntity<>(exportData, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            log.error("Invalid export request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid request", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to generate export: {}", e.getMessage(), e);
            auditService.logAction(
                    companyIdLong,
                    userId,
                    AuditAction.EXPORT_TRIGGERED,
                    ResourceType.EXPORT,
                    request.dashboardType(),
                    Map.of("status", "FAILED", "error", e.getMessage()),
                    httpRequest);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Export failed", "message", "An error occurred while generating the export"));
        }
    }

    @GetMapping("/dashboard/export/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Download exported file", description = "Download a previously generated export file by job ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File downloaded successfully"),
        @ApiResponse(responseCode = "404", description = "Export job not found or expired"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<?> downloadExport(@PathVariable String jobId, HttpServletRequest httpRequest) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        ExportResponse exportStatus = exportService.getExportStatus(jobId);

        if (exportStatus == null) {
            return ResponseEntity.notFound().build();
        }

        if (!"COMPLETED".equals(exportStatus.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "jobId", jobId,
                            "status", exportStatus.status(),
                            "message", "Export is still being processed"));
        }

        auditService.logAction(
                companyId,
                userId,
                AuditAction.EXPORT_TRIGGERED,
                ResourceType.EXPORT,
                jobId,
                Map.of("action", "DOWNLOAD"),
                httpRequest);

        return ResponseEntity.ok(exportStatus);
    }

    @GetMapping("/dashboard/export/{jobId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Get export job status", description = "Check the status of an async export job")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Export job not found")
    })
    public ResponseEntity<ExportResponse> getExportStatus(@PathVariable String jobId) {
        ExportResponse status = exportService.getExportStatus(jobId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/dashboard/export/metadata")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Get export metadata template", description = "Get the metadata structure for exports")
    public ResponseEntity<ExportMetadata> getExportMetadataTemplate() {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId).orElse(null);
        String username = user != null ? user.getFullName() : "Unknown";

        ExportMetadata template = new ExportMetadata(
                companyId,
                null,
                "FINANCIAL_OVERVIEW",
                "EXCEL",
                0,
                LocalDateTime.now(),
                username,
                "CONFIDENTIAL - Generated by " + username);

        return ResponseEntity.ok(template);
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

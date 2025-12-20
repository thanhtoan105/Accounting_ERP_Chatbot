package com.accounting.controller.dashboard;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.dashboard.DashboardAuditLog;
import com.accounting.entity.dashboard.DashboardETLRun;
import com.accounting.entity.dashboard.DashboardFreshness;
import com.accounting.entity.dashboard.FreshnessLevel;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.dashboard.DashboardAuditLogRepository;
import com.accounting.repository.dashboard.DashboardFreshnessRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.analytics.AnalyticsWidgetService;
import com.accounting.service.analytics.AnalyticsWidgetService.PeriodSummaryData;
import com.accounting.service.analytics.ETLPipelineService;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "dashboard:refresh:ratelimit:";

    private final ETLPipelineService etlPipelineService;
    private final DashboardFreshnessRepository freshnessRepository;
    private final DashboardAuditLogRepository auditLogRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final StringRedisTemplate redisTemplate;
    private final AnalyticsWidgetService analyticsWidgetService;

    @Value("${dashboard.refresh.rate-limit-seconds:60}")
    private int rateLimitSeconds;

    public DashboardController(
            ETLPipelineService etlPipelineService,
            DashboardFreshnessRepository freshnessRepository,
            DashboardAuditLogRepository auditLogRepository,
            AccountingPeriodRepository accountingPeriodRepository,
            StringRedisTemplate redisTemplate,
            AnalyticsWidgetService analyticsWidgetService) {
        this.etlPipelineService = etlPipelineService;
        this.freshnessRepository = freshnessRepository;
        this.auditLogRepository = auditLogRepository;
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.redisTemplate = redisTemplate;
        this.analyticsWidgetService = analyticsWidgetService;
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
    public ResponseEntity<RefreshResponse> triggerRefresh() {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        logAuditEvent(companyId, userId, "REFRESH_ATTEMPT", "ETL_JOB", null);

        if (!checkRateLimit(userId)) {
            logAuditEvent(companyId, userId, "REFRESH_RATE_LIMITED", "ETL_JOB", null);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RefreshResponse(null, "RATE_LIMITED",
                            "Rate limit exceeded. Please wait before refreshing again."));
        }

        String instanceId = getInstanceId();
        if (!etlPipelineService.acquireLock(companyId, instanceId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new RefreshResponse(null, "IN_PROGRESS",
                            "A refresh is already in progress. Please wait."));
        }

        try {
            DashboardETLRun etlRun = etlPipelineService.refreshMaterializedViewsManual(companyId, userId);

            logAuditEvent(companyId, userId, "REFRESH_COMPLETED", "ETL_JOB", etlRun.getId().toString());

            return ResponseEntity.accepted()
                    .body(new RefreshResponse(
                            etlRun.getId(),
                            etlRun.getStatus().name(),
                            "Refresh job started successfully"));

        } finally {
            etlPipelineService.releaseLock(companyId, instanceId);
        }
    }

    @GetMapping("/etl/status/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    public ResponseEntity<ETLStatusResponse> getETLStatus(@PathVariable UUID jobId) {
        DashboardETLRun etlRun = etlPipelineService.getETLRunStatus(jobId);

        if (etlRun == null) {
            return ResponseEntity.notFound().build();
        }

        Long companyId = CompanyContext.getCompanyId();
        if (!etlRun.getCompanyId().equals(companyId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(new ETLStatusResponse(
                etlRun.getId(),
                etlRun.getStatus().name(),
                etlRun.getStartedAt(),
                etlRun.getCompletedAt(),
                etlRun.getDurationMs(),
                etlRun.getRowsProcessed(),
                etlRun.getErrorMessage()));
    }

    @GetMapping("/freshness")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    public ResponseEntity<FreshnessResponse> getFreshness() {
        Long companyId = CompanyContext.getCompanyId();
        return buildFreshnessResponse(companyId);
    }

    @GetMapping("/freshness/{companyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FreshnessResponse> getFreshnessForCompany(@PathVariable Long companyId) {
        return buildFreshnessResponse(companyId);
    }

    private ResponseEntity<FreshnessResponse> buildFreshnessResponse(Long companyId) {
        Optional<DashboardFreshness> freshnessOpt = freshnessRepository.findByCompanyId(companyId);
        
        Optional<AccountingPeriod> currentPeriodOpt = accountingPeriodRepository.findCurrentPeriodByCompanyId(companyId);
        boolean periodLocked = currentPeriodOpt.map(p -> p.getStatus() == PeriodStatus.CLOSED).orElse(false);
        String currentPeriodId = currentPeriodOpt.map(p -> p.getId().toString()).orElse(null);
        boolean canManualRefresh = !periodLocked;

        if (freshnessOpt.isEmpty()) {
            return ResponseEntity.ok(new FreshnessResponse(
                    FreshnessLevel.RED,
                    null,
                    "No data available. Dashboard has never been refreshed.",
                    0,
                    periodLocked,
                    currentPeriodId,
                    null,
                    false,
                    canManualRefresh));
        }

        DashboardFreshness freshness = freshnessOpt.get();
        freshness.updateFreshnessLevel();

        long minutesSinceRefresh = freshness.getLastSuccessfulRefresh() != null
                ? Duration.between(freshness.getLastSuccessfulRefresh(), Instant.now()).toMinutes()
                : -1;

        return ResponseEntity.ok(new FreshnessResponse(
                freshness.getFreshnessLevel(),
                freshness.getLastSuccessfulRefresh(),
                getFreshnessMessage(freshness.getFreshnessLevel(), minutesSinceRefresh),
                freshness.getConsecutiveFailures(),
                periodLocked,
                currentPeriodId,
                null,
                periodLocked,
                canManualRefresh));
    }

    private boolean checkRateLimit(Long userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        Boolean wasAbsent = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(rateLimitSeconds));
        return Boolean.TRUE.equals(wasAbsent);
    }

    private void logAuditEvent(Long companyId, Long userId, String action, String resourceType, String resourceId) {
        try {
            DashboardAuditLog log = DashboardAuditLog.create(companyId, userId, action, resourceType);
            log.setResourceId(resourceId);
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to log audit event: {}", e.getMessage());
        }
    }

    private String getFreshnessMessage(FreshnessLevel level, long minutesSinceRefresh) {
        return switch (level) {
            case GREEN -> "Data is fresh (updated " + minutesSinceRefresh + " minutes ago)";
            case YELLOW -> "Data may be slightly stale (" + minutesSinceRefresh + " minutes since last update)";
            case RED -> minutesSinceRefresh >= 0
                    ? "Data is stale (" + minutesSinceRefresh + " minutes since last update)"
                    : "No refresh data available";
        };
    }

    private String getInstanceId() {
        return System.getenv().getOrDefault("HOSTNAME", "local-" + ProcessHandle.current().pid());
    }

    @GetMapping("/period-summary/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    public ResponseEntity<PeriodSummaryResponse> getPeriodSummary(@PathVariable Long periodId) {
        try {
            PeriodSummaryData data = analyticsWidgetService.getPeriodSummary(periodId);
            return ResponseEntity.ok(new PeriodSummaryResponse(
                    data.periodId(),
                    data.totalRevenue(),
                    data.totalExpense(),
                    data.totalRevenue().subtract(data.totalExpense()),
                    data.arBalance(),
                    data.apBalance(),
                    data.cashBalance(),
                    data.voucherCount(),
                    data.periodStart(),
                    data.periodEnd()));
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record RefreshResponse(UUID jobId, String status, String message) {}

    public record ETLStatusResponse(
            UUID jobId,
            String status,
            Instant startedAt,
            Instant completedAt,
            Long durationMs,
            Integer rowsProcessed,
            String errorMessage) {}

    public record FreshnessResponse(
            FreshnessLevel level,
            Instant lastRefresh,
            String message,
            Integer consecutiveFailures,
            Boolean periodLocked,
            String currentPeriodId,
            String dataAsOfPeriodId,
            Boolean dataAsOfPeriodLocked,
            Boolean canManualRefresh) {}

    public record PeriodSummaryResponse(
            Long periodId,
            BigDecimal totalRevenue,
            BigDecimal totalExpense,
            BigDecimal netIncome,
            BigDecimal arBalance,
            BigDecimal apBalance,
            BigDecimal cashBalance,
            Integer voucherCount,
            java.time.LocalDate periodStart,
            java.time.LocalDate periodEnd) {}
}

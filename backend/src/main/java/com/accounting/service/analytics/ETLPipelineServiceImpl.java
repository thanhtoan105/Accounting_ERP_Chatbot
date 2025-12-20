package com.accounting.service.analytics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.analytics.DashboardReconciliationReport;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.dashboard.DashboardETLRun;
import com.accounting.entity.dashboard.DashboardFreshness;
import com.accounting.entity.dashboard.ETLJobStatus;
import com.accounting.entity.dashboard.ETLTriggerType;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.dashboard.DashboardETLRunRepository;
import com.accounting.repository.dashboard.DashboardFreshnessRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ETLPipelineServiceImpl implements ETLPipelineService {

    private static final Logger logger = LoggerFactory.getLogger(ETLPipelineServiceImpl.class);
    private static final String JOB_NAME = "DASHBOARD_MV_REFRESH";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DashboardETLRunRepository etlRunRepository;
    private final DashboardFreshnessRepository freshnessRepository;
    private final AccountingPeriodRepository periodRepository;
    private final AnalyticsCacheService analyticsCacheService;
    private final ETLAlertService etlAlertService;
    private final MaterializedViewRefreshService materializedViewRefreshService;
    private final DashboardReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;
    private final AnalyticsCacheKeyGenerator cacheKeyGenerator;

    @Value("${dashboard.etl.lock-ttl-seconds:300}")
    private int lockTtlSeconds;

    public ETLPipelineServiceImpl(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            DashboardETLRunRepository etlRunRepository,
            DashboardFreshnessRepository freshnessRepository,
            AccountingPeriodRepository periodRepository,
            AnalyticsCacheService analyticsCacheService,
            ETLAlertService etlAlertService,
            MaterializedViewRefreshService materializedViewRefreshService,
            DashboardReconciliationService reconciliationService,
            ObjectMapper objectMapper,
            AnalyticsCacheKeyGenerator cacheKeyGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.etlRunRepository = etlRunRepository;
        this.freshnessRepository = freshnessRepository;
        this.periodRepository = periodRepository;
        this.analyticsCacheService = analyticsCacheService;
        this.etlAlertService = etlAlertService;
        this.materializedViewRefreshService = materializedViewRefreshService;
        this.reconciliationService = reconciliationService;
        this.objectMapper = objectMapper;
        this.cacheKeyGenerator = cacheKeyGenerator;
    }

    @Override
    @Transactional
    public DashboardETLRun refreshMaterializedViews(Long companyId) {
        return executeRefresh(companyId, ETLTriggerType.SCHEDULED, null);
    }

    @Override
    @Transactional
    public DashboardETLRun refreshMaterializedViewsManual(Long companyId, Long triggeredByUserId) {
        return executeRefresh(companyId, ETLTriggerType.MANUAL, triggeredByUserId);
    }

    private DashboardETLRun executeRefresh(Long companyId, ETLTriggerType triggerType, Long userId) {
        DashboardETLRun etlRun = createETLRun(companyId, triggerType, userId);
        logger.info("Starting ETL refresh for company {} (job: {})", companyId, etlRun.getId());

        try {
            UUID currentMaxPostedVoucherId = getMaxPostedVoucherId(companyId);
            DashboardFreshness freshness = freshnessRepository.findByCompanyId(companyId).orElse(null);
            UUID previousLastPostingId = freshness != null ? freshness.getLastPostedVoucherId() : null;

            if (currentMaxPostedVoucherId != null
                    && previousLastPostingId != null
                    && Objects.equals(currentMaxPostedVoucherId, previousLastPostingId)) {
                logger.info("Skipping ETL refresh for company {} - no new posted entries (last_posting_id: {})",
                        companyId, currentMaxPostedVoucherId);

                etlRun.setStatus(ETLJobStatus.SKIPPED);
                etlRun.setCompletedAt(Instant.now());
                etlRun.setDurationMs(Duration.between(etlRun.getStartedAt(), Instant.now()).toMillis());
                etlRun.setMetadata(buildMetadataJson(currentMaxPostedVoucherId, previousLastPostingId, true));
                etlRunRepository.save(etlRun);

                return etlRun;
            }

            etlRun.setStatus(ETLJobStatus.RUNNING);
            etlRunRepository.save(etlRun);

            int rowsProcessed = materializedViewRefreshService.refreshAllMaterializedViews(companyId);

            IntegrityCheckResult integrityResult = runIntegrityChecks(companyId);
            if (!integrityResult.passed()) {
                logger.warn("Integrity check failed for company {}: {}", companyId, integrityResult.errorMessage());
            }

            ReconciliationCheckResult reconciliationResult = runReconciliationChecks(companyId);
            
            if (!reconciliationResult.passed() && !reconciliationResult.skipped()) {
                etlRun.setStatus(ETLJobStatus.COMPLETED_WITH_WARNINGS);
                etlRun.setCompletedAt(Instant.now());
                etlRun.setDurationMs(Duration.between(etlRun.getStartedAt(), Instant.now()).toMillis());
                etlRun.setRowsProcessed(rowsProcessed);
                etlRun.setErrorMessage("Reconciliation failed: " + reconciliationResult.message());
                etlRun.setMetadata(buildMetadataJson(currentMaxPostedVoucherId, previousLastPostingId, false));
                etlRunRepository.save(etlRun);
                
                updateFreshnessStatus(companyId, etlRun.getId(), true, currentMaxPostedVoucherId);
                analyticsCacheService.invalidateCompanyWidgetCaches(companyId);
                
                logger.warn("ETL refresh completed with reconciliation warnings for company {} in {}ms",
                        companyId, etlRun.getDurationMs());
                
                return etlRun;
            }

            etlRun.markCompleted(rowsProcessed);
            etlRun.setMetadata(buildMetadataJson(currentMaxPostedVoucherId, previousLastPostingId, false));
            etlRunRepository.save(etlRun);

            updateFreshnessStatus(companyId, etlRun.getId(), true, currentMaxPostedVoucherId);
            analyticsCacheService.invalidateCompanyWidgetCaches(companyId);

            logger.info("ETL refresh completed for company {} in {}ms, {} rows processed",
                    companyId, etlRun.getDurationMs(), rowsProcessed);

            return etlRun;

        } catch (Exception e) {
            logger.error("ETL refresh failed for company {}: {}", companyId, e.getMessage());

            etlRun.markFailed(e.getMessage(), getStackTrace(e));
            etlRunRepository.save(etlRun);

            updateFreshnessStatus(companyId, etlRun.getId(), false, null);
            etlAlertService.alertOnJobFailure(etlRun);

            return etlRun;
        }
    }

    @Override
    public IntegrityCheckResult runIntegrityChecks(Long companyId) {
        try {
            Boolean balanced = jdbcTemplate.queryForObject(
                    """
                    SELECT ABS(SUM(debit_amount) - SUM(credit_amount)) < 0.01
                    FROM journal_entries je
                    INNER JOIN vouchers v ON je.voucher_id = v.id
                    WHERE je.company_id = ? AND v.status = 'posted'
                    """,
                    Boolean.class,
                    companyId);

            if (balanced == null || !balanced) {
                return new IntegrityCheckResult(false, false, 0, "Debit/Credit imbalance detected");
            }

            Integer orphanCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM journal_entries je
                    LEFT JOIN vouchers v ON je.voucher_id = v.id
                    WHERE je.company_id = ? AND v.id IS NULL
                    """,
                    Integer.class,
                    companyId);

            if (orphanCount != null && orphanCount > 0) {
                logger.warn("Found {} orphaned journal entries for company {}", orphanCount, companyId);
                return IntegrityCheckResult.withOrphans(orphanCount);
            }

            return IntegrityCheckResult.success();

        } catch (Exception e) {
            logger.error("Integrity check failed for company {}: {}", companyId, e.getMessage());
            return IntegrityCheckResult.failure("Integrity check error: " + e.getMessage());
        }
    }

    private ReconciliationCheckResult runReconciliationChecks(Long companyId) {
        try {
            AccountingPeriod currentPeriod = periodRepository.findCurrentPeriodByCompanyId(companyId)
                    .orElse(null);

            if (currentPeriod == null) {
                logger.debug("No current period found for company {}, skipping reconciliation checks", companyId);
                return ReconciliationCheckResult.skipped("No current period found");
            }

            UUID periodId = currentPeriod.getId();
            DashboardReconciliationReport report = reconciliationService.runFullReconciliation(companyId, periodId);

            if (!report.allPassed()) {
                String failedChecks = report.results().stream()
                        .filter(r -> !r.passed())
                        .map(r -> r.checkType() + " variance=" + r.variance())
                        .toList()
                        .toString();
                
                logger.warn("Dashboard reconciliation {} for company {} period {}: {}",
                        report.overallStatus(), companyId, periodId, failedChecks);
                
                etlAlertService.alertOnReconciliationFailure(companyId, periodId, report);
                
                return ReconciliationCheckResult.failed(failedChecks, report);
            }
            
            logger.info("Dashboard reconciliation PASSED for company {} period {}", companyId, periodId);
            return ReconciliationCheckResult.passed(report);
            
        } catch (Exception e) {
            logger.warn("Reconciliation check failed for company {} (error): {}",
                    companyId, e.getMessage());
            return ReconciliationCheckResult.error(e.getMessage());
        }
    }
    
    public record ReconciliationCheckResult(
            boolean passed,
            boolean skipped,
            String message,
            DashboardReconciliationReport report
    ) {
        public static ReconciliationCheckResult passed(DashboardReconciliationReport report) {
            return new ReconciliationCheckResult(true, false, "All reconciliation checks passed", report);
        }
        
        public static ReconciliationCheckResult failed(String message, DashboardReconciliationReport report) {
            return new ReconciliationCheckResult(false, false, message, report);
        }
        
        public static ReconciliationCheckResult skipped(String reason) {
            return new ReconciliationCheckResult(true, true, reason, null);
        }
        
        public static ReconciliationCheckResult error(String errorMessage) {
            return new ReconciliationCheckResult(false, false, "Error: " + errorMessage, null);
        }
    }

    @Override
    @Transactional
    public void updateFreshnessStatus(Long companyId, UUID etlRunId, boolean success, UUID lastPostedVoucherId) {
        DashboardFreshness freshness = freshnessRepository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    DashboardFreshness newFreshness = new DashboardFreshness();
                    newFreshness.setCompanyId(companyId);
                    return newFreshness;
                });

        if (success) {
            freshness.recordSuccess(etlRunId, Instant.now());
            if (lastPostedVoucherId != null) {
                freshness.setLastPostedVoucherId(lastPostedVoucherId);
            }
        } else {
            freshness.recordFailure();
        }

        freshnessRepository.save(freshness);
    }

    @Override
    public DashboardETLRun getETLRunStatus(UUID jobId) {
        return etlRunRepository.findById(jobId).orElse(null);
    }

    @Override
    public boolean acquireLock(Long companyId, String instanceId) {
        String lockKey = cacheKeyGenerator.etlLock(companyId);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, instanceId, Duration.ofSeconds(lockTtlSeconds));

        if (Boolean.TRUE.equals(acquired)) {
            logger.debug("Acquired ETL lock for company {} by instance {}", companyId, instanceId);
            return true;
        }

        String currentHolder = redisTemplate.opsForValue().get(lockKey);
        logger.debug("ETL lock for company {} held by {}", companyId, currentHolder);
        return false;
    }

    @Override
    public void releaseLock(Long companyId, String instanceId) {
        String lockKey = cacheKeyGenerator.etlLock(companyId);
        String currentHolder = redisTemplate.opsForValue().get(lockKey);

        if (instanceId.equals(currentHolder)) {
            redisTemplate.delete(lockKey);
            logger.debug("Released ETL lock for company {} by instance {}", companyId, instanceId);
        }
    }

    private DashboardETLRun createETLRun(Long companyId, ETLTriggerType triggerType, Long userId) {
        DashboardETLRun etlRun = new DashboardETLRun();
        etlRun.setCompanyId(companyId);
        etlRun.setJobName(JOB_NAME);
        etlRun.setStatus(ETLJobStatus.PENDING);
        etlRun.setTriggeredBy(triggerType);
        etlRun.setTriggeredByUserId(userId);
        etlRun.setStartedAt(Instant.now());
        return etlRunRepository.save(etlRun);
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        return trace.length() > 4000 ? trace.substring(0, 4000) : trace;
    }

    private UUID getMaxPostedVoucherId(Long companyId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT max(id) FROM vouchers v
                WHERE v.company_id = ?
                  AND v.status = 'posted'
                  AND v.reversal_of IS NULL
                  AND NOT EXISTS (SELECT 1 FROM vouchers rv WHERE rv.reversal_of = v.id)
                """,
                UUID.class,
                companyId);
    }

    private String buildMetadataJson(UUID currentMaxPostedVoucherId, UUID previousLastPostingId, boolean skipped) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("last_posting_id", currentMaxPostedVoucherId != null ? currentMaxPostedVoucherId.toString() : null);
            metadata.put("as_of_timestamp", Instant.now().toString());
            metadata.put("previous_last_posting_id", previousLastPostingId != null ? previousLastPostingId.toString() : null);
            metadata.put("skipped_due_to_no_changes", skipped);
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            logger.error("Failed to build metadata JSON: {}", e.getMessage());
            return null;
        }
    }
}

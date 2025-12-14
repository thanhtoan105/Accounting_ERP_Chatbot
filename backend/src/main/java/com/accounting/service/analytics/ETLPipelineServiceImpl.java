package com.accounting.service.analytics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.dashboard.DashboardETLRun;
import com.accounting.entity.dashboard.DashboardFreshness;
import com.accounting.entity.dashboard.ETLJobStatus;
import com.accounting.entity.dashboard.ETLTriggerType;
import com.accounting.repository.dashboard.DashboardETLRunRepository;
import com.accounting.repository.dashboard.DashboardFreshnessRepository;

@Service
public class ETLPipelineServiceImpl implements ETLPipelineService {

    private static final Logger logger = LoggerFactory.getLogger(ETLPipelineServiceImpl.class);
    private static final String LOCK_KEY_PREFIX = "etl:lock:company:";
    private static final String JOB_NAME = "DASHBOARD_MV_REFRESH";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DashboardETLRunRepository etlRunRepository;
    private final DashboardFreshnessRepository freshnessRepository;

    @Value("${dashboard.etl.lock-ttl-seconds:300}")
    private int lockTtlSeconds;

    public ETLPipelineServiceImpl(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            DashboardETLRunRepository etlRunRepository,
            DashboardFreshnessRepository freshnessRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.etlRunRepository = etlRunRepository;
        this.freshnessRepository = freshnessRepository;
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
        return executeRefreshWithRetry(companyId, triggerType, userId, 3);
    }

    private DashboardETLRun executeRefreshWithRetry(Long companyId, ETLTriggerType triggerType, Long userId, int maxRetries) {
        String instanceId = getInstanceId();
        DashboardETLRun etlRun = createETLRun(companyId, triggerType, userId);

        logger.info("Starting ETL refresh for company {} (job: {})", companyId, etlRun.getId());

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                etlRun.setStatus(ETLJobStatus.RUNNING);
                etlRun.setRetryCount(attempt - 1);
                etlRunRepository.save(etlRun);

                int rowsProcessed = refreshAllMaterializedViews();

                IntegrityCheckResult integrityResult = runIntegrityChecks(companyId);
                if (!integrityResult.passed()) {
                    logger.warn("Integrity check failed for company {}: {}", companyId, integrityResult.errorMessage());
                }

                etlRun.markCompleted(rowsProcessed);
                etlRunRepository.save(etlRun);

                updateFreshnessStatus(companyId, etlRun.getId(), true);

                logger.info("ETL refresh completed for company {} in {}ms, {} rows processed",
                        companyId, etlRun.getDurationMs(), rowsProcessed);

                return etlRun;

            } catch (Exception e) {
                lastException = e;
                logger.warn("ETL refresh attempt {} failed for company {}: {}", attempt, companyId, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        long backoffMs = (long) (1000 * Math.pow(2, attempt - 1));
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.error("ETL refresh failed for company {} after {} attempts: {}", 
                companyId, maxRetries, lastException != null ? lastException.getMessage() : "unknown error");

        etlRun.markFailed(
                lastException != null ? lastException.getMessage() : "Unknown error",
                lastException != null ? getStackTrace(lastException) : null);
        etlRunRepository.save(etlRun);

        updateFreshnessStatus(companyId, etlRun.getId(), false);

        return etlRun;
    }

    private int refreshAllMaterializedViews() {
        logger.debug("Refreshing materialized views...");

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_revenue_expense");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_ar_ap_aging");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_cash_flow_summary");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_period_summary");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_top_debtors_creditors");

        Integer totalRows = jdbcTemplate.queryForObject(
                "SELECT (SELECT COUNT(*) FROM mv_daily_revenue_expense) + " +
                        "(SELECT COUNT(*) FROM mv_ar_ap_aging) + " +
                        "(SELECT COUNT(*) FROM mv_cash_flow_summary) + " +
                        "(SELECT COUNT(*) FROM mv_period_summary) + " +
                        "(SELECT COUNT(*) FROM mv_top_debtors_creditors)",
                Integer.class);

        return totalRows != null ? totalRows : 0;
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

    @Override
    @Transactional
    public void updateFreshnessStatus(Long companyId, UUID etlRunId, boolean success) {
        DashboardFreshness freshness = freshnessRepository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    DashboardFreshness newFreshness = new DashboardFreshness();
                    newFreshness.setCompanyId(companyId);
                    return newFreshness;
                });

        if (success) {
            freshness.recordSuccess(etlRunId, Instant.now());
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
        String lockKey = LOCK_KEY_PREFIX + companyId;
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
        String lockKey = LOCK_KEY_PREFIX + companyId;
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

    private String getInstanceId() {
        return System.getenv().getOrDefault("HOSTNAME", "local-" + ProcessHandle.current().pid());
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        return trace.length() > 4000 ? trace.substring(0, 4000) : trace;
    }
}

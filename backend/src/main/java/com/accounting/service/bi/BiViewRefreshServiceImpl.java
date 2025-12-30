package com.accounting.service.bi;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.accounting.dto.bi.BiViewRefreshStatus;
import com.accounting.dto.bi.BiViewRefreshStatus.ViewStatus;

@Service
public class BiViewRefreshServiceImpl implements BiViewRefreshService {

    private static final Logger log = LoggerFactory.getLogger(BiViewRefreshServiceImpl.class);

    private static final List<String> BI_VIEWS = List.of(
        "bi_cash_position_daily",
        "bi_ar_aging",
        "bi_ap_aging",
        "bi_revenue_vs_prior",
        "bi_exec_kpis_daily"
    );

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, ViewStatus> viewStatuses = new ConcurrentHashMap<>();
    private volatile Instant lastFullRefresh;

    @Value("${bi.refresh.enabled:true}")
    private boolean refreshEnabled;

    public BiViewRefreshServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${bi.refresh.cron:0 0 2 * * *}")
    @Override
    public void refreshAllViews() {
        if (!refreshEnabled) {
            log.debug("BI view refresh is disabled");
            return;
        }

        log.info("Starting BI materialized views refresh");
        Instant start = Instant.now();
        int successCount = 0;
        int failureCount = 0;

        for (String viewName : BI_VIEWS) {
            try {
                refreshView(viewName);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to refresh {}: {}", viewName, e.getMessage());
                viewStatuses.put(viewName, new ViewStatus(
                    viewName,
                    Instant.now(),
                    null,
                    null,
                    e.getMessage()
                ));
            }
        }

        lastFullRefresh = Instant.now();
        long durationMs = Duration.between(start, Instant.now()).toMillis();

        log.info("BI views refresh completed in {} ms: {} success, {} failed",
            durationMs, successCount, failureCount);
    }

    @Override
    public void refreshView(String viewName) {
        if (!BI_VIEWS.contains(viewName)) {
            throw new IllegalArgumentException("Unknown BI view: " + viewName);
        }

        log.info("Refreshing {}", viewName);
        Instant start = Instant.now();

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName);

        Integer rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + viewName, Integer.class);

        long durationMs = Duration.between(start, Instant.now()).toMillis();

        viewStatuses.put(viewName, new ViewStatus(
            viewName,
            Instant.now(),
            rowCount != null ? rowCount.longValue() : 0L,
            durationMs,
            null
        ));

        log.info("Refreshed {} in {} ms, {} rows", viewName, durationMs, rowCount);
    }

    @Override
    public BiViewRefreshStatus getRefreshStatus() {
        Map<String, ViewStatus> orderedStatuses = new LinkedHashMap<>();
        for (String viewName : BI_VIEWS) {
            ViewStatus status = viewStatuses.get(viewName);
            if (status != null) {
                orderedStatuses.put(viewName, status);
            }
        }
        return new BiViewRefreshStatus(lastFullRefresh, orderedStatuses);
    }
}

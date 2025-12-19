package com.accounting.service.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class MaterializedViewRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(MaterializedViewRefreshService.class);

    private final JdbcTemplate jdbcTemplate;

    public MaterializedViewRefreshService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Retryable(
        retryFor = {DataAccessException.class, RuntimeException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public int refreshAllMaterializedViews(Long companyId) {
        logger.debug("Refreshing materialized views for company {}...", companyId);

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_revenue_expense");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_ar_ap_aging");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_cash_flow_summary");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_period_summary");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_top_debtors_creditors");

        Integer totalRows = jdbcTemplate.queryForObject(
                "SELECT (SELECT COUNT(*) FROM mv_daily_revenue_expense WHERE company_id = ?) + " +
                        "(SELECT COUNT(*) FROM mv_ar_ap_aging WHERE company_id = ?) + " +
                        "(SELECT COUNT(*) FROM mv_cash_flow_summary WHERE company_id = ?) + " +
                        "(SELECT COUNT(*) FROM mv_period_summary WHERE company_id = ?) + " +
                        "(SELECT COUNT(*) FROM mv_top_debtors_creditors WHERE company_id = ?)",
                Integer.class,
                companyId, companyId, companyId, companyId, companyId);

        return totalRows != null ? totalRows : 0;
    }

    @Recover
    public int recoverRefreshMaterializedViews(Exception e, Long companyId) {
        logger.error("All retry attempts exhausted for materialized view refresh, company {}: {}",
                companyId, e.getMessage());
        throw new MaterializedViewRefreshException("Failed to refresh materialized views after retries", e);
    }

    public static class MaterializedViewRefreshException extends RuntimeException {
        public MaterializedViewRefreshException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

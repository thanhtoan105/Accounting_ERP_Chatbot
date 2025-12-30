package com.accounting.service.bi;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.bi.DataQualityIssue;
import com.accounting.dto.bi.DataQualitySummary;

@Service
public class DataQualityServiceImpl implements DataQualityService {

    private static final Logger log = LoggerFactory.getLogger(DataQualityServiceImpl.class);
    private static final BigDecimal BALANCE_TOLERANCE = new BigDecimal("0.01");
    private static final Duration STALE_VIEW_THRESHOLD = Duration.ofHours(24);

    private final JdbcTemplate jdbcTemplate;

    public DataQualityServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Scheduled(cron = "0 0 6 * * *")
    public void runDailyChecks() {
        log.info("Running daily data quality checks for all companies");
        try {
            List<Long> companyIds = getAllCompanyIds();
            for (Long companyId : companyIds) {
                List<DataQualityIssue> issues = runAllChecks(companyId);
                if (!issues.isEmpty()) {
                    log.warn(
                            "Data quality check for company {}: {} issues found (critical={}, warning={})",
                            companyId,
                            issues.size(),
                            issues.stream()
                                    .filter(
                                            i ->
                                                    i.severity()
                                                            == DataQualityIssue.Severity.CRITICAL)
                                    .count(),
                            issues.stream()
                                    .filter(
                                            i ->
                                                    i.severity()
                                                            == DataQualityIssue.Severity.WARNING)
                                    .count());
                } else {
                    log.info("Data quality check for company {}: all checks passed", companyId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to run daily data quality checks", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataQualityIssue> runAllChecks(Long companyId) {
        List<DataQualityIssue> issues = new ArrayList<>();

        issues.addAll(checkMissingCompanyId());
        issues.addAll(checkUnbalancedEntries(companyId));
        issues.addAll(checkOrphanedVoucherLines(companyId));
        issues.addAll(checkStaleViews());
        issues.addAll(checkAgingDataGaps(companyId));

        return issues;
    }

    @Override
    @Transactional(readOnly = true)
    public DataQualitySummary getSummary(Long companyId) {
        return DataQualitySummary.from(runAllChecks(companyId));
    }

    private List<Long> getAllCompanyIds() {
        String sql = "SELECT id FROM companies WHERE deleted = false ORDER BY id";
        return jdbcTemplate.queryForList(sql, Long.class);
    }

    private List<DataQualityIssue> checkMissingCompanyId() {
        List<DataQualityIssue> issues = new ArrayList<>();

        String sql = "SELECT COUNT(*) FROM journal_entries WHERE company_id IS NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);

        if (count != null && count > 0) {
            issues.add(
                    DataQualityIssue.critical(
                            "MISSING_COMPANY_ID",
                            String.format(
                                    "%d journal entries have NULL company_id. "
                                            + "Multi-tenancy data isolation may be compromised.",
                                    count),
                            "journal_entries",
                            null));
        }

        return issues;
    }

    private List<DataQualityIssue> checkUnbalancedEntries(Long companyId) {
        List<DataQualityIssue> issues = new ArrayList<>();

        String sql =
                """
                SELECT je.id, je.voucher_id,
                       SUM(jel.debit_amount) as debits,
                       SUM(jel.credit_amount) as credits,
                       ABS(SUM(jel.debit_amount) - SUM(jel.credit_amount)) as variance
                FROM journal_entries je
                JOIN journal_entry_lines jel ON je.id = jel.journal_entry_id
                WHERE je.company_id = ?
                GROUP BY je.id, je.voucher_id
                HAVING ABS(SUM(jel.debit_amount) - SUM(jel.credit_amount)) > ?
                LIMIT 100
                """;

        List<UnbalancedEntry> unbalanced =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNum) ->
                                new UnbalancedEntry(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("voucher_id", UUID.class),
                                        rs.getBigDecimal("debits"),
                                        rs.getBigDecimal("credits"),
                                        rs.getBigDecimal("variance")),
                        companyId,
                        BALANCE_TOLERANCE);

        for (UnbalancedEntry entry : unbalanced) {
            issues.add(
                    DataQualityIssue.critical(
                            "UNBALANCED_ENTRY",
                            String.format(
                                    "Journal entry %s has unbalanced lines: "
                                            + "debits=%.2f, credits=%.2f, variance=%.2f",
                                    entry.id(),
                                    entry.debits(),
                                    entry.credits(),
                                    entry.variance()),
                            "journal_entries",
                            entry.id()));
        }

        return issues;
    }

    private List<DataQualityIssue> checkOrphanedVoucherLines(Long companyId) {
        List<DataQualityIssue> issues = new ArrayList<>();

        String sql =
                """
                SELECT vl.id, vl.voucher_id
                FROM voucher_lines vl
                LEFT JOIN vouchers v ON vl.voucher_id = v.id
                WHERE v.id IS NULL AND vl.company_id = ?
                LIMIT 100
                """;

        List<OrphanedLine> orphans =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNum) ->
                                new OrphanedLine(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("voucher_id", UUID.class)),
                        companyId);

        for (OrphanedLine orphan : orphans) {
            issues.add(
                    DataQualityIssue.warning(
                            "ORPHANED_VOUCHER_LINE",
                            String.format(
                                    "Voucher line %s references non-existent voucher %s",
                                    orphan.id(), orphan.voucherId()),
                            "voucher_lines",
                            orphan.id()));
        }

        return issues;
    }

    private List<DataQualityIssue> checkStaleViews() {
        List<DataQualityIssue> issues = new ArrayList<>();

        String[] biViews = {
            "bi_cash_position_daily",
            "bi_ar_aging",
            "bi_ap_aging",
            "bi_revenue_vs_prior",
            "bi_exec_kpis_daily"
        };

        for (String viewName : biViews) {
            try {
                String sql =
                        String.format(
                                "SELECT MAX(refreshed_at) FROM %s",
                                viewName.replaceAll("[^a-z_]", ""));
                Timestamp lastRefresh = jdbcTemplate.queryForObject(sql, Timestamp.class);

                if (lastRefresh == null) {
                    issues.add(
                            DataQualityIssue.warning(
                                    "STALE_VIEW",
                                    String.format(
                                            "Materialized view %s has never been refreshed or is"
                                                    + " empty",
                                            viewName),
                                    viewName,
                                    null));
                } else {
                    Duration sinceRefresh =
                            Duration.between(lastRefresh.toInstant(), Instant.now());
                    if (sinceRefresh.compareTo(STALE_VIEW_THRESHOLD) > 0) {
                        issues.add(
                                DataQualityIssue.warning(
                                        "STALE_VIEW",
                                        String.format(
                                                "Materialized view %s was last refreshed %d hours"
                                                        + " ago (threshold: %d hours)",
                                                viewName,
                                                sinceRefresh.toHours(),
                                                STALE_VIEW_THRESHOLD.toHours()),
                                        viewName,
                                        null));
                    }
                }
            } catch (Exception e) {
                issues.add(
                        DataQualityIssue.info(
                                "VIEW_CHECK_FAILED",
                                String.format(
                                        "Could not check view %s: %s", viewName, e.getMessage()),
                                viewName));
            }
        }

        return issues;
    }

    private List<DataQualityIssue> checkAgingDataGaps(Long companyId) {
        List<DataQualityIssue> issues = new ArrayList<>();

        String arSql =
                """
                SELECT COUNT(*) as orphan_count
                FROM bi_ar_aging
                WHERE company_id = ? AND customer_id IS NULL AND total_ar > 0
                """;

        Integer arOrphans = jdbcTemplate.queryForObject(arSql, Integer.class, companyId);
        if (arOrphans != null && arOrphans > 0) {
            issues.add(
                    DataQualityIssue.warning(
                            "AR_AGING_DATA_GAP",
                            String.format(
                                    "%d AR aging records have no customer assigned. "
                                            + "Customer dimension may be incomplete.",
                                    arOrphans),
                            "bi_ar_aging",
                            null));
        }

        String apSql =
                """
                SELECT COUNT(*) as orphan_count
                FROM bi_ap_aging
                WHERE company_id = ? AND supplier_id IS NULL AND total_ap > 0
                """;

        Integer apOrphans = jdbcTemplate.queryForObject(apSql, Integer.class, companyId);
        if (apOrphans != null && apOrphans > 0) {
            issues.add(
                    DataQualityIssue.warning(
                            "AP_AGING_DATA_GAP",
                            String.format(
                                    "%d AP aging records have no supplier assigned. "
                                            + "Supplier dimension may be incomplete.",
                                    apOrphans),
                            "bi_ap_aging",
                            null));
        }

        return issues;
    }

    private record UnbalancedEntry(
            UUID id, UUID voucherId, BigDecimal debits, BigDecimal credits, BigDecimal variance) {}

    private record OrphanedLine(UUID id, UUID voucherId) {}
}

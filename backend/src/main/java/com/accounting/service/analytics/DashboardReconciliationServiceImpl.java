package com.accounting.service.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.analytics.DashboardReconciliationReport;
import com.accounting.dto.analytics.MultiCurrencyReconciliationResult;
import com.accounting.dto.analytics.ReconciliationResult;

@Service
public class DashboardReconciliationServiceImpl implements DashboardReconciliationService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardReconciliationServiceImpl.class);
    private static final BigDecimal TOLERANCE_VND = BigDecimal.ONE;

    private static final String CHECK_TYPE_AR = "AR";
    private static final String CHECK_TYPE_REVENUE = "REVENUE";
    private static final String CHECK_TYPE_CASH = "CASH";

    private final JdbcTemplate jdbcTemplate;

    public DashboardReconciliationServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationResult checkARReconciliation(Long companyId, UUID periodId) {
        logger.debug("Running AR reconciliation for company {} period {}", companyId, periodId);

        BigDecimal mvTotal = getMVARTotal(companyId, periodId);
        BigDecimal glTotal = getGLARTotal(companyId, periodId);

        ReconciliationResult result = ReconciliationResult.of(CHECK_TYPE_AR, mvTotal, glTotal, TOLERANCE_VND);

        if (!result.passed()) {
            logger.warn("AR reconciliation failed for company {} period {}: MV={}, GL={}, variance={}",
                    companyId, periodId, mvTotal, glTotal, result.variance());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationResult checkRevenueReconciliation(Long companyId, UUID periodId) {
        logger.debug("Running Revenue reconciliation for company {} period {}", companyId, periodId);

        BigDecimal mvTotal = getMVRevenueTotal(companyId, periodId);
        BigDecimal glTotal = getGLRevenueTotal(companyId, periodId);

        ReconciliationResult result = ReconciliationResult.of(CHECK_TYPE_REVENUE, mvTotal, glTotal, TOLERANCE_VND);

        if (!result.passed()) {
            logger.warn("Revenue reconciliation failed for company {} period {}: MV={}, GL={}, variance={}",
                    companyId, periodId, mvTotal, glTotal, result.variance());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationResult checkCashReconciliation(Long companyId, UUID periodId) {
        logger.debug("Running Cash reconciliation for company {} period {}", companyId, periodId);

        BigDecimal mvTotal = getMVCashTotal(companyId, periodId);
        BigDecimal glTotal = getGLCashTotal(companyId, periodId);

        ReconciliationResult result = ReconciliationResult.of(CHECK_TYPE_CASH, mvTotal, glTotal, TOLERANCE_VND);

        if (!result.passed()) {
            logger.warn("Cash reconciliation failed for company {} period {}: MV={}, GL={}, variance={}",
                    companyId, periodId, mvTotal, glTotal, result.variance());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardReconciliationReport runFullReconciliation(Long companyId, UUID periodId) {
        logger.info("Running full dashboard reconciliation for company {} period {}", companyId, periodId);

        List<ReconciliationResult> results = List.of(
                checkARReconciliation(companyId, periodId),
                checkRevenueReconciliation(companyId, periodId),
                checkCashReconciliation(companyId, periodId));

        DashboardReconciliationReport report = DashboardReconciliationReport.of(companyId, periodId, results);

        if (report.allPassed()) {
            logger.info("Full reconciliation PASSED for company {} period {}", companyId, periodId);
        } else {
            logger.warn("Full reconciliation {} for company {} period {}: {} of {} checks passed",
                    report.overallStatus(), companyId, periodId,
                    results.stream().filter(ReconciliationResult::passed).count(),
                    results.size());
        }

        return report;
    }

    private BigDecimal getMVARTotal(Long companyId, UUID periodId) {
        String sql = """
                SELECT COALESCE(SUM(total_outstanding), 0)
                FROM mv_ar_ap_aging
                WHERE company_id = ? AND balance_type = 'AR' AND as_of_period_id = ?
                """;
        return queryForBigDecimal(sql, companyId, periodId);
    }

    private BigDecimal getGLARTotal(Long companyId, UUID periodId) {
        String sql = """
                SELECT COALESCE(SUM(je.debit_amount - je.credit_amount), 0)
                FROM journal_entries je
                JOIN vouchers v ON je.voucher_id = v.id
                JOIN chart_of_accounts coa ON je.account_id = coa.id
                WHERE je.company_id = ? AND v.period_id = ? AND v.status = 'posted'
                  AND coa.code LIKE '131%'
                """;
        return queryForBigDecimal(sql, companyId, periodId);
    }

    private BigDecimal getMVRevenueTotal(Long companyId, UUID periodId) {
        String sql = """
                SELECT COALESCE(SUM(revenue), 0)
                FROM mv_daily_revenue_expense
                WHERE company_id = ? AND as_of_period_id = ?
                """;
        return queryForBigDecimal(sql, companyId, periodId);
    }

    private BigDecimal getGLRevenueTotal(Long companyId, UUID periodId) {
        String sql = """
                SELECT COALESCE(SUM(je.credit_amount - je.debit_amount), 0)
                FROM journal_entries je
                JOIN vouchers v ON je.voucher_id = v.id
                JOIN chart_of_accounts coa ON je.account_id = coa.id
                WHERE je.company_id = ? AND v.period_id = ? AND v.status = 'posted'
                  AND (coa.code LIKE '511%' OR coa.code LIKE '512%' OR coa.code LIKE '515%')
                """;
        return queryForBigDecimal(sql, companyId, periodId);
    }

    private BigDecimal getMVCashTotal(Long companyId, UUID periodId) {
        String sql = """
                SELECT COALESCE(SUM(net_flow), 0)
                FROM mv_cash_flow_summary
                WHERE company_id = ? AND as_of_period_id = ?
                """;
        return queryForBigDecimal(sql, companyId, periodId);
    }

    private BigDecimal getGLCashTotal(Long companyId, UUID periodId) {
        String sql = """
                SELECT COALESCE(SUM(je.debit_amount - je.credit_amount), 0)
                FROM journal_entries je
                JOIN vouchers v ON je.voucher_id = v.id
                JOIN chart_of_accounts coa ON je.account_id = coa.id
                WHERE je.company_id = ? AND v.period_id = ? AND v.status = 'posted'
                  AND (coa.code LIKE '111%' OR coa.code LIKE '112%')
                """;
        return queryForBigDecimal(sql, companyId, periodId);
    }

    private BigDecimal queryForBigDecimal(String sql, Long companyId, UUID periodId) {
        BigDecimal result = jdbcTemplate.queryForObject(sql, BigDecimal.class, companyId, periodId);
        return result != null ? result : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public MultiCurrencyReconciliationResult checkARReconciliationByCurrency(Long companyId, UUID periodId) {
        throw new UnsupportedOperationException(
                "Multi-currency reconciliation is not yet implemented. "
                        + "Currency support will be added in a future release (Task 15.3).");
    }

    @Override
    @Transactional(readOnly = true)
    public MultiCurrencyReconciliationResult checkRevenueReconciliationByCurrency(Long companyId, UUID periodId) {
        throw new UnsupportedOperationException(
                "Multi-currency reconciliation is not yet implemented. "
                        + "Currency support will be added in a future release (Task 15.3).");
    }
}

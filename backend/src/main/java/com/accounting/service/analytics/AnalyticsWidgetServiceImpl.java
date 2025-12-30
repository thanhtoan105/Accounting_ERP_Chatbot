package com.accounting.service.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.accounting.security.CompanyContext;

@Service
public class AnalyticsWidgetServiceImpl implements AnalyticsWidgetService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsWidgetServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final AnalyticsCacheService analyticsCacheService;

    public AnalyticsWidgetServiceImpl(
            JdbcTemplate jdbcTemplate,
            AnalyticsCacheService analyticsCacheService) {
        this.jdbcTemplate = jdbcTemplate;
        this.analyticsCacheService = analyticsCacheService;
    }

    @Override
    @Cacheable(value = "analytics-widget",
            key = "'widget:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':revenue-expense:' + #startDate + ':' + #endDate")
    public RevenueExpenseData getRevenueVsExpenses(LocalDate startDate, LocalDate endDate) {
        Long companyId = CompanyContext.getCompanyId();
        logger.debug("Fetching revenue vs expenses for company {} from {} to {}", companyId, startDate, endDate);

        analyticsCacheService.recordCacheMiss("revenue-expense");

        List<DailyRevenueExpense> dailyData = jdbcTemplate.query(
                """
                SELECT transaction_date, revenue, expense
                FROM mv_daily_revenue_expense
                WHERE company_id = ? AND transaction_date BETWEEN ? AND ?
                ORDER BY transaction_date
                """,
                (rs, rowNum) -> new DailyRevenueExpense(
                        rs.getDate("transaction_date").toLocalDate(),
                        rs.getBigDecimal("revenue"),
                        rs.getBigDecimal("expense")),
                companyId, startDate, endDate);

        BigDecimal totalRevenue = dailyData.stream()
                .map(DailyRevenueExpense::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenses = dailyData.stream()
                .map(DailyRevenueExpense::expenses)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RevenueExpenseData(
                totalRevenue,
                totalExpenses,
                totalRevenue.subtract(totalExpenses),
                dailyData);
    }

    @Override
    @Cacheable(value = "analytics-widget",
            key = "'widget:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':ar-ap-balance:' + #asOfDate")
    public ARAPBalanceData getARAPBalances(LocalDate asOfDate) {
        Long companyId = CompanyContext.getCompanyId();
        logger.debug("Fetching AR/AP balances for company {} as of {}", companyId, asOfDate);

        analyticsCacheService.recordCacheMiss("ar-ap-balance");

        var result = jdbcTemplate.queryForMap(
                """
                SELECT
                    COALESCE(SUM(CASE WHEN balance_type = 'AR' THEN total_outstanding ELSE 0 END), 0) as total_ar,
                    COALESCE(SUM(CASE WHEN balance_type = 'AP' THEN total_outstanding ELSE 0 END), 0) as total_ap,
                    COALESCE(SUM(CASE WHEN balance_type = 'AR' THEN bucket_current ELSE 0 END), 0) as ar_current,
                    COALESCE(SUM(CASE WHEN balance_type = 'AR' THEN bucket_1_30 ELSE 0 END), 0) as ar_1_30,
                    COALESCE(SUM(CASE WHEN balance_type = 'AR' THEN bucket_31_60 ELSE 0 END), 0) as ar_31_60,
                    COALESCE(SUM(CASE WHEN balance_type = 'AR' THEN bucket_61_90 ELSE 0 END), 0) as ar_61_90,
                    COALESCE(SUM(CASE WHEN balance_type = 'AR' THEN bucket_over_90 ELSE 0 END), 0) as ar_over_90,
                    COALESCE(SUM(CASE WHEN balance_type = 'AP' THEN bucket_current ELSE 0 END), 0) as ap_current,
                    COALESCE(SUM(CASE WHEN balance_type = 'AP' THEN bucket_1_30 ELSE 0 END), 0) as ap_1_30,
                    COALESCE(SUM(CASE WHEN balance_type = 'AP' THEN bucket_31_60 ELSE 0 END), 0) as ap_31_60,
                    COALESCE(SUM(CASE WHEN balance_type = 'AP' THEN bucket_61_90 ELSE 0 END), 0) as ap_61_90,
                    COALESCE(SUM(CASE WHEN balance_type = 'AP' THEN bucket_over_90 ELSE 0 END), 0) as ap_over_90
                FROM mv_ar_ap_aging
                WHERE company_id = ?
                """,
                companyId);

        BigDecimal totalAR = (BigDecimal) result.get("total_ar");
        BigDecimal totalAP = (BigDecimal) result.get("total_ap");

        Map<String, BigDecimal> arByAgingBucket = Map.of(
                "current", (BigDecimal) result.get("ar_current"),
                "1_30_days", (BigDecimal) result.get("ar_1_30"),
                "31_60_days", (BigDecimal) result.get("ar_31_60"),
                "61_90_days", (BigDecimal) result.get("ar_61_90"),
                "over_90_days", (BigDecimal) result.get("ar_over_90"));

        Map<String, BigDecimal> apByAgingBucket = Map.of(
                "current", (BigDecimal) result.get("ap_current"),
                "1_30_days", (BigDecimal) result.get("ap_1_30"),
                "31_60_days", (BigDecimal) result.get("ap_31_60"),
                "61_90_days", (BigDecimal) result.get("ap_61_90"),
                "over_90_days", (BigDecimal) result.get("ap_over_90"));

        return new ARAPBalanceData(
                totalAR,
                totalAP,
                totalAR.subtract(totalAP),
                arByAgingBucket,
                apByAgingBucket);
    }

    @Override
    @Cacheable(value = "analytics-widget",
            key = "'widget:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':cash-position:' + #asOfDate")
    public CashPositionData getCashPosition(LocalDate asOfDate) {
        Long companyId = CompanyContext.getCompanyId();
        logger.debug("Fetching cash position for company {} as of {}", companyId, asOfDate);

        analyticsCacheService.recordCacheMiss("cash-position");

        var result = jdbcTemplate.queryForMap(
                """
                SELECT
                    COALESCE(SUM(CASE WHEN account_code LIKE '112%' THEN net_flow ELSE 0 END), 0) as cash_in_bank,
                    COALESCE(SUM(CASE WHEN account_code LIKE '111%' THEN net_flow ELSE 0 END), 0) as cash_on_hand,
                    COALESCE(SUM(net_flow), 0) as net_cash_flow
                FROM mv_cash_flow_summary
                WHERE company_id = ?
                """,
                companyId);

        BigDecimal cashInBank = (BigDecimal) result.get("cash_in_bank");
        BigDecimal cashOnHand = (BigDecimal) result.get("cash_on_hand");
        BigDecimal netCashFlow = (BigDecimal) result.get("net_cash_flow");

        return new CashPositionData(
                cashInBank.add(cashOnHand),
                cashInBank,
                cashOnHand,
                netCashFlow);
    }

    @Override
    @Cacheable(value = "analytics-widget",
            key = "'widget:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':top-debtors:' + #asOfDate")
    public List<TopDebtorCreditor> getTop5Debtors(LocalDate asOfDate) {
        Long companyId = CompanyContext.getCompanyId();
        logger.debug("Fetching top 5 debtors for company {} as of {}", companyId, asOfDate);

        analyticsCacheService.recordCacheMiss("top-debtors");

        return jdbcTemplate.query(
                """
                SELECT entity_id, entity_name, entity_code, balance, rank
                FROM mv_top_debtors_creditors
                WHERE company_id = ? AND entity_type = 'DEBTOR'
                ORDER BY rank
                LIMIT 5
                """,
                (rs, rowNum) -> new TopDebtorCreditor(
                        rs.getLong("entity_id"),
                        rs.getString("entity_name"),
                        rs.getString("entity_code"),
                        rs.getBigDecimal("balance"),
                        rs.getInt("rank")),
                companyId);
    }

    @Override
    @Cacheable(value = "analytics-widget",
            key = "'widget:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':top-creditors:' + #asOfDate")
    public List<TopDebtorCreditor> getTop5Creditors(LocalDate asOfDate) {
        Long companyId = CompanyContext.getCompanyId();
        logger.debug("Fetching top 5 creditors for company {} as of {}", companyId, asOfDate);

        analyticsCacheService.recordCacheMiss("top-creditors");

        return jdbcTemplate.query(
                """
                SELECT entity_id, entity_name, entity_code, balance, rank
                FROM mv_top_debtors_creditors
                WHERE company_id = ? AND entity_type = 'CREDITOR'
                ORDER BY rank
                LIMIT 5
                """,
                (rs, rowNum) -> new TopDebtorCreditor(
                        rs.getLong("entity_id"),
                        rs.getString("entity_name"),
                        rs.getString("entity_code"),
                        rs.getBigDecimal("balance"),
                        rs.getInt("rank")),
                companyId);
    }

    @Override
    @Cacheable(value = "analytics-widget",
            key = "'widget:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':period-summary:' + #periodId")
    public PeriodSummaryData getPeriodSummary(java.util.UUID periodId) {
        Long companyId = CompanyContext.getCompanyId();
        logger.debug("Fetching period summary for company {} period {}", companyId, periodId);

        analyticsCacheService.recordCacheMiss("period-summary");

        return jdbcTemplate.queryForObject(
                """
                SELECT period_id, total_revenue, total_expense, ar_balance, ap_balance,
                       cash_balance, voucher_count, period_start, period_end
                FROM mv_period_summary
                WHERE company_id = ? AND period_id = ?
                """,
                (rs, rowNum) -> new PeriodSummaryData(
                        java.util.UUID.fromString(rs.getString("period_id")),
                        rs.getBigDecimal("total_revenue"),
                        rs.getBigDecimal("total_expense"),
                        rs.getBigDecimal("ar_balance"),
                        rs.getBigDecimal("ap_balance"),
                        rs.getBigDecimal("cash_balance"),
                        rs.getInt("voucher_count"),
                        rs.getDate("period_start").toLocalDate(),
                        rs.getDate("period_end").toLocalDate()),
                companyId, periodId);
    }

    @Override
    @Cacheable(value = "analytics-widget",
            key = "'widget:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':cash-trend:' + #startDate + ':' + #endDate")
    public List<CashPositionTrend> getCashPositionTrend(LocalDate startDate, LocalDate endDate) {
        Long companyId = CompanyContext.getCompanyId();
        logger.debug("Fetching cash position trend for company {} from {} to {}", companyId, startDate, endDate);

        analyticsCacheService.recordCacheMiss("cash-trend");

        return jdbcTemplate.query(
                """
                SELECT transaction_date,
                       SUM(net_cash_flow) as daily_cash_flow,
                       SUM(SUM(net_cash_flow)) OVER (ORDER BY transaction_date) as running_balance
                FROM mv_cash_flow_summary
                WHERE company_id = ? AND transaction_date BETWEEN ? AND ?
                GROUP BY transaction_date
                ORDER BY transaction_date
                """,
                (rs, rowNum) -> new CashPositionTrend(
                        rs.getDate("transaction_date").toLocalDate(),
                        rs.getBigDecimal("daily_cash_flow"),
                        rs.getBigDecimal("running_balance")),
                companyId, startDate, endDate);
    }

    @Override
    public AnalyticsCacheService.CacheMetrics getCacheMetrics() {
        return analyticsCacheService.getCacheMetrics();
    }

    @Override
    public AnalyticsCacheService.CacheMetrics getCacheMetricsForWidget(String widgetType) {
        return analyticsCacheService.getCacheMetricsForWidget(widgetType);
    }
}

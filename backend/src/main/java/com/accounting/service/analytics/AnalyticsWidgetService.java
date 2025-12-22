package com.accounting.service.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AnalyticsWidgetService {

    RevenueExpenseData getRevenueVsExpenses(LocalDate startDate, LocalDate endDate);

    ARAPBalanceData getARAPBalances(LocalDate asOfDate);

    CashPositionData getCashPosition(LocalDate asOfDate);

    List<TopDebtorCreditor> getTop5Debtors(LocalDate asOfDate);

    List<TopDebtorCreditor> getTop5Creditors(LocalDate asOfDate);

    PeriodSummaryData getPeriodSummary(java.util.UUID periodId);

    List<CashPositionTrend> getCashPositionTrend(LocalDate startDate, LocalDate endDate);

    AnalyticsCacheService.CacheMetrics getCacheMetrics();

    AnalyticsCacheService.CacheMetrics getCacheMetricsForWidget(String widgetType);

    record RevenueExpenseData(
            BigDecimal totalRevenue,
            BigDecimal totalExpenses,
            BigDecimal netIncome,
            List<DailyRevenueExpense> dailyData) {}

    record DailyRevenueExpense(
            LocalDate date,
            BigDecimal revenue,
            BigDecimal expenses) {}

    record ARAPBalanceData(
            BigDecimal totalAR,
            BigDecimal totalAP,
            BigDecimal netPosition,
            Map<String, BigDecimal> arByAgingBucket,
            Map<String, BigDecimal> apByAgingBucket) {}

    record CashPositionData(
            BigDecimal totalCash,
            BigDecimal cashInBank,
            BigDecimal cashOnHand,
            BigDecimal netCashFlow) {}

    record TopDebtorCreditor(
            Long partnerId,
            String partnerName,
            String partnerCode,
            BigDecimal balance,
            int rank) {}

    record PeriodSummaryData(
            java.util.UUID periodId,
            BigDecimal totalRevenue,
            BigDecimal totalExpense,
            BigDecimal arBalance,
            BigDecimal apBalance,
            BigDecimal cashBalance,
            int voucherCount,
            LocalDate periodStart,
            LocalDate periodEnd) {}

    record CashPositionTrend(
            LocalDate date,
            BigDecimal dailyCashFlow,
            BigDecimal runningBalance) {}
}

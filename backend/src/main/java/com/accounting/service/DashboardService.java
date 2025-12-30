package com.accounting.service;

import java.time.LocalDate;

import com.accounting.dto.dashboard.*;

/**
 * Service for dashboard chart data.
 */
public interface DashboardService {

    /**
     * Get AR aging buckets for pie/bar chart.
     * @param asOfDate as-of date (null = today)
     * @return aging buckets with amounts and percentages
     */
    AgingBucketsDTO getAgingBuckets(LocalDate asOfDate);

    /**
     * Get monthly revenue for line chart.
     * @param months number of months (1-24)
     * @return monthly revenue data
     */
    MonthlyRevenueDTO getMonthlyRevenue(int months);

    /**
     * Get cash flow for stacked bar chart.
     * @param months number of months (1-24)
     * @return cash flow data
     */
    CashFlowDTO getCashFlow(int months);

    /**
     * Get expense breakdown for pie chart.
     * @param startDate period start (null = first of current month)
     * @param endDate period end (null = today)
     * @return expense breakdown by category
     */
    ExpenseBreakdownDTO getExpenseBreakdown(LocalDate startDate, LocalDate endDate);
}

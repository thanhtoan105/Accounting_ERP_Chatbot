package com.accounting.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.dashboard.AgingBucketsDTO;
import com.accounting.dto.dashboard.CashFlowDTO;
import com.accounting.dto.dashboard.ExpenseBreakdownDTO;
import com.accounting.dto.dashboard.MonthlyRevenueDTO;
import com.accounting.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/dashboard/charts")
@Tag(name = "Dashboard Charts", description = "Dashboard chart data endpoints")
public class DashboardChartController {

    private final DashboardService dashboardService;

    public DashboardChartController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/ar-aging")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
    @Operation(
            summary = "Get AR aging buckets",
            description = "Returns accounts receivable aging data grouped by time buckets (current, 1-30 days, 31-60 days, etc.)")
    public ResponseEntity<AgingBucketsDTO> getARAgingBuckets(
            @Parameter(description = "As-of date for aging calculation (defaults to today)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOfDate) {
        AgingBucketsDTO result = dashboardService.getAgingBuckets(asOfDate);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/monthly-revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(
            summary = "Get monthly revenue data",
            description = "Returns monthly revenue data for the specified number of months for chart visualization")
    public ResponseEntity<MonthlyRevenueDTO> getMonthlyRevenue(
            @Parameter(description = "Number of months to retrieve (default 12)")
                    @RequestParam(defaultValue = "12")
                    int months) {
        MonthlyRevenueDTO result = dashboardService.getMonthlyRevenue(months);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(
            summary = "Get cash flow data",
            description = "Returns cash flow data (inflows, outflows, net) for the specified number of months")
    public ResponseEntity<CashFlowDTO> getCashFlow(
            @Parameter(description = "Number of months to retrieve (default 12)")
                    @RequestParam(defaultValue = "12")
                    int months) {
        CashFlowDTO result = dashboardService.getCashFlow(months);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/expense-breakdown")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(
            summary = "Get expense breakdown",
            description = "Returns expense breakdown by category for pie chart visualization")
    public ResponseEntity<ExpenseBreakdownDTO> getExpenseBreakdown(
            @Parameter(description = "Start date for expense period")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @Parameter(description = "End date for expense period")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate) {
        ExpenseBreakdownDTO result = dashboardService.getExpenseBreakdown(startDate, endDate);
        return ResponseEntity.ok(result);
    }
}

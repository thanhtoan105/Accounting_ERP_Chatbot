package com.accounting.service.analytics;

import com.accounting.dto.analytics.ExportResponse;

public interface AnalyticsExportService {

    byte[] exportDashboardToExcel(Long companyId, Long periodId, String dashboardType);

    byte[] exportDashboardToPdf(Long companyId, Long periodId, String dashboardType);

    ExportResponse getExportStatus(String jobId);

    enum DashboardType {
        FINANCIAL_OVERVIEW,
        AR_AP_AGING,
        CASH_FLOW,
        PERIOD_SUMMARY
    }
}

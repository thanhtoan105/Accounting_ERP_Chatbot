package com.accounting.service.analytics;

import java.util.UUID;

import com.accounting.dto.analytics.ReconciliationReportDTO;

public interface ReconciliationReportService {

    ReconciliationReportDTO generateReport(Long companyId, UUID periodId);

    byte[] exportToExcel(Long companyId, UUID periodId);
}

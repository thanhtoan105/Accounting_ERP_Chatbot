package com.accounting.service.dashboard;

import java.time.Duration;

import com.accounting.service.dashboard.dto.FreshnessStatus;

public interface DashboardFreshnessService {

    FreshnessStatus getFreshness(Long companyId);

    FreshnessStatus getFreshnessForDataset(Long companyId, String datasetType);

    Duration getTimeSinceLastRefresh(Long companyId);
}

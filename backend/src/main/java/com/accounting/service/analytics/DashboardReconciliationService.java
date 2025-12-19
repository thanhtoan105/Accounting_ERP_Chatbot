package com.accounting.service.analytics;

import java.util.UUID;

import com.accounting.dto.analytics.DashboardReconciliationReport;
import com.accounting.dto.analytics.MultiCurrencyReconciliationResult;
import com.accounting.dto.analytics.ReconciliationResult;

public interface DashboardReconciliationService {

    ReconciliationResult checkARReconciliation(Long companyId, UUID periodId);

    ReconciliationResult checkRevenueReconciliation(Long companyId, UUID periodId);

    ReconciliationResult checkCashReconciliation(Long companyId, UUID periodId);

    DashboardReconciliationReport runFullReconciliation(Long companyId, UUID periodId);

    MultiCurrencyReconciliationResult checkARReconciliationByCurrency(Long companyId, UUID periodId);

    MultiCurrencyReconciliationResult checkRevenueReconciliationByCurrency(Long companyId, UUID periodId);
}

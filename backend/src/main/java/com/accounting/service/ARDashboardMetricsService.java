package com.accounting.service;

import com.accounting.dto.ARDashboardMetricsDTO;

/**
 * Service interface for AR dashboard metrics.
 * Provides overdue summary information for dashboard tiles.
 */
public interface ARDashboardMetricsService {

    /**
     * Get dashboard metrics including total overdue, count, and top customers.
     * Results are cached in Redis with 5-minute TTL.
     *
     * @return dashboard metrics
     */
    ARDashboardMetricsDTO getDashboardMetrics();
}

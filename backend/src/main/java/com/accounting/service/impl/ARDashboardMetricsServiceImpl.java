package com.accounting.service.impl;

import com.accounting.dto.ARDashboardMetricsDTO;
import com.accounting.service.ARDashboardMetricsService;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of ARDashboardMetricsService.
 * Provides dashboard metrics with 5-minute cache TTL.
 * TODO: Full implementation with actual data queries.
 */
@Service
@Transactional(readOnly = true)
public class ARDashboardMetricsServiceImpl implements ARDashboardMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(ARDashboardMetricsServiceImpl.class);

    @Override
    @Cacheable(value = "ar-dashboard", key = "'ar-dashboard:' + T(com.accounting.security.CompanyContext).getCompanyId()")
    public ARDashboardMetricsDTO getDashboardMetrics() {
        logger.debug("Calculating AR dashboard metrics");

        // TODO: Implement actual metrics calculation
        // For now, return stub data
        ARDashboardMetricsDTO metrics = new ARDashboardMetricsDTO();
        metrics.setTotalOverdue(BigDecimal.ZERO);
        metrics.setOverdueCount(0);
        metrics.setTopOverdueCustomers(new ArrayList<>());

        return metrics;
    }
}

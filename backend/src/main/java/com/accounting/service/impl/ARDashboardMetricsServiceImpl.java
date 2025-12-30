package com.accounting.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.ARDashboardMetricsDTO;
import com.accounting.dto.ARDashboardMetricsDTO.TopOverdueCustomer;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ARDashboardMetricsService;

/**
 * Implementation of ARDashboardMetricsService.
 * Provides dashboard metrics with 5-minute cache TTL.
 */
@Service
@Transactional(readOnly = true)
public class ARDashboardMetricsServiceImpl implements ARDashboardMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(ARDashboardMetricsServiceImpl.class);
    private static final int TOP_CUSTOMERS_LIMIT = 5;

    private final SalesInvoiceRepository salesInvoiceRepository;

    public ARDashboardMetricsServiceImpl(SalesInvoiceRepository salesInvoiceRepository) {
        this.salesInvoiceRepository = salesInvoiceRepository;
    }

    @Override
    @Cacheable(value = "ar-dashboard", key = "'ar-dashboard:' + T(com.accounting.security.CompanyContext).getCompanyId()")
    public ARDashboardMetricsDTO getDashboardMetrics() {
        Long companyId = CompanyContext.getCompanyId();
        LocalDate today = LocalDate.now();

        logger.debug("Calculating AR dashboard metrics for company {} as of {}", companyId, today);

        ARDashboardMetricsDTO metrics = new ARDashboardMetricsDTO();

        // Get total overdue amount
        BigDecimal totalOverdue = salesInvoiceRepository.getTotalOverdueAmount(companyId, today);
        metrics.setTotalOverdue(totalOverdue != null ? totalOverdue : BigDecimal.ZERO);

        // Get overdue invoice count
        Integer overdueCount = salesInvoiceRepository.countOverdueInvoices(companyId, today);
        metrics.setOverdueCount(overdueCount != null ? overdueCount : 0);

        // Get top overdue customers
        List<Object[]> topCustomersData = salesInvoiceRepository.findTopOverdueCustomers(
                companyId, today, PageRequest.of(0, TOP_CUSTOMERS_LIMIT));

        List<TopOverdueCustomer> topCustomers = new ArrayList<>();
        for (Object[] row : topCustomersData) {
            TopOverdueCustomer customer = new TopOverdueCustomer();
            customer.setCustomerId((Long) row[0]);
            customer.setCustomerName((String) row[1]);
            customer.setOverdueAmount((BigDecimal) row[2]);
            topCustomers.add(customer);
        }
        metrics.setTopOverdueCustomers(topCustomers);

        logger.debug("AR dashboard metrics: totalOverdue={}, count={}, topCustomers={}",
                metrics.getTotalOverdue(), metrics.getOverdueCount(), topCustomers.size());

        return metrics;
    }
}

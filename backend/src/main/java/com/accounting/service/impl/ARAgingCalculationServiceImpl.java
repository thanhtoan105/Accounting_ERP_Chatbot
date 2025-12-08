package com.accounting.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.ARAgingBucketDTO;
import com.accounting.entity.Customer;
import com.accounting.entity.SalesInvoice;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ARAgingCalculationService;

/**
 * Implementation of ARAgingCalculationService.
 * Calculates AR aging buckets based on invoice due dates vs. as-of date.
 * Mirrors AP aging logic but for customers and sales invoices.
 */
@Service
@Transactional(readOnly = true)
public class ARAgingCalculationServiceImpl implements ARAgingCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(ARAgingCalculationServiceImpl.class);

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final CustomerRepository customerRepository;

    @Autowired
    public ARAgingCalculationServiceImpl(
            SalesInvoiceRepository salesInvoiceRepository, CustomerRepository customerRepository) {
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public ARAgingBucketDTO calculateAgingBuckets(Long customerId, LocalDate asOfDate) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();
        ARAgingBucketDTO buckets = new ARAgingBucketDTO();

        List<SalesInvoice> invoices;
        if (customerId != null) {
            invoices = salesInvoiceRepository.findOpenInvoicesByCustomerId(companyId, customerId);
        } else {
            invoices = getOpenInvoicesForCompany(companyId);
        }

        logger.debug(
                "Calculating aging buckets for company {} customer {} as of {}: found {} open invoices",
                companyId,
                customerId,
                currentDate,
                invoices.size());

        for (SalesInvoice invoice : invoices) {
            BigDecimal remainingBalance = invoice.getRemainingBalance();
            if (remainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            LocalDate dueDate = invoice.getDueDate();

            // Calculate overdue days as number of days currentDate is after dueDate.
            // Positive values mean the invoice is overdue; zero/negative means current or
            // future.
            long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(dueDate, currentDate);

            if (!dueDate.isAfter(currentDate)) {
                // Invoice is due today or already overdue
                long overdueDays = Math.max(daysDiff, 0);

                if (overdueDays == 0) {
                    // Treat due-today invoices as current
                    buckets.setCurrent(buckets.getCurrent().add(remainingBalance));
                } else if (overdueDays <= 30) {
                    buckets.setDays1To30(buckets.getDays1To30().add(remainingBalance));
                } else if (overdueDays <= 60) {
                    buckets.setDays31To60(buckets.getDays31To60().add(remainingBalance));
                } else if (overdueDays <= 90) {
                    buckets.setDays61To90(buckets.getDays61To90().add(remainingBalance));
                } else {
                    buckets.setDaysOver90(buckets.getDaysOver90().add(remainingBalance));
                }
            } else {
                // Future-dated invoice → Current bucket
                buckets.setCurrent(buckets.getCurrent().add(remainingBalance));
            }
        }

        // Calculate total
        BigDecimal total = buckets
                .getCurrent()
                .add(buckets.getDays1To30())
                .add(buckets.getDays31To60())
                .add(buckets.getDays61To90())
                .add(buckets.getDaysOver90());
        buckets.setTotal(total);

        logger.debug(
                "Aging buckets calculated: Current={}, 1-30d={}, 31-60d={}, 61-90d={}, 90+d={}, Total={}",
                buckets.getCurrent(),
                buckets.getDays1To30(),
                buckets.getDays31To60(),
                buckets.getDays61To90(),
                buckets.getDaysOver90(),
                buckets.getTotal());

        return buckets;
    }

    @Override
    public Map<Long, ARAgingBucketDTO> calculateAgingForAllCustomers(LocalDate asOfDate) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Missing company context");
        }

        LocalDate currentDate = asOfDate != null ? asOfDate : LocalDate.now();
        Map<Long, ARAgingBucketDTO> agingMap = new HashMap<>();

        // Get all customers for the company
        List<Customer> customers = customerRepository.findByCompanyId(companyId);

        logger.debug(
                "Calculating aging for all customers in company {} as of {}: {} customers",
                companyId,
                currentDate,
                customers.size());

        for (Customer customer : customers) {
            ARAgingBucketDTO buckets = calculateAgingBuckets(customer.getId(), currentDate);

            // Only include customers with outstanding balances
            if (buckets.getTotal().compareTo(BigDecimal.ZERO) > 0) {
                agingMap.put(customer.getId(), buckets);
            }
        }

        logger.debug(
                "Aging calculation complete: {} customers with outstanding balances", agingMap.size());

        return agingMap;
    }

    /**
     * Get all open invoices for a company.
     * Open invoices are those with status POSTED or PARTIALLY_PAID and remaining
     * balance > 0.
     *
     * @param companyId company ID
     * @return list of open invoices
     */
    private List<SalesInvoice> getOpenInvoicesForCompany(Long companyId) {
        // Query all open invoices for the company
        // Note: This could be optimized with a custom repository method if needed
        return salesInvoiceRepository.findAll().stream()
                .filter(
                        invoice -> invoice.getCompanyId().equals(companyId)
                                && (invoice.getStatus().name().equals("POSTED")
                                        || invoice.getStatus().name().equals("PARTIALLY_PAID"))
                                && invoice.getRemainingBalance().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }
}

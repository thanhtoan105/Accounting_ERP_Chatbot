package com.accounting.service;

import java.time.LocalDate;

import com.accounting.dto.ARAgingBucketDTO;

/**
 * Service interface for AR aging calculations.
 * Computes aging buckets based on invoice due dates vs. as-of date.
 */
public interface ARAgingCalculationService {

    /**
     * Calculate aging buckets for a specific customer.
     * Queries POSTED and PARTIALLY_PAID invoices with remaining balance > 0.
     * Classifies invoices into aging buckets based on days overdue.
     *
     * @param customerId customer ID (null for all customers)
     * @param asOfDate   as-of date for aging calculation (null defaults to today)
     * @return aging bucket amounts
     */
    ARAgingBucketDTO calculateAgingBuckets(Long customerId, LocalDate asOfDate);

    /**
     * Calculate aging buckets for all customers in the current company.
     * Used for generating the full aging report.
     *
     * @param asOfDate as-of date for aging calculation (null defaults to today)
     * @return map of customer ID to aging buckets
     */
    java.util.Map<Long, ARAgingBucketDTO> calculateAgingForAllCustomers(LocalDate asOfDate);
}

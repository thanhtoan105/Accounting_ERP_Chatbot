package com.accounting.service;

import com.accounting.dto.ARAgingDrillDownDTO;
import com.accounting.dto.ARAgingReportDTO;
import com.accounting.dto.ARInvoiceDetailDTO;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for AR aging report operations.
 * Provides aging report generation with Redis caching and RBAC filtering.
 */
public interface ARAgingService {

    /**
     * Get AR aging report with optional filters.
     * Results are cached in Redis with 1-hour TTL.
     * Applies RBAC filtering based on user role.
     *
     * @param customerId optional customer ID filter
     * @param asOfDate   as-of date for aging calculation (null defaults to today)
     * @param status     optional invoice status filter
     * @param bucket     optional aging bucket filter (CURRENT, DAYS_1_30,
     *                   DAYS_31_60, DAYS_61_90,
     *                   DAYS_OVER_90)
     * @param pageable   pagination parameters
     * @return paginated aging report
     */
    Page<ARAgingReportDTO> getAgingReport(
            Long customerId,
            LocalDate asOfDate,
            String status,
            String bucket,
            Pageable pageable);

    /**
     * Manually refresh aging report cache.
     * Forces recalculation of aging buckets for all customers.
     *
     * @param asOfDate as-of date for aging calculation
     */
    void refreshAgingCache(LocalDate asOfDate);

    /**
     * Invalidate AR aging cache.
     * Called when invoices are approved or receipts are posted.
     * Clears all cached aging data to force recalculation on next request.
     */
    void invalidateAgingCache();

    /**
     * Get drill-down invoice list for a specific customer and aging bucket.
     * Shows detailed invoice information for invoices in the selected bucket.
     *
     * @param customerId     customer ID
     * @param agingBucketKey aging bucket key (CURRENT, DAYS_1_30, DAYS_31_60,
     *                       DAYS_61_90,
     *                       DAYS_OVER_90)
     * @param asOfDate       as-of date for aging calculation (null defaults to
     *                       today)
     * @param pageable       pagination parameters
     * @return paginated invoice list
     */
    Page<ARAgingDrillDownDTO> getDrillDownDetail(
            Long customerId, String agingBucketKey, LocalDate asOfDate,
            Pageable pageable);

    /**
     * Get detailed invoice information with payment history.
     * Used for read-only invoice view from aging report.
     *
     * @param invoiceId invoice ID
     * @return invoice detail with payment history
     */
    ARInvoiceDetailDTO getInvoiceDetail(UUID invoiceId);
}

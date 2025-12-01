package com.accounting.service;

import java.time.LocalDate;

/**
 * Service interface for AR aging report export operations.
 * Provides Excel and PDF export functionality with metadata.
 */
public interface ARAgingExportService {

    /**
     * Export AR aging report to Excel format.
     * Includes snapshot metadata and applied filters.
     *
     * @param customerId optional customer ID filter
     * @param asOfDate   as-of date for aging calculation
     * @return byte array of Excel file
     */
    byte[] exportToExcel(Long customerId, LocalDate asOfDate);

    /**
     * Export AR aging report to PDF format.
     * Includes company header, snapshot metadata, and legal footer.
     *
     * @param customerId optional customer ID filter
     * @param asOfDate   as-of date for aging calculation
     * @return byte array of PDF file
     */
    byte[] exportToPDF(Long customerId, LocalDate asOfDate);
}

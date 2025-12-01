package com.accounting.service;

import com.accounting.dto.cashbook.CashBookFilterDTO;
import com.accounting.dto.cashbook.CashBookResponseDTO;
import com.accounting.dto.cashbook.CashBookSummaryDTO;

/**
 * Service for Cash Book / Bank Book export operations.
 * Provides Excel and PDF export with company branding and document hash.
 *
 * <p>
 * AC6.4-04: Export to Excel/PDF with company branding/logo, filter snapshot,
 * timestamp, generated-by; file footer includes document hash.
 */
public interface CashBookExportService {

    /**
     * Export cash book transactions to Excel format.
     * Includes company header, filter snapshot, and SHA256 hash in footer.
     *
     * @param cashBookData cash book response data to export
     * @param filter       filters applied for snapshot metadata
     * @return byte array of Excel file (.xlsx)
     */
    byte[] exportToExcel(CashBookResponseDTO cashBookData, CashBookFilterDTO filter);

    /**
     * Export cash book transactions to PDF format.
     * Includes company header, filter snapshot, and SHA256 hash in footer.
     *
     * @param cashBookData cash book response data to export
     * @param filter       filters applied for snapshot metadata
     * @return byte array of PDF file
     */
    byte[] exportToPdf(CashBookResponseDTO cashBookData, CashBookFilterDTO filter);

    /**
     * Export multi-account summary to Excel format.
     * Includes company header, filter snapshot, and SHA256 hash in footer.
     *
     * @param summaryData summary data to export
     * @param filter      filters applied for snapshot metadata
     * @return byte array of Excel file (.xlsx)
     */
    byte[] exportSummaryToExcel(CashBookSummaryDTO summaryData, CashBookFilterDTO filter);

    /**
     * Export multi-account summary to PDF format.
     * Includes company header, filter snapshot, and SHA256 hash in footer.
     *
     * @param summaryData summary data to export
     * @param filter      filters applied for snapshot metadata
     * @return byte array of PDF file
     */
    byte[] exportSummaryToPdf(CashBookSummaryDTO summaryData, CashBookFilterDTO filter);

    /**
     * Generate SHA256 hash of cash book data for integrity verification.
     *
     * @param cashBookData cash book data to hash
     * @return first 16 characters of SHA256 hash
     */
    String generateDataHash(CashBookResponseDTO cashBookData);
}

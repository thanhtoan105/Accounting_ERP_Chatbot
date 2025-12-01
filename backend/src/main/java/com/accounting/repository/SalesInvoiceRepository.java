package com.accounting.repository;

import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesInvoiceRepository
        extends JpaRepository<SalesInvoice, UUID>, JpaSpecificationExecutor<SalesInvoice> {

    /**
     * Find all sales invoices for a company.
     *
     * @param companyId company ID
     * @return list of sales invoices
     */
    List<SalesInvoice> findByCompanyId(Long companyId);

    /**
     * Find sales invoice by company ID and invoice ID.
     *
     * @param companyId company ID
     * @param invoiceId invoice ID
     * @return optional sales invoice
     */
    Optional<SalesInvoice> findByCompanyIdAndId(Long companyId, UUID invoiceId);

    /**
     * Find sales invoices by company and status.
     *
     * @param companyId company ID
     * @param status    invoice status
     * @return list of sales invoices
     */
    List<SalesInvoice> findByCompanyIdAndStatus(Long companyId, SalesInvoiceStatus status);

    /**
     * Find sales invoices by company and date range.
     *
     * @param companyId company ID
     * @param dateFrom  start date (inclusive)
     * @param dateTo    end date (inclusive)
     * @return list of sales invoices
     */
    List<SalesInvoice> findByCompanyIdAndInvoiceDateBetween(
            Long companyId, LocalDate dateFrom, LocalDate dateTo);

    /**
     * Check if sales invoice exists with duplicate customer+invoice number+year
     * combination.
     * Unique constraint: UNIQUE(company_id, customer_id, invoice_number,
     * EXTRACT(YEAR FROM invoice_date))
     * WHERE is_deleted = FALSE
     *
     * @param companyId     company ID
     * @param customerId    customer ID
     * @param invoiceNumber invoice number
     * @param yearStart     start of year
     * @param yearEnd       end of year
     * @param excludeId     invoice ID to exclude (for updates, null for creates)
     * @return true if duplicate exists, false otherwise
     */
    @Query("SELECT COUNT(si) > 0 FROM SalesInvoice si WHERE si.companyId = :companyId "
            + "AND si.customerId = :customerId "
            + "AND si.invoiceNumber = :invoiceNumber "
            + "AND si.invoiceDate >= :yearStart AND si.invoiceDate < :yearEnd "
            + "AND si.isDeleted = false "
            + "AND (:excludeId IS NULL OR si.id != :excludeId)")
    boolean existsByCompanyIdAndCustomerIdAndInvoiceNumberAndYear(
            @Param("companyId") Long companyId,
            @Param("customerId") Long customerId,
            @Param("invoiceNumber") String invoiceNumber,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd,
            @Param("excludeId") UUID excludeId);

    /**
     * Check if sales invoice exists with duplicate customer+invoice number+invoice
     * date combination.
     *
     * @param companyId     company ID
     * @param customerId    customer ID
     * @param invoiceNumber invoice number
     * @param invoiceDate   invoice date
     * @param excludeId     invoice ID to exclude (for updates, null for creates)
     * @return true if duplicate exists, false otherwise
     */
    @Query("SELECT COUNT(si) > 0 FROM SalesInvoice si WHERE si.companyId = :companyId "
            + "AND si.customerId = :customerId "
            + "AND si.invoiceNumber = :invoiceNumber "
            + "AND si.invoiceDate = :invoiceDate "
            + "AND si.isDeleted = false "
            + "AND (:excludeId IS NULL OR si.id != :excludeId)")
    boolean existsByCompanyIdAndCustomerIdAndInvoiceNumberAndInvoiceDate(
            @Param("companyId") Long companyId,
            @Param("customerId") Long customerId,
            @Param("invoiceNumber") String invoiceNumber,
            @Param("invoiceDate") LocalDate invoiceDate,
            @Param("excludeId") UUID excludeId);

    /**
     * Find sales invoice IDs matching search term using native PostgreSQL unaccent
     * function.
     * This method uses the unaccent_search() function for Vietnamese text matching.
     *
     * @param companyId  company ID to filter by
     * @param searchTerm search term (will be matched against invoice_number and
     *                   reference)
     * @return list of sales invoice IDs matching the search
     */
    @Query(value = "SELECT id FROM sales_invoices WHERE company_id = :companyId "
            + "AND is_deleted = false "
            + "AND (unaccent_search(LOWER(invoice_number)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%' "
            + "OR unaccent_search(LOWER(reference)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%')", nativeQuery = true)
    List<UUID> findIdsByCompanyIdAndSearchTerm(
            @Param("companyId") Long companyId, @Param("searchTerm") String searchTerm);

    /**
     * Find open invoices for a customer (POSTED or PARTIALLY_PAID with remaining
     * balance > 0).
     *
     * @param companyId  company ID
     * @param customerId customer ID
     * @return list of open invoices
     */
    @Query("SELECT si FROM SalesInvoice si " +
            "WHERE si.companyId = :companyId " +
            "AND si.customerId = :customerId " +
            "AND si.status IN ('POSTED', 'PARTIALLY_PAID') " +
            "AND si.remainingBalance > 0 " +
            "ORDER BY si.invoiceDate ASC")
    List<SalesInvoice> findOpenInvoicesByCustomerId(
            @Param("companyId") Long companyId,
            @Param("customerId") Long customerId);

    /**
     * Find sales invoices by company, customer, and invoice date less than or equal to given date.
     * Optimized query for statement generation.
     *
     * @param companyId  company ID
     * @param customerId customer ID
     * @param dateTo     end date (inclusive)
     * @return list of sales invoices
     */
    List<SalesInvoice> findByCompanyIdAndCustomerIdAndInvoiceDateLessThanEqual(
            Long companyId, Long customerId, LocalDate dateTo);

    /**
     * Find sales invoice by company, customer, and invoice number (case-insensitive).
     * Used for reconciliation import matching.
     *
     * @param companyId     company ID
     * @param customerId    customer ID
     * @param invoiceNumber invoice number (case-insensitive match)
     * @return optional sales invoice
     */
    @Query("SELECT si FROM SalesInvoice si " +
            "WHERE si.companyId = :companyId " +
            "AND si.customerId = :customerId " +
            "AND LOWER(TRIM(si.invoiceNumber)) = LOWER(TRIM(:invoiceNumber)) " +
            "AND si.isDeleted = false")
    Optional<SalesInvoice> findByCompanyIdAndCustomerIdAndInvoiceNumberIgnoreCase(
            @Param("companyId") Long companyId,
            @Param("customerId") Long customerId,
            @Param("invoiceNumber") String invoiceNumber);

}

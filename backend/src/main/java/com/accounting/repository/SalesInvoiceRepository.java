package com.accounting.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;

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

    /**
     * Get total overdue amount for a company.
     * Overdue = due_date < today AND status IN (POSTED, PARTIALLY_PAID) AND remaining_balance > 0.
     *
     * @param companyId company ID
     * @param today     current date
     * @return total overdue amount
     */
    @Query("SELECT COALESCE(SUM(si.remainingBalance), 0) FROM SalesInvoice si " +
            "WHERE si.companyId = :companyId " +
            "AND si.dueDate < :today " +
            "AND si.status IN ('POSTED', 'PARTIALLY_PAID') " +
            "AND si.remainingBalance > 0 " +
            "AND si.isDeleted = false")
    java.math.BigDecimal getTotalOverdueAmount(
            @Param("companyId") Long companyId,
            @Param("today") LocalDate today);

    /**
     * Count overdue invoices for a company.
     *
     * @param companyId company ID
     * @param today     current date
     * @return count of overdue invoices
     */
    @Query("SELECT COUNT(si) FROM SalesInvoice si " +
            "WHERE si.companyId = :companyId " +
            "AND si.dueDate < :today " +
            "AND si.status IN ('POSTED', 'PARTIALLY_PAID') " +
            "AND si.remainingBalance > 0 " +
            "AND si.isDeleted = false")
    Integer countOverdueInvoices(
            @Param("companyId") Long companyId,
            @Param("today") LocalDate today);

    /**
     * Get top overdue customers by total overdue amount.
     *
     * @param companyId company ID
     * @param today     current date
     * @param limit     max number of customers to return
     * @return list of [customerId, customerName, totalOverdueAmount]
     */
    @Query("SELECT si.customerId, c.name, SUM(si.remainingBalance) as totalOverdue " +
            "FROM SalesInvoice si " +
            "JOIN si.customer c " +
            "WHERE si.companyId = :companyId " +
            "AND si.dueDate < :today " +
            "AND si.status IN ('POSTED', 'PARTIALLY_PAID') " +
            "AND si.remainingBalance > 0 " +
            "AND si.isDeleted = false " +
            "GROUP BY si.customerId, c.name " +
            "ORDER BY totalOverdue DESC")
    List<Object[]> findTopOverdueCustomers(
            @Param("companyId") Long companyId,
            @Param("today") LocalDate today,
            org.springframework.data.domain.Pageable pageable);

    // Dashboard aging bucket queries

    /**
     * Get current (not yet due) outstanding amount.
     * Current = due_date >= asOfDate AND remaining_balance > 0.
     *
     * @param companyId company ID
     * @param asOfDate  as-of date
     * @param unused    unused parameter for signature compatibility
     * @return current outstanding amount
     */
    @Query("SELECT COALESCE(SUM(si.remainingBalance), 0) FROM SalesInvoice si " +
            "WHERE si.companyId = :companyId " +
            "AND si.dueDate >= :asOfDate " +
            "AND si.status IN ('POSTED', 'PARTIALLY_PAID') " +
            "AND si.remainingBalance > 0 " +
            "AND si.isDeleted = false")
    java.math.BigDecimal getAgingBucketAmount(
            @Param("companyId") Long companyId,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("unused") LocalDate unused);

    /**
     * Get aging bucket amount for a specific date range.
     *
     * @param companyId company ID
     * @param startDate start date (inclusive)
     * @param endDate   end date (inclusive)
     * @return outstanding amount in range
     */
    @Query("SELECT COALESCE(SUM(si.remainingBalance), 0) FROM SalesInvoice si " +
            "WHERE si.companyId = :companyId " +
            "AND si.dueDate >= :startDate " +
            "AND si.dueDate <= :endDate " +
            "AND si.status IN ('POSTED', 'PARTIALLY_PAID') " +
            "AND si.remainingBalance > 0 " +
            "AND si.isDeleted = false")
    java.math.BigDecimal getAgingBucketAmountByRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Get aging bucket amount for invoices over 90 days overdue.
     *
     * @param companyId  company ID
     * @param cutoffDate date before which invoices are considered over 90 days
     * @return outstanding amount over 90 days
     */
    @Query("SELECT COALESCE(SUM(si.remainingBalance), 0) FROM SalesInvoice si " +
            "WHERE si.companyId = :companyId " +
            "AND si.dueDate < :cutoffDate " +
            "AND si.status IN ('POSTED', 'PARTIALLY_PAID') " +
            "AND si.remainingBalance > 0 " +
            "AND si.isDeleted = false")
    java.math.BigDecimal getAgingBucketAmountOver90(
            @Param("companyId") Long companyId,
            @Param("cutoffDate") LocalDate cutoffDate);

    // Dashboard revenue queries

    /**
     * Get monthly revenue from posted invoices.
     *
     * @param companyId company ID
     * @param startDate month start date
     * @param endDate   month end date
     * @return total revenue for the month
     */
    @Query("SELECT COALESCE(SUM(si.totalAmount), 0) FROM SalesInvoice si " +
            "WHERE si.companyId = :companyId " +
            "AND si.invoiceDate >= :startDate " +
            "AND si.invoiceDate <= :endDate " +
            "AND si.status IN ('POSTED', 'PARTIALLY_PAID', 'PAID') " +
            "AND si.isDeleted = false")
    java.math.BigDecimal getMonthlyRevenue(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Dashboard cash flow queries (stub - actual implementation would need payment/receipt tables)

    /**
     * Get monthly cash inflow (payments received).
     * Note: This is a stub that returns 0. Real implementation needs ARPayment table query.
     *
     * @param companyId company ID
     * @param startDate month start date
     * @param endDate   month end date
     * @return total inflow for the month
     */
    @Query("SELECT COALESCE(SUM(si.totalAmount - si.remainingBalance), 0) FROM SalesInvoice si " +
            "WHERE si.companyId = :companyId " +
            "AND si.invoiceDate >= :startDate " +
            "AND si.invoiceDate <= :endDate " +
            "AND si.status IN ('PAID', 'PARTIALLY_PAID') " +
            "AND si.isDeleted = false")
    java.math.BigDecimal getMonthlyInflow(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Get monthly cash outflow (payments made).
     * Note: Returns 0 - actual implementation needs APPayment/Voucher table query.
     *
     * @param companyId company ID
     * @param startDate month start date
     * @param endDate   month end date
     * @return total outflow for the month (currently 0)
     */
    @Query("SELECT CAST(0 AS java.math.BigDecimal)")
    java.math.BigDecimal getMonthlyOutflow(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Dashboard expense breakdown (stub - needs voucher/expense table)

    /**
     * Get expenses grouped by category.
     * Note: Returns empty list - actual implementation needs expense voucher table.
     *
     * @param companyId company ID
     * @param startDate period start date
     * @param endDate   period end date
     * @return list of [categoryId, categoryName, amount]
     */
    @Query("SELECT CAST(NULL AS Long), CAST(NULL AS String), CAST(0 AS java.math.BigDecimal) " +
            "FROM SalesInvoice si WHERE 1=0")
    List<Object[]> getExpensesByCategory(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

}

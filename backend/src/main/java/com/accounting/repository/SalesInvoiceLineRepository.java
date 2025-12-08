package com.accounting.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.SalesInvoiceLine;

public interface SalesInvoiceLineRepository
    extends JpaRepository<SalesInvoiceLine, UUID> {

  /**
   * Find all lines for a sales invoice.
   *
   * @param salesInvoiceId sales invoice ID
   * @return list of sales invoice lines ordered by line number
   */
  List<SalesInvoiceLine> findBySalesInvoiceIdOrderByLineNumberAsc(UUID salesInvoiceId);

  /**
   * Find all lines for a sales invoice by company ID (for company scoping).
   *
   * @param companyId company ID
   * @param salesInvoiceId sales invoice ID
   * @return list of sales invoice lines ordered by line number
   */
  List<SalesInvoiceLine> findByCompanyIdAndSalesInvoiceIdOrderByLineNumberAsc(
      Long companyId, UUID salesInvoiceId);

  /**
   * Delete all lines for a sales invoice.
   *
   * @param salesInvoiceId sales invoice ID
   */
  void deleteBySalesInvoiceId(UUID salesInvoiceId);

  /**
   * Count lines for a sales invoice.
   *
   * @param salesInvoiceId sales invoice ID
   * @return count of lines
   */
  long countBySalesInvoiceId(UUID salesInvoiceId);

  /**
   * Find lines by account ID (for account-level queries).
   *
   * @param companyId company ID
   * @param accountId account ID
   * @return list of sales invoice lines
   */
  List<SalesInvoiceLine> findByCompanyIdAndAccountId(Long companyId, Long accountId);

  /**
   * Find lines for a set of sales invoices (company-scoped).
   *
   * @param companyId company ID
   * @param salesInvoiceIds list of sales invoice IDs
   * @return list of sales invoice lines ordered by invoice and line number
   */
  List<SalesInvoiceLine> findByCompanyIdAndSalesInvoiceIdInOrderBySalesInvoiceIdAscLineNumberAsc(
      Long companyId, List<java.util.UUID> salesInvoiceIds);

  /**
   * Find specific line scoped by company.
   *
   * @param companyId company ID
   * @param id line ID
   * @return optional sales invoice line
   */
  java.util.Optional<SalesInvoiceLine> findByCompanyIdAndId(Long companyId, UUID id);
}

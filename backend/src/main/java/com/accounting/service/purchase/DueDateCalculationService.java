package com.accounting.service.purchase;

import java.time.LocalDate;

/**
 * Service for calculating due dates for purchase bills.
 * Supports configurable payment terms per supplier or company default.
 */
public interface DueDateCalculationService {

  /**
   * Calculate due date from bill date and payment terms.
   * Defaults to 30 days if payment terms not provided.
   *
   * @param billDate bill date
   * @param paymentTermsDays payment terms in days (null for default 30 days)
   * @return calculated due date
   */
  LocalDate calculateDueDate(LocalDate billDate, Integer paymentTermsDays);

  /**
   * Calculate due date from bill date using supplier's payment terms.
   * Falls back to company default if supplier has no payment terms configured.
   *
   * @param billDate bill date
   * @param supplierId supplier ID (optional, for supplier-specific payment terms)
   * @return calculated due date
   */
  LocalDate calculateDueDateForSupplier(LocalDate billDate, Long supplierId);

  /**
   * Calculate due date using business days (exclude weekends and holidays).
   * Only used if business days calculation is configured for the company.
   *
   * @param billDate bill date
   * @param paymentTermsDays payment terms in business days
   * @param excludeWeekends whether to exclude weekends
   * @param excludeHolidays whether to exclude holidays (requires holiday configuration)
   * @return calculated due date
   */
  LocalDate calculateDueDateBusinessDays(
      LocalDate billDate,
      Integer paymentTermsDays,
      boolean excludeWeekends,
      boolean excludeHolidays);

  /**
   * Get default payment terms for the company (in days).
   *
   * @return default payment terms (default: 30 days)
   */
  Integer getDefaultPaymentTerms();
}


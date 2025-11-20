package com.accounting.service.impl.purchase;

import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.purchase.DueDateCalculationService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of DueDateCalculationService for purchase bills.
 * Handles due date calculation with configurable payment terms and business days support.
 */
@Service
public class DueDateCalculationServiceImpl implements DueDateCalculationService {

  private static final Logger logger = LoggerFactory.getLogger(DueDateCalculationServiceImpl.class);

  // Default payment terms: 30 days
  private static final int DEFAULT_PAYMENT_TERMS_DAYS = 30;

  private final SupplierRepository supplierRepository;

  // TODO: Add holiday configuration service when available
  // private final HolidayConfigurationService holidayConfigurationService;

  public DueDateCalculationServiceImpl(SupplierRepository supplierRepository) {
    this.supplierRepository = supplierRepository;
    // this.holidayConfigurationService = holidayConfigurationService;
  }

  @Override
  public LocalDate calculateDueDate(LocalDate billDate, Integer paymentTermsDays) {
    if (billDate == null) {
      throw new IllegalArgumentException("Bill date cannot be null");
    }

    if (paymentTermsDays == null || paymentTermsDays <= 0) {
      paymentTermsDays = DEFAULT_PAYMENT_TERMS_DAYS;
    }

    LocalDate dueDate = billDate.plusDays(paymentTermsDays);

    logger.debug("Calculated due date: billDate={}, paymentTermsDays={}, dueDate={}",
        billDate, paymentTermsDays, dueDate);

    return dueDate;
  }

  @Override
  public LocalDate calculateDueDateForSupplier(LocalDate billDate, Long supplierId) {
    if (billDate == null) {
      throw new IllegalArgumentException("Bill date cannot be null");
    }

    Integer paymentTermsDays = null;

    // Try to get supplier-specific payment terms
    if (supplierId != null) {
      Long companyId = CompanyContext.getCompanyId();
      if (companyId != null) {
        paymentTermsDays = getSupplierPaymentTerms(supplierId, companyId);
      }
    }

    // Fall back to default if supplier has no payment terms
    if (paymentTermsDays == null) {
      paymentTermsDays = getDefaultPaymentTerms();
    }

    return calculateDueDate(billDate, paymentTermsDays);
  }

  @Override
  public LocalDate calculateDueDateBusinessDays(
      LocalDate billDate,
      Integer paymentTermsDays,
      boolean excludeWeekends,
      boolean excludeHolidays) {
    if (billDate == null) {
      throw new IllegalArgumentException("Bill date cannot be null");
    }

    if (paymentTermsDays == null || paymentTermsDays <= 0) {
      paymentTermsDays = DEFAULT_PAYMENT_TERMS_DAYS;
    }

    LocalDate dueDate = billDate;
    int businessDaysAdded = 0;

    // Add business days, skipping weekends and holidays as configured
    while (businessDaysAdded < paymentTermsDays) {
      dueDate = dueDate.plusDays(1);

      // Check if this day should be counted
      boolean isBusinessDay = true;

      // Exclude weekends if configured
      if (excludeWeekends) {
        DayOfWeek dayOfWeek = dueDate.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
          isBusinessDay = false;
        }
      }

      // Exclude holidays if configured
      if (excludeHolidays && isBusinessDay) {
        // TODO: Check against holiday configuration when available
        // if (isHoliday(dueDate)) {
        //   isBusinessDay = false;
        // }
      }

      if (isBusinessDay) {
        businessDaysAdded++;
      }
    }

    logger.debug("Calculated due date (business days): billDate={}, paymentTermsDays={}, excludeWeekends={}, excludeHolidays={}, dueDate={}",
        billDate, paymentTermsDays, excludeWeekends, excludeHolidays, dueDate);

    return dueDate;
  }

  @Override
  public Integer getDefaultPaymentTerms() {
    // TODO: Get from company settings when available
    // For now, return default value
    return DEFAULT_PAYMENT_TERMS_DAYS;
  }

  /**
   * Get payment terms for a supplier.
   * Returns null if supplier has no payment terms configured.
   *
   * @param supplierId supplier ID
   * @param companyId company ID
   * @return payment terms in days, or null if not configured
   */
  private Integer getSupplierPaymentTerms(Long supplierId, Long companyId) {
    // TODO: Add payment_terms_days field to Supplier entity and retrieve it here
    // For now, return null (will fall back to default)
    if (supplierRepository.findByCompanyIdAndId(companyId, supplierId).isPresent()) {
      // TODO: return supplier.getPaymentTermsDays();
      return null; // Not yet implemented
    }
    return null;
  }

  /**
   * Check if a date is a holiday.
   * TODO: Implement when holiday configuration service is available.
   *
   * @param date date to check
   * @return true if holiday, false otherwise
   */
  @SuppressWarnings("unused")
  private boolean isHoliday(LocalDate date) {
    // TODO: Implement holiday checking
    // This would check against a holiday configuration table or service
    // For now, return false (no holidays configured)
    return false;
  }

  /**
   * Get configured holidays for the company.
   * TODO: Implement when holiday configuration service is available.
   *
   * @param companyId company ID
   * @return set of holiday dates
   */
  @SuppressWarnings("unused")
  private Set<LocalDate> getHolidays(Long companyId) {
    // TODO: Implement holiday retrieval
    // This would query a holiday configuration table
    // For now, return empty set
    return new HashSet<>();
  }
}


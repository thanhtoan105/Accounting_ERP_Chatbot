package com.accounting.service;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.PeriodCloseRequest;
import com.accounting.dto.PeriodReopenRequest;
import com.accounting.dto.PeriodSummaryDTO;
import com.accounting.entity.AccountingPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing accounting periods.
 * Handles period close/reopen workflows, period queries, and period-voucher validation.
 */
public interface PeriodManagementService {

  /**
   * Get current period for the company (period containing today's date).
   *
   * @return current period DTO, or empty if no period exists for today
   */
  Optional<AccountingPeriodDTO> getCurrentPeriod();

  /**
   * Get open periods for the company (current + 3 prior/next open periods).
   * Used for period selector dropdown.
   *
   * @return list of open period DTOs, ordered by start date
   */
  List<AccountingPeriodDTO> getOpenPeriods();

  /**
   * Get period by ID with company scoping.
   *
   * @param periodId period ID
   * @return period DTO, or empty if not found
   */
  Optional<AccountingPeriodDTO> getPeriodById(UUID periodId);

  /**
   * Get period summary with badge data (closing status, posting flow status, pending drafts count).
   *
   * @param periodId period ID
   * @return period summary DTO, or empty if not found
   */
  Optional<PeriodSummaryDTO> getPeriodSummary(UUID periodId);

  /**
   * Find period that contains a specific date for the company.
   *
   * @param date date to check
   * @return period DTO containing the date, or empty if not found
   */
  Optional<AccountingPeriodDTO> findPeriodByDate(LocalDate date);

  /**
   * Check if period is open.
   *
   * @param periodId period ID
   * @return true if period is open, false otherwise
   */
  boolean isPeriodOpen(UUID periodId);

  /**
   * Check if date is in an open period.
   *
   * @param date date to check
   * @return true if date falls within an open period, false otherwise
   */
  boolean isDateInOpenPeriod(LocalDate date);

  /**
   * Close a period atomically.
   * Validates no draft vouchers exist, validates all posted vouchers are balanced,
   * updates period status to CLOSED, batch-locks all vouchers in period, and creates audit log.
   *
   * @param periodId period ID to close
   * @param request close request with reason
   * @return closed period DTO
   * @throws org.springframework.web.server.ResponseStatusException if validation fails (400) or period already closed (409)
   */
  AccountingPeriodDTO closePeriod(UUID periodId, PeriodCloseRequest request);

  /**
   * Reopen a period atomically.
   * Validates period is CLOSED, logs reopen attempt (even if not approved),
   * updates period status to OPEN, removes lock flag from all vouchers, and creates audit log.
   *
   * @param periodId period ID to reopen
   * @param request reopen request with reason and approval metadata
   * @return reopened period DTO
   * @throws org.springframework.web.server.ResponseStatusException if validation fails (400)
   */
  AccountingPeriodDTO reopenPeriod(UUID periodId, PeriodReopenRequest request);

  /**
   * Validate period is open for voucher operations.
   *
   * @param periodId period ID to validate
   * @param operation operation type (e.g., "VOUCHER_CREATE", "VOUCHER_POST")
   * @throws org.springframework.web.server.ResponseStatusException if period is closed or future (400)
   */
  void validatePeriodForVoucherOperation(UUID periodId, String operation);

  /**
   * Get all periods for the company.
   *
   * @return list of all period DTOs
   */
  List<AccountingPeriodDTO> getAllPeriods();

  /**
   * Get periods by fiscal year.
   *
   * @param fiscalYear fiscal year
   * @return list of period DTOs for the fiscal year
   */
  List<AccountingPeriodDTO> getPeriodsByFiscalYear(Integer fiscalYear);

  /**
   * Create a new period.
   *
   * @param period period entity to create
   * @return created period DTO
   * @throws org.springframework.web.server.ResponseStatusException if validation fails (400)
   */
  AccountingPeriodDTO createPeriod(AccountingPeriod period);

  /**
   * Update an existing period.
   *
   * @param periodId period ID
   * @param period period entity with updated values
   * @return updated period DTO
   * @throws org.springframework.web.server.ResponseStatusException if validation fails (400) or period not found (404)
   */
  AccountingPeriodDTO updatePeriod(UUID periodId, AccountingPeriod period);

  /**
   * Delete a period (only if no vouchers exist).
   *
   * @param periodId period ID
   * @throws org.springframework.web.server.ResponseStatusException if validation fails (400) or period not found (404)
   */
  void deletePeriod(UUID periodId);
}

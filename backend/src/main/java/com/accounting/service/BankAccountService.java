package com.accounting.service;

import com.accounting.dto.BalanceTooltipDTO;
import com.accounting.dto.BankAccountCreateRequest;
import com.accounting.dto.BankAccountDTO;
import com.accounting.dto.BankAccountUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for BankAccount operations including CRUD, search, and balance tooltip.
 */
public interface BankAccountService {

  /**
   * Find all bank accounts with pagination, sorting, and filters.
   * Company-scoped - only returns bank accounts in the current company.
   *
   * @param pageable pagination and sorting parameters
   * @param type filter by account type (CASH/BANK, optional, null for all)
   * @param status filter by active status (optional, null for all)
   * @param search search term for account number or bank name (optional, null to ignore)
   * @return page of bank accounts matching filters
   */
  Page<BankAccountDTO> findAll(Pageable pageable, String type, Boolean status, String search);

  /**
   * Get bank account by ID (must be in same company).
   *
   * @param bankAccountId bank account ID
   * @return bank account DTO or empty if not found
   */
  java.util.Optional<BankAccountDTO> getBankAccountById(Long bankAccountId);

  /**
   * Create a new bank account.
   *
   * @param request create request with bank account details
   * @return created bank account DTO
   */
  BankAccountDTO create(BankAccountCreateRequest request);

  /**
   * Update an existing bank account.
   *
   * @param bankAccountId bank account ID
   * @param request update request with bank account details
   * @return updated bank account DTO
   */
  BankAccountDTO update(Long bankAccountId, BankAccountUpdateRequest request);

  /**
   * Delete a bank account (hard delete).
   * Blocks deletion if bank account is referenced by vouchers, periods, or reconciliations.
   *
   * @param bankAccountId bank account ID
   */
  void delete(Long bankAccountId);

  /**
   * Activate a bank account (set active=true).
   *
   * @param bankAccountId bank account ID
   */
  void activate(Long bankAccountId);

  /**
   * Deactivate a bank account (set active=false).
   *
   * @param bankAccountId bank account ID
   */
  void deactivate(Long bankAccountId);

  /**
   * Get balance tooltip data (current and prior period balances).
   *
   * @param bankAccountId bank account ID
   * @return balance tooltip DTO
   */
  BalanceTooltipDTO getBalanceTooltip(Long bankAccountId);
}


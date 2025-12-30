package com.accounting.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.accounting.entity.AccountControl;

/**
 * Service for managing account control configuration.
 * Handles company-specific required dimension settings per account.
 */
public interface AccountControlService {

  /**
   * Get account control by ID.
   *
   * @param id account control ID
   * @return account control if present
   */
  Optional<AccountControl> getAccountControlById(UUID id);

  /**
   * Get account control by account ID and company ID.
   *
   * @param accountId account ID
   * @param companyId company ID
   * @return account control if present
   */
  Optional<AccountControl> getAccountControlByAccountId(Long accountId, Long companyId);

  /**
   * Get all account controls for current company.
   *
   * @return list of account controls
   */
  List<AccountControl> getAllAccountControls();

  /**
   * Get required dimensions for an account.
   * Returns the account control configuration if it exists, otherwise returns null.
   *
   * @param accountId account ID
   * @param companyId company ID
   * @return account control if present, empty otherwise
   */
  Optional<AccountControl> getRequiredDimensions(Long accountId, Long companyId);

  /**
   * Create or update account control.
   *
   * @param accountControl account control to save
   * @return saved account control
   */
  AccountControl saveAccountControl(AccountControl accountControl);

  /**
   * Delete account control by ID.
   *
   * @param id account control ID
   */
  void deleteAccountControl(UUID id);

  /**
   * Validate required dimensions for a voucher line.
   * Checks if the account requires customer, supplier, or item dimensions
   * and validates that they are present.
   *
   * @param accountControl account control configuration
   * @param customerId customer ID (optional)
   * @param supplierId supplier ID (optional)
   * @param itemId item ID (optional)
   * @return list of missing dimension error messages (empty if all required dimensions present)
   */
  List<String> validateRequiredDimensions(
      AccountControl accountControl,
      Long customerId,
      Long supplierId,
      Long itemId);
}

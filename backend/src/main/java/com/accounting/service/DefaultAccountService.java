package com.accounting.service;

import java.util.List;
import java.util.Optional;

import com.accounting.dto.DefaultAccountCreateRequest;
import com.accounting.dto.DefaultAccountDTO;
import com.accounting.dto.DefaultAccountUpdateRequest;

/**
 * Service for Default Account operations.
 */
public interface DefaultAccountService {

  /**
   * Find all default accounts for the current company with optional filters.
   *
   * @param search search term for entry name or voucher type (optional)
   * @param status filter by status (ACTIVE/INACTIVE, optional)
   * @param voucherType filter by voucher type (optional)
   * @return list of default accounts
   */
  List<DefaultAccountDTO> findAll(String search, String status, String voucherType);

  /**
   * Get default account by ID (must be in same company).
   *
   * @param id default account ID
   * @return default account or empty if not found
   */
  Optional<DefaultAccountDTO> findById(Long id);

  /**
   * Create a new default account.
   *
   * @param request create request
   * @return created default account
   * @throws IllegalArgumentException if accounts are invalid
   */
  DefaultAccountDTO create(DefaultAccountCreateRequest request);

  /**
   * Update an existing default account.
   *
   * @param id default account ID
   * @param request update request
   * @return updated default account
   * @throws IllegalArgumentException if default account not found or accounts are invalid
   */
  DefaultAccountDTO update(Long id, DefaultAccountUpdateRequest request);

  /**
   * Delete a default account.
   *
   * @param id default account ID
   * @throws IllegalArgumentException if default account not found
   */
  void delete(Long id);
}

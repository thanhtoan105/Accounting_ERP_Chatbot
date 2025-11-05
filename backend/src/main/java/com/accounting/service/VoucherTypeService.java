package com.accounting.service;

import com.accounting.dto.VoucherTypeDTO;
import com.accounting.dto.VoucherTypeCreateRequest;
import com.accounting.dto.VoucherTypeUpdateRequest;
import java.util.List;
import java.util.Optional;

/**
 * Service for Voucher Type operations.
 */
public interface VoucherTypeService {

  /**
   * Find all voucher types for the current company with optional filters.
   *
   * @param search search term for type code or name (optional)
   * @param status filter by status (ACTIVE/INACTIVE, optional)
   * @return list of voucher types
   */
  List<VoucherTypeDTO> findAll(String search, String status);

  /**
   * Get voucher type by ID (must be in same company).
   *
   * @param id voucher type ID
   * @return voucher type or empty if not found
   */
  Optional<VoucherTypeDTO> findById(Long id);

  /**
   * Create a new voucher type.
   *
   * @param request create request
   * @return created voucher type
   * @throws IllegalArgumentException if type code already exists or accounts are invalid
   */
  VoucherTypeDTO create(VoucherTypeCreateRequest request);

  /**
   * Update an existing voucher type.
   *
   * @param id voucher type ID
   * @param request update request
   * @return updated voucher type
   * @throws IllegalArgumentException if voucher type not found, type code already exists, or accounts are invalid
   */
  VoucherTypeDTO update(Long id, VoucherTypeUpdateRequest request);

  /**
   * Delete a voucher type.
   *
   * @param id voucher type ID
   * @throws IllegalArgumentException if voucher type not found
   */
  void delete(Long id);

  /**
   * Deactivate a voucher type (sets status to INACTIVE).
   *
   * @param id voucher type ID
   * @return updated voucher type
   * @throws IllegalArgumentException if voucher type not found
   */
  VoucherTypeDTO deactivate(Long id);

  /**
   * Activate a voucher type (sets status to ACTIVE).
   *
   * @param id voucher type ID
   * @return updated voucher type
   * @throws IllegalArgumentException if voucher type not found
   */
  VoucherTypeDTO activate(Long id);
}



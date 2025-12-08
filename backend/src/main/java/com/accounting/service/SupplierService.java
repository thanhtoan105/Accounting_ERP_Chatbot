package com.accounting.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.SupplierAPSummaryDTO;
import com.accounting.dto.SupplierCreateRequest;
import com.accounting.dto.SupplierDTO;
import com.accounting.dto.SupplierUpdateRequest;
import com.accounting.entity.Supplier;

/**
 * Service interface for Supplier operations including CRUD, search, and duplicate detection.
 */
public interface SupplierService {

  /**
   * Find all suppliers with pagination, sorting, and filters.
   * Company-scoped - only returns suppliers in the current company.
   *
   * @param pageable pagination and sorting parameters
   * @param status filter by active status (optional, null for all)
   * @param search search term for code or name (optional, null to ignore)
   * @return page of suppliers matching filters
   */
  Page<SupplierDTO> findAll(Pageable pageable, Boolean status, String search);

  /**
   * Get supplier by ID (must be in same company).
   *
   * @param supplierId supplier ID
   * @return supplier DTO or empty if not found
   */
  Optional<SupplierDTO> getSupplierById(Long supplierId);

  /**
   * Create a new supplier with auto-generated code.
   *
   * @param request create request with supplier details
   * @return created supplier DTO
   */
  SupplierDTO create(SupplierCreateRequest request);

  /**
   * Update an existing supplier.
   *
   * @param supplierId supplier ID
   * @param request update request with supplier details
   * @return updated supplier DTO
   */
  SupplierDTO update(Long supplierId, SupplierUpdateRequest request);

  /**
   * Delete a supplier (hard delete).
   * Blocks deletion if supplier has linked bills/payments.
   *
   * @param supplierId supplier ID
   */
  void delete(Long supplierId);

  /**
   * Activate a supplier (set active=true).
   *
   * @param supplierId supplier ID
   */
  void activate(Long supplierId);

  /**
   * Deactivate a supplier (set active=false).
   *
   * @param supplierId supplier ID
   */
  void deactivate(Long supplierId);

  /**
   * Check for duplicate suppliers by tax code, email, or phone.
   *
   * @param taxCode tax code (optional)
   * @param email email (optional)
   * @param phone phone (optional)
   * @param excludeId supplier ID to exclude (for updates)
   * @return duplicate supplier if found, empty otherwise
   */
  Optional<Supplier> checkDuplicate(String taxCode, String email, String phone, Long excludeId);

  /**
   * Get supplier AP summary (open bills, total owed, average payment days).
   * For MVP, returns placeholder values until Epic 4 (AP Module) is implemented.
   *
   * @param supplierId supplier ID
   * @return AP summary DTO
   */
  SupplierAPSummaryDTO getSupplierAPSummary(Long supplierId);
}

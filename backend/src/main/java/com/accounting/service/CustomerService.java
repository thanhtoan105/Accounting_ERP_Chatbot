package com.accounting.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.CustomerARSummaryDTO;
import com.accounting.dto.CustomerCreateRequest;
import com.accounting.dto.CustomerDTO;
import com.accounting.dto.CustomerUpdateRequest;
import com.accounting.entity.Customer;

/**
 * Service interface for Customer operations including CRUD, search, and duplicate detection.
 */
public interface CustomerService {

  /**
   * Find all customers with pagination, sorting, and filters.
   * Company-scoped - only returns customers in the current company.
   *
   * @param pageable pagination and sorting parameters
   * @param status filter by active status (optional, null for all)
   * @param search search term for code or name (optional, null to ignore)
   * @return page of customers matching filters
   */
  Page<CustomerDTO> findAll(Pageable pageable, Boolean status, String search);

  /**
   * Get customer by ID (must be in same company).
   *
   * @param customerId customer ID
   * @return customer DTO or empty if not found
   */
  Optional<CustomerDTO> getCustomerById(Long customerId);

  /**
   * Create a new customer with auto-generated code.
   *
   * @param request create request with customer details
   * @return created customer DTO
   */
  CustomerDTO create(CustomerCreateRequest request);

  /**
   * Update an existing customer.
   *
   * @param customerId customer ID
   * @param request update request with customer details
   * @return updated customer DTO
   */
  CustomerDTO update(Long customerId, CustomerUpdateRequest request);

  /**
   * Delete a customer (hard delete).
   * Blocks deletion if customer has linked invoices/payments.
   *
   * @param customerId customer ID
   */
  void delete(Long customerId);

  /**
   * Activate a customer (set active=true).
   *
   * @param customerId customer ID
   */
  void activate(Long customerId);

  /**
   * Deactivate a customer (set active=false).
   *
   * @param customerId customer ID
   */
  void deactivate(Long customerId);

  /**
   * Check for duplicate customers by tax code, email, or phone.
   *
   * @param taxCode tax code (optional)
   * @param email email (optional)
   * @param phone phone (optional)
   * @param excludeId customer ID to exclude (for updates)
   * @return duplicate customer if found, empty otherwise
   */
  Optional<Customer> checkDuplicate(String taxCode, String email, String phone, Long excludeId);

  /**
   * Get customer AR summary (open invoices, total owed, average payment days).
   * For MVP, returns placeholder values until Epic 5 (AR Module) is implemented.
   *
   * @param customerId customer ID
   * @return AR summary DTO
   */
  CustomerARSummaryDTO getCustomerARSummary(Long customerId);
}

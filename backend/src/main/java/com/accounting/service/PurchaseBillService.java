package com.accounting.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.PurchaseBillCreateRequest;
import com.accounting.dto.PurchaseBillDTO;
import com.accounting.dto.PurchaseBillListDTO;
import com.accounting.entity.PurchaseBillStatus;

public interface PurchaseBillService {

  /**
   * Find all purchase bills with pagination, filtering, and sorting support.
   *
   * @param pageable pagination and sorting parameters
   * @param supplierId filter by supplier ID (optional)
   * @param status filter by status (optional)
   * @param dateFrom filter by date from (optional)
   * @param dateTo filter by date to (optional)
   * @param search search term for bill number or reference (optional, supports Vietnamese unaccented matching)
   * @return paginated list of purchase bills
   */
  Page<PurchaseBillListDTO> findAll(
      Pageable pageable,
      Long supplierId,
      PurchaseBillStatus status,
      LocalDate dateFrom,
      LocalDate dateTo,
      String search);

  /**
   * Search purchase bills by number or reference with Vietnamese unaccented support.
   *
   * @param searchTerm search term for bill number or reference
   * @return list of matching purchase bills
   */
  List<PurchaseBillDTO> search(String searchTerm);

  /**
   * Get purchase bill by ID.
   *
   * @param billId purchase bill ID
   * @return optional purchase bill DTO
   */
  Optional<PurchaseBillDTO> findById(UUID billId);

  /**
   * Create a new purchase bill with line items.
   *
   * @param request purchase bill create request with line items
   * @return created purchase bill DTO
   * @throws org.springframework.web.server.ResponseStatusException if validation fails
   */
  PurchaseBillDTO create(PurchaseBillCreateRequest request);

  /**
   * Update an existing draft purchase bill.
   *
   * @param billId purchase bill ID
   * @param request purchase bill update request with line items
   * @return updated purchase bill DTO
   * @throws org.springframework.web.server.ResponseStatusException if validation fails or bill is not draft
   */
  PurchaseBillDTO update(UUID billId, PurchaseBillCreateRequest request);

  /**
   * Delete purchase bill with validation (only DRAFT status, creator-only unless admin).
   *
   * @param billId purchase bill ID
   * @param reason deletion reason (required)
   * @param request HTTP request for audit logging
   * @throws org.springframework.web.server.ResponseStatusException if validation fails
   */
  void delete(UUID billId, String reason, jakarta.servlet.http.HttpServletRequest request);

  /**
   * Save draft purchase bill (autosave functionality).
   *
   * @param request purchase bill create/update request
   * @return saved draft purchase bill DTO
   */
  PurchaseBillDTO saveDraft(PurchaseBillCreateRequest request);

  /**
   * Recover draft purchase bill (recoverable by creator/admin).
   *
   * @param billId purchase bill ID
   * @return recovered purchase bill DTO
   */
  PurchaseBillDTO recoverDraft(UUID billId);

  /**
   * Get list of recoverable drafts for current user/admin.
   *
   * @return list of draft purchase bills
   */
  List<PurchaseBillDTO> getDrafts();

  /**
   * Check if duplicate purchase bill exists (supplier+bill number+date combination).
   *
   * @param supplierId supplier ID
   * @param billNumber bill number
   * @param billDate bill date
   * @return true if duplicate exists, false otherwise
   */
  boolean checkDuplicate(Long supplierId, String billNumber, LocalDate billDate);
}

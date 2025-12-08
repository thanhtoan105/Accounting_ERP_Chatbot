package com.accounting.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.VoucherCountDTO;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherListDTO;

public interface VoucherService {

  /**
   * Find all vouchers with pagination, filtering, and sorting support.
   *
   * @param pageable  pagination and sorting parameters
   * @param status    filter by status (optional: draft, posted, unposted)
   * @param dateFrom  filter by date from (optional)
   * @param dateTo    filter by date to (optional)
   * @param search    search term for voucher number or description (optional,
   *                  supports Vietnamese unaccented matching)
   * @param accountId filter by account ID (optional, filters vouchers that have
   *                  lines with this account)
   * @return paginated list of vouchers
   */
  Page<VoucherListDTO> findAll(
      Pageable pageable, String status, LocalDate dateFrom, LocalDate dateTo, String search, Long accountId);

  /**
   * Search vouchers by number or description with Vietnamese unaccented support.
   *
   * @param searchTerm search term for voucher number or description
   * @return list of matching vouchers
   */
  List<VoucherDTO> search(String searchTerm);

  /**
   * Get voucher by ID.
   *
   * @param voucherId voucher ID
   * @return optional voucher DTO
   */
  Optional<VoucherDTO> getVoucherById(UUID voucherId);

  /**
   * Get voucher counts by status (for badges).
   *
   * @return voucher counts DTO
   */
  VoucherCountDTO getCounts();

  /**
   * Create a new voucher with line items.
   *
   * @param request voucher create request with line items
   * @return created voucher DTO
   * @throws org.springframework.web.server.ResponseStatusException if validation
   *                                                                fails
   */
  VoucherDTO create(VoucherCreateRequest request);

  /**
   * Update an existing draft voucher.
   *
   * @param voucherId voucher ID
   * @param request   voucher update request with line items
   * @return updated voucher DTO
   * @throws org.springframework.web.server.ResponseStatusException if validation
   *                                                                fails or
   *                                                                voucher is not
   *                                                                draft
   */
  VoucherDTO update(UUID voucherId, VoucherCreateRequest request);

  /**
   * Delete voucher with validation.
   *
   * @param voucherId voucher ID
   * @param reason    deletion reason (required)
   * @param request   HTTP request for audit logging
   * @throws org.springframework.web.server.ResponseStatusException if validation
   *                                                                fails
   */
  void delete(UUID voucherId, String reason, jakarta.servlet.http.HttpServletRequest request);

  /**
   * Post a sales invoice voucher (Dr 131 / Cr 5xx / Cr 3332).
   * Creates voucher entries for the sales invoice and posts them.
   *
   * @param salesInvoice sales invoice to post
   * @param userId       user ID performing the posting
   * @return posted voucher DTO
   * @throws org.springframework.web.server.ResponseStatusException if validation
   *                                                                fails
   */
  VoucherDTO postSalesInvoiceVoucher(
      com.accounting.entity.SalesInvoice salesInvoice, Long userId);
}

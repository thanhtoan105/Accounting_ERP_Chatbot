package com.accounting.service.voucher;

import com.accounting.dto.PostVoucherResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Service for posting vouchers with atomic transaction support.
 * Posting changes voucher status from DRAFT to POSTED and generates journal entries.
 */
public interface VoucherPostingService {

  /**
   * Post a voucher atomically.
   * Validates the voucher, updates status to POSTED, and generates journal entries
   * in a single database transaction.
   *
   * @param voucherId voucher ID to post
   * @param request HTTP request for audit logging (can be null)
   * @return response containing posted voucher and generated journal entries
   * @throws com.accounting.exception.VoucherPostingException if validation fails (contains detailed error map)
   * @throws org.springframework.web.server.ResponseStatusException if voucher not found or already posted
   */
  PostVoucherResponse postVoucher(UUID voucherId, HttpServletRequest request);
}


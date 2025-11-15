package com.accounting.service.voucher;

import com.accounting.dto.VoucherDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Service for reversing vouchers with auto-posting.
 * Reversal creates a new voucher with swapped debit/credit amounts and auto-posts it.
 */
public interface VoucherReversalService {

  /**
   * Reverse a posted voucher.
   * Creates a new reversal voucher with REV-{original_number} format,
   * copies lines with swapped debit/credit amounts, links bi-directionally,
   * and auto-posts the reversal voucher.
   *
   * @param voucherId   original voucher ID to reverse
   * @param description description for reversal voucher
   * @param reason      reason for reversal (required for audit)
   * @param request     HTTP request for audit logging (can be null)
   * @return reversal result containing both original and reversal vouchers
   * @throws org.springframework.web.server.ResponseStatusException if voucher not found, not posted, or already reversed
   */
  ReversalResult reverseVoucher(UUID voucherId, String description, String reason, HttpServletRequest request);

  /**
   * Reversal result containing both vouchers.
   */
  class ReversalResult {
    private final VoucherDTO original;
    private final VoucherDTO reversal;

    public ReversalResult(VoucherDTO original, VoucherDTO reversal) {
      this.original = original;
      this.reversal = reversal;
    }

    public VoucherDTO getOriginal() {
      return original;
    }

    public VoucherDTO getReversal() {
      return reversal;
    }
  }
}


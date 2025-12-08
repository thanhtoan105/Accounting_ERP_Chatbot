package com.accounting.service.voucher;

import java.util.UUID;

import com.accounting.dto.VoucherDTO;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Service for unposting vouchers with dependency checking.
 * Unposting changes voucher status from POSTED to DRAFT and deletes journal entries.
 */
public interface VoucherUnpostingService {

  /**
   * Check if a voucher has dependencies (referenced in payments/receipts).
   * For MVP, this is a placeholder that returns empty (no dependencies).
   * Will be implemented in Epic 4-5 when payments and receipts are available.
   *
   * @param voucherId voucher ID to check
   * @return dependency check result (empty for MVP)
   * @see docs/epics/epic-4-accounts-payable.md
   * @see docs/epics/epic-5-accounts-receivable.md
   */
  DependencyCheckResult checkDependencies(UUID voucherId);

  /**
   * Unpost a voucher atomically.
   * Validates the voucher is posted, checks dependencies, updates status to DRAFT,
   * and deletes journal entries in a single database transaction.
   *
   * @param voucherId voucher ID to unpost
   * @param reason    reason for unposting (required for audit)
   * @param request   HTTP request for audit logging (can be null)
   * @return unposted voucher DTO
   * @throws org.springframework.web.server.ResponseStatusException if voucher not found, not posted, or has dependencies
   */
  VoucherDTO unpostVoucher(UUID voucherId, String reason, HttpServletRequest request);

  /**
   * Dependency check result.
   */
  class DependencyCheckResult {
    private final boolean hasDependencies;
    private final String message;
    private final java.util.List<String> references;

    public DependencyCheckResult(boolean hasDependencies, String message, java.util.List<String> references) {
      this.hasDependencies = hasDependencies;
      this.message = message;
      this.references = references != null ? references : java.util.Collections.emptyList();
    }

    public boolean hasDependencies() {
      return hasDependencies;
    }

    public String getMessage() {
      return message;
    }

    public java.util.List<String> getReferences() {
      return references;
    }

    public static DependencyCheckResult noDependencies() {
      return new DependencyCheckResult(false, null, null);
    }

    public static DependencyCheckResult withDependencies(String message, java.util.List<String> references) {
      return new DependencyCheckResult(true, message, references);
    }
  }
}

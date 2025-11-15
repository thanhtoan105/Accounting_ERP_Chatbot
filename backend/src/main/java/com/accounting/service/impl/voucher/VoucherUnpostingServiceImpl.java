package com.accounting.service.impl.voucher;

import com.accounting.dto.VoucherDTO;
import com.accounting.entity.Voucher;
import com.accounting.repository.JournalEntryRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.VoucherService;
import com.accounting.service.voucher.VoucherUnpostingService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of VoucherUnpostingService.
 * Handles atomic unposting of vouchers with dependency checking and journal entry deletion.
 */
@Service
public class VoucherUnpostingServiceImpl implements VoucherUnpostingService {

  private static final Logger logger = LoggerFactory.getLogger(VoucherUnpostingServiceImpl.class);

  private final VoucherRepository voucherRepository;
  private final JournalEntryRepository journalEntryRepository;
  private final VoucherService voucherService;
  private final AuditService auditService;

  public VoucherUnpostingServiceImpl(
      VoucherRepository voucherRepository,
      JournalEntryRepository journalEntryRepository,
      VoucherService voucherService,
      AuditService auditService) {
    this.voucherRepository = voucherRepository;
    this.journalEntryRepository = journalEntryRepository;
    this.voucherService = voucherService;
    this.auditService = auditService;
  }

  @Override
  public DependencyCheckResult checkDependencies(UUID voucherId) {
    // Placeholder for MVP - Epic 4-5 (payments/receipts) not yet implemented
    // TODO: Implement dependency checking when payments/receipts are available
    // See: docs/epics/epic-4-accounts-payable.md (Epic 4: Purchase Bills and Cash Payments)
    // See: docs/epics/epic-5-accounts-receivable.md (Epic 5: Sales Invoices and Customer Receipts)
    return DependencyCheckResult.noDependencies();
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public VoucherDTO unpostVoucher(UUID voucherId, String reason, HttpServletRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unposting reason is required for audit");
    }

    // Load voucher with company scoping
    Voucher voucher = voucherRepository
        .findByCompanyIdAndId(companyId, voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Validate voucher status is POSTED
    if (!"posted".equals(voucher.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Cannot unpost voucher: voucher must be in POSTED status. Current status: " + voucher.getStatus());
    }

    // Check dependencies (placeholder for MVP)
    DependencyCheckResult dependencyCheck = checkDependencies(voucherId);
    if (dependencyCheck.hasDependencies()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot unpost voucher: " + dependencyCheck.getMessage());
    }

    // All checks passed - proceed with unposting in atomic transaction
    Instant now = Instant.now();

    // Update voucher status to DRAFT
    voucher.setStatus("draft");
    voucher.setPostedBy(null);
    voucher.setPostedAt(null);
    voucher.setUpdatedAt(now);
    voucher = voucherRepository.save(voucher);

    // Delete associated journal entries
    journalEntryRepository.deleteByVoucherId(voucherId);

    // Convert to DTO
    VoucherDTO voucherDTO = voucherService.getVoucherById(voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve unposted voucher"));

    logger.info(
        "Voucher unposted successfully. Voucher ID: {}, Voucher Number: {}, Reason: {}",
        voucherId,
        voucher.getVoucherNumber(),
        reason);

    // Audit logging for unposting operation
    if (request != null) {
      try {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        auditService.logVoucherUnposted(voucherId, voucher.getVoucherNumber(), reason, currentUserId, request);
      } catch (Exception e) {
        // Audit logging failure should not block the operation, but log the error
        logger.error("Failed to log voucher unposting to audit trail. Voucher ID: {}", voucherId, e);
      }
    }

    return voucherDTO;
  }
}


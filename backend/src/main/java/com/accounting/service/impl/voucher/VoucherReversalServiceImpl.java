package com.accounting.service.impl.voucher;

import com.accounting.dto.VoucherDTO;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.VoucherService;
import com.accounting.service.util.VoucherAuditHelper;
import com.accounting.service.voucher.VoucherPostingService;
import com.accounting.service.voucher.VoucherReversalService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of VoucherReversalService.
 * Handles reversal workflow with auto-posting and bi-directional linking.
 */
@Service
public class VoucherReversalServiceImpl implements VoucherReversalService {

  private static final Logger logger = LoggerFactory.getLogger(VoucherReversalServiceImpl.class);

  private final VoucherRepository voucherRepository;
  private final VoucherLineRepository voucherLineRepository;
  private final VoucherService voucherService;
  private final VoucherPostingService voucherPostingService;
  private final AuditService auditService;
  private final VoucherAuditHelper voucherAuditHelper;

  public VoucherReversalServiceImpl(
      VoucherRepository voucherRepository,
      VoucherLineRepository voucherLineRepository,
      VoucherService voucherService,
      VoucherPostingService voucherPostingService,
      AuditService auditService,
      VoucherAuditHelper voucherAuditHelper) {
    this.voucherRepository = voucherRepository;
    this.voucherLineRepository = voucherLineRepository;
    this.voucherService = voucherService;
    this.voucherPostingService = voucherPostingService;
    this.auditService = auditService;
    this.voucherAuditHelper = voucherAuditHelper;
  }

  @Override
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public ReversalResult reverseVoucher(UUID voucherId, String description, String reason, HttpServletRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Reversal reason is required for audit");
    }

    // Load original voucher with company scoping
    Voucher originalVoucher = voucherRepository
        .findByCompanyIdAndId(companyId, voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Capture before snapshot for audit logging (original posted state)
    com.fasterxml.jackson.databind.JsonNode originalBeforeSnapshot = voucherAuditHelper.serializeVoucherToJson(originalVoucher);

    // Validate voucher status is POSTED
    if (!"posted".equals(originalVoucher.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Cannot reverse voucher: voucher must be in POSTED status. Current status: " + originalVoucher.getStatus());
    }

    // Check if voucher already reversed (block double reversal - AC #6)
    if (originalVoucher.getReversedByVoucherId() != null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot reverse voucher: voucher has already been reversed. Reversal voucher ID: " + originalVoucher.getReversedByVoucherId());
    }

    // Get current user ID
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Instant now = Instant.now();

    // Create reversal voucher
    Voucher reversalVoucher = new Voucher();
    reversalVoucher.setCompanyId(companyId);
    reversalVoucher.setVoucherNumber("REV-" + originalVoucher.getVoucherNumber());
    reversalVoucher.setVoucherDate(LocalDate.now()); // Current date (or next open period when available)
    // Use original voucher's period ID - period validation will be performed during auto-posting
    // TODO: Add period validation here or use current open period when PeriodService is available
    reversalVoucher.setPeriodId(originalVoucher.getPeriodId());
    reversalVoucher.setDescription("REV-" + originalVoucher.getDescription() + " - " + description);
    reversalVoucher.setStatus("draft"); // Will be auto-posted
    reversalVoucher.setCurrency(originalVoucher.getCurrency());
    reversalVoucher.setEnteredBy(currentUserId);
    reversalVoucher.setReversalOf(originalVoucher.getId()); // Link to original
    reversalVoucher.setCreatedAt(now);
    reversalVoucher.setUpdatedAt(now);

    // Get original voucher lines and create reversal lines with swapped amounts
    List<VoucherLine> originalLines = voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(originalVoucher.getId());
    List<VoucherLine> reversalLines = new ArrayList<>();
    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;

    int lineNumber = 1;
    for (VoucherLine originalLine : originalLines) {
      VoucherLine reversalLine = new VoucherLine();
      reversalLine.setLineNumber(lineNumber++);
      reversalLine.setAccountId(originalLine.getAccountId());
      // Swap debit and credit amounts
      reversalLine.setDebit(originalLine.getCredit());
      reversalLine.setCredit(originalLine.getDebit());
      reversalLine.setDescription(originalLine.getDescription());
      reversalLine.setCustomerId(originalLine.getCustomerId());
      reversalLine.setVendorId(originalLine.getVendorId());
      reversalLine.setCostCenterId(originalLine.getCostCenterId());
      reversalLine.setItemId(originalLine.getItemId());
      reversalLine.setCompanyId(companyId);
      reversalLines.add(reversalLine);

      totalDebit = totalDebit.add(reversalLine.getDebit());
      totalCredit = totalCredit.add(reversalLine.getCredit());
    }

    reversalVoucher.setTotalDebit(totalDebit);
    reversalVoucher.setTotalCredit(totalCredit);

    // Save reversal voucher
    reversalVoucher = voucherRepository.save(reversalVoucher);

    // Save reversal lines
    for (VoucherLine line : reversalLines) {
      line.setVoucherId(reversalVoucher.getId());
    }
    voucherLineRepository.saveAll(reversalLines);

    // Link bi-directionally: original.reversedByVoucherId = reversal
    originalVoucher.setReversedByVoucherId(reversalVoucher.getId());
    originalVoucher.setReversedBy(currentUserId); // User who created the reversal
    originalVoucher.setUpdatedAt(now);
    voucherRepository.save(originalVoucher);

    // Auto-post reversal voucher (AC #3)
    // Note: If auto-posting fails, the @Transactional annotation will automatically rollback
    // the entire transaction, including reversal voucher creation and bi-directional linking.
    // This ensures data consistency - either the entire reversal operation succeeds (including
    // auto-posting) or nothing is persisted.
    try {
      voucherPostingService.postVoucher(reversalVoucher.getId(), request);
    } catch (Exception e) {
      logger.error("Failed to auto-post reversal voucher: {}", reversalVoucher.getId(), e);
      // Exception will cause transaction rollback, cleaning up reversal voucher and unlinking
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Failed to auto-post reversal voucher: " + e.getMessage());
    }

    // Reload vouchers to get updated data
    VoucherDTO originalDTO = voucherService.getVoucherById(voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve original voucher"));
    VoucherDTO reversalDTO = voucherService.getVoucherById(reversalVoucher.getId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve reversal voucher"));

    logger.info(
        "Voucher reversed successfully. Original: {}, Reversal: {}, Reason: {}",
        originalVoucher.getVoucherNumber(),
        reversalVoucher.getVoucherNumber(),
        reason);

    // Enhanced audit logging for reversal operation with JSON snapshots and diff hash
      try {
      // Log original voucher reversal event
      com.fasterxml.jackson.databind.JsonNode originalAfterSnapshot = voucherAuditHelper.serializeVoucherToJson(originalVoucher);
      String originalDiffHash = voucherAuditHelper.calculateDiffHash(originalBeforeSnapshot, originalAfterSnapshot);
      auditService.logVoucherEvent(
            originalVoucher.getId(),
            originalVoucher.getVoucherNumber(),
          "VOUCHER_REVERSED",
          originalBeforeSnapshot,
          originalAfterSnapshot,
          originalDiffHash,
          request);
      
      // Log reversal voucher creation event
      com.fasterxml.jackson.databind.JsonNode reversalAfterSnapshot = voucherAuditHelper.serializeVoucherToJson(reversalVoucher);
      String reversalDiffHash = voucherAuditHelper.calculateDiffHash(null, reversalAfterSnapshot);
      auditService.logVoucherEvent(
            reversalVoucher.getId(),
            reversalVoucher.getVoucherNumber(),
          "VOUCHER_REVERSAL_CREATED",
          null, // before snapshot (null for create)
          reversalAfterSnapshot,
          reversalDiffHash,
            request);
      } catch (Exception e) {
      // Non-blocking: log error but don't break main flow
        logger.error("Failed to log voucher reversal to audit trail. Original Voucher ID: {}, Reversal Voucher ID: {}",
            originalVoucher.getId(), reversalVoucher.getId(), e);
    }

    return new ReversalResult(originalDTO, reversalDTO);
  }
}


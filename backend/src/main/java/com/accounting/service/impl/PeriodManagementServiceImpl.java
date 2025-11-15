package com.accounting.service.impl;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.PeriodCloseRequest;
import com.accounting.dto.PeriodReopenRequest;
import com.accounting.dto.PeriodSummaryDTO;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.util.VoucherAuditHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of PeriodManagementService.
 * Handles period close/reopen workflows, period queries, and period-voucher validation.
 */
@Service
public class PeriodManagementServiceImpl implements PeriodManagementService {

  private static final Logger logger = LoggerFactory.getLogger(PeriodManagementServiceImpl.class);

  private final AccountingPeriodRepository periodRepository;
  private final VoucherRepository voucherRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final VoucherAuditHelper voucherAuditHelper;
  private final ObjectMapper objectMapper;

  public PeriodManagementServiceImpl(
      AccountingPeriodRepository periodRepository,
      VoucherRepository voucherRepository,
      UserRepository userRepository,
      AuditService auditService,
      VoucherAuditHelper voucherAuditHelper,
      ObjectMapper objectMapper) {
    this.periodRepository = periodRepository;
    this.voucherRepository = voucherRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.voucherAuditHelper = voucherAuditHelper;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<AccountingPeriodDTO> getCurrentPeriod() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return periodRepository.findCurrentPeriodByCompanyId(companyId)
        .map(this::toDTO);
  }

  @Override
  public List<AccountingPeriodDTO> getOpenPeriods() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    LocalDate currentDate = LocalDate.now();
    List<AccountingPeriod> periods = periodRepository.findOpenPeriodsAroundDate(
        companyId, PeriodStatus.OPEN, currentDate);
    // Limit to current + 3 prior/next (7 total)
    return periods.stream()
        .limit(7)
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<AccountingPeriodDTO> getPeriodById(UUID periodId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return periodRepository.findByCompanyIdAndId(companyId, periodId)
        .map(this::toDTO);
  }

  @Override
  public Optional<PeriodSummaryDTO> getPeriodSummary(UUID periodId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    AccountingPeriod period = periodRepository.findByCompanyIdAndId(companyId, periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    Long draftCount = periodRepository.countDraftVouchersInPeriod(companyId, periodId);
    Long postedCount = voucherRepository.findByCompanyIdAndPeriodId(companyId, periodId).stream()
        .filter(v -> "posted".equals(v.getStatus()))
        .count();

    AccountingPeriod currentPeriod = periodRepository.findCurrentPeriodByCompanyId(companyId)
        .orElse(null);
    boolean isCurrentPeriod = currentPeriod != null && currentPeriod.getId().equals(periodId);

    PeriodSummaryDTO summary = new PeriodSummaryDTO(
        period.getPeriodName(),
        period.getStatus(),
        period.getStatus().getDisplayName(),
        period.getStartDate(),
        period.getEndDate(),
        draftCount,
        postedCount,
        draftCount > 0,
        period.getStatus() == PeriodStatus.OPEN ? "ACTIVE" : "BLOCKED",
        isCurrentPeriod
    );

    return Optional.of(summary);
  }

  @Override
  public Optional<AccountingPeriodDTO> findPeriodByDate(LocalDate date) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return periodRepository.findByCompanyIdAndDate(companyId, date)
        .map(this::toDTO);
  }

  @Override
  public boolean isPeriodOpen(UUID periodId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return periodRepository.findByCompanyIdAndId(companyId, periodId)
        .map(p -> p.getStatus() == PeriodStatus.OPEN && !p.isFuture())
        .orElse(false);
  }

  @Override
  public boolean isDateInOpenPeriod(LocalDate date) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return periodRepository.findByCompanyIdAndDate(companyId, date)
        .map(p -> p.getStatus() == PeriodStatus.OPEN && !p.isFuture())
        .orElse(false);
  }

  @Override
  @Transactional
  @SuppressWarnings("null") // Repository method returns non-null List, safe for saveAll
  public AccountingPeriodDTO closePeriod(UUID periodId, PeriodCloseRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    String reason = request.getReason();
    if (reason == null || reason.trim().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Close reason is required");
    }

    // Load period with company scoping
    AccountingPeriod period = periodRepository.findByCompanyIdAndId(companyId, periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Validate period is not already closed
    if (period.getStatus() == PeriodStatus.CLOSED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Period is already closed: " + period.getPeriodName());
    }

    // Validate period is not future
    if (period.isFuture()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot close future period: " + period.getPeriodName());
    }

    // Validate no DRAFT vouchers exist in period
    Long draftCount = periodRepository.countDraftVouchersInPeriod(companyId, periodId);
    if (draftCount > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Cannot close period: " + draftCount + " draft voucher(s) exist in period " + period.getPeriodName());
    }

    // Validate all posted vouchers are balanced (Dr=Cr per account)
    List<Voucher> vouchers = voucherRepository.findByCompanyIdAndPeriodId(companyId, periodId);
    for (Voucher voucher : vouchers) {
      if ("posted".equals(voucher.getStatus())) {
        if (voucher.getTotalDebit().compareTo(voucher.getTotalCredit()) != 0) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Cannot close period: Unbalanced voucher " + voucher.getVoucherNumber() +
                  " (Debit: " + voucher.getTotalDebit() + ", Credit: " + voucher.getTotalCredit() + ")");
        }
      }
    }

    // Capture before snapshot for audit logging
    JsonNode beforeSnapshot = serializePeriodToJson(period);

    // Close period atomically
    Long currentUserId = SecurityUtils.getCurrentUserId();
    period.closePeriod(currentUserId, reason);
    period = periodRepository.save(period);

    // Batch update all vouchers in period: add lock flag (prevent future edits)
    List<Voucher> vouchersInPeriod = voucherRepository.findByCompanyIdAndPeriodId(companyId, periodId);
    for (Voucher voucher : vouchersInPeriod) {
      voucher.setIsLocked(true);
    }
    voucherRepository.saveAll(vouchersInPeriod);

    // Capture after snapshot for audit logging
    JsonNode afterSnapshot = serializePeriodToJson(period);

    // Calculate hash digest
    String hashDigest = voucherAuditHelper.calculateDiffHash(beforeSnapshot, afterSnapshot);

    // Create audit log entry
    auditService.logPeriodClosed(periodId, reason, hashDigest);

    logger.info(
        "Period closed successfully. Period ID: {}, Period Name: {}, Closed By: {}, Locked {} vouchers",
        periodId,
        period.getPeriodName(),
        currentUserId,
        vouchersInPeriod.size());

    return toDTO(period);
  }

  @Override
  @Transactional
  @SuppressWarnings("null") // Repository method returns non-null List, safe for saveAll
  public AccountingPeriodDTO reopenPeriod(UUID periodId, PeriodReopenRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    String reason = request.getReason();
    String approvalMetadata = request.getApprovalMetadata();

    if (reason == null || reason.trim().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Reopen reason is required");
    }

    if (approvalMetadata == null || approvalMetadata.trim().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Approval metadata is required");
    }

    // Load period with company scoping
    AccountingPeriod period = periodRepository.findByCompanyIdAndId(companyId, periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Validate period is CLOSED before reopening
    if (period.getStatus() != PeriodStatus.CLOSED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Period is not closed: " + period.getPeriodName());
    }

    // Log reopen attempt (even if not approved) - always log for compliance
    String hashDigest = voucherAuditHelper.calculateSHA256(
        voucherAuditHelper.toJson(period) + reason + approvalMetadata);

    // Always log the reopen attempt
    auditService.logPeriodReopened(periodId, reason, approvalMetadata, hashDigest);

    // Reopen period atomically
    period.reopenPeriod();
    period = periodRepository.save(period);

    // Remove lock flag from all vouchers in period (allow edits)
    List<Voucher> vouchersInPeriod = voucherRepository.findByCompanyIdAndPeriodId(companyId, periodId);
    for (Voucher voucher : vouchersInPeriod) {
      voucher.setIsLocked(false);
    }
    voucherRepository.saveAll(vouchersInPeriod);

    logger.info(
        "Period reopened successfully. Period ID: {}, Period Name: {}, Reopened By: {}, Unlocked {} vouchers",
        periodId,
        period.getPeriodName(),
        SecurityUtils.getCurrentUserId(),
        vouchersInPeriod.size());

    return toDTO(period);
  }

  @Override
  public void validatePeriodForVoucherOperation(UUID periodId, String operation) {
    if (periodId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Period ID is required for " + operation);
    }

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    AccountingPeriod period = periodRepository.findByCompanyIdAndId(companyId, periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Check if period is closed
    if (period.getStatus() == PeriodStatus.CLOSED) {
      String errorMessage = "Cannot " + operation.toLowerCase() +
          " voucher in closed period: " + period.getPeriodName();
      auditService.logPeriodValidationBlocked(periodId, operation, "PERIOD_CLOSED");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
    }

    // Check if period is future
    if (period.isFuture()) {
      String errorMessage = "Cannot " + operation.toLowerCase() +
          " voucher in future period: " + period.getPeriodName();
      auditService.logPeriodValidationBlocked(periodId, operation, "PERIOD_FUTURE");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
    }
  }

  @Override
  public List<AccountingPeriodDTO> getAllPeriods() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return periodRepository.findByCompanyId(companyId).stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  public List<AccountingPeriodDTO> getPeriodsByFiscalYear(Integer fiscalYear) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return periodRepository.findByCompanyIdAndFiscalYear(companyId, fiscalYear).stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public AccountingPeriodDTO createPeriod(AccountingPeriod period) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    period.setCompanyId(companyId);

    // Validate no overlapping periods
    Long overlappingCount = periodRepository.countOverlappingPeriods(
        companyId, period.getStartDate(), period.getEndDate());
    if (overlappingCount > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Cannot create period: Overlapping period exists for date range " +
              period.getStartDate() + " to " + period.getEndDate());
    }

    AccountingPeriod saved = periodRepository.save(period);
    logger.info("Period created: {}", saved.getPeriodName());
    return toDTO(saved);
  }

  @Override
  @Transactional
  public AccountingPeriodDTO updatePeriod(UUID periodId, AccountingPeriod period) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    AccountingPeriod existing = periodRepository.findByCompanyIdAndId(companyId, periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Validate period is not closed (can't update closed periods)
    if (existing.getStatus() == PeriodStatus.CLOSED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot update closed period: " + existing.getPeriodName());
    }

    // Update fields
    existing.setFiscalYear(period.getFiscalYear());
    existing.setPeriodNumber(period.getPeriodNumber());
    existing.setPeriodName(period.getPeriodName());
    existing.setStartDate(period.getStartDate());
    existing.setEndDate(period.getEndDate());

    // Validate no overlapping periods (excluding current period)
    Long overlappingCount = periodRepository.countOverlappingPeriods(
        companyId, period.getStartDate(), period.getEndDate());
    if (overlappingCount > 1 || (overlappingCount == 1 && !existing.getId().equals(periodId))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Cannot update period: Overlapping period exists for date range " +
              period.getStartDate() + " to " + period.getEndDate());
    }

    AccountingPeriod updated = periodRepository.save(existing);
    logger.info("Period updated: {}", updated.getPeriodName());
    return toDTO(updated);
  }

  @Override
  @Transactional
  public void deletePeriod(UUID periodId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    AccountingPeriod period = periodRepository.findByCompanyIdAndId(companyId, periodId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Period not found: " + periodId));

    // Validate period is not closed
    if (period.getStatus() == PeriodStatus.CLOSED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot delete closed period: " + period.getPeriodName());
    }

    // Validate no vouchers exist in period
    List<Voucher> vouchers = voucherRepository.findByCompanyIdAndPeriodId(companyId, periodId);
    if (!vouchers.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Cannot delete period: " + vouchers.size() + " voucher(s) exist in period " + period.getPeriodName());
    }

    periodRepository.delete(period);
    logger.info("Period deleted: {}", period.getPeriodName());
  }

  /**
   * Convert AccountingPeriod entity to DTO.
   */
  private AccountingPeriodDTO toDTO(AccountingPeriod period) {
    String closedByName = null;
    if (period.getClosedBy() != null) {
      closedByName = getUserName(period.getClosedBy());
    }

    AccountingPeriodDTO dto = new AccountingPeriodDTO(
        period.getId(),
        period.getCompanyId(),
        period.getFiscalYear(),
        period.getPeriodNumber(),
        period.getPeriodName(),
        period.getStartDate(),
        period.getEndDate(),
        period.getStatus(),
        period.getStatus().getDisplayName(),
        period.getClosedBy(),
        closedByName,
        period.getClosedAt(),
        period.getCloseReason(),
        period.getCreatedAt(),
        period.getUpdatedAt(),
        period.getVersion()
    );
    return dto;
  }

  /**
   * Get user name by user ID.
   */
  private String getUserName(Long userId) {
    if (userId == null) {
      return null;
    }
    return userRepository.findById(userId)
        .map(User::getFullName)
        .orElse(null);
  }

  /**
   * Serialize period entity to JSON snapshot for audit logging.
   */
  private JsonNode serializePeriodToJson(AccountingPeriod period) {
    try {
      return objectMapper.valueToTree(period);
    } catch (Exception e) {
      logger.error("Failed to serialize period to JSON: {}", e.getMessage(), e);
      return objectMapper.createObjectNode();
    }
  }
}

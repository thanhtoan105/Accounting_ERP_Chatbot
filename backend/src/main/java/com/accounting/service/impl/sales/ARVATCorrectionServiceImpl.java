package com.accounting.service.impl.sales;

import com.accounting.dto.ARVATCorrectionCreateRequest;
import com.accounting.dto.ARVATCorrectionDTO;
import com.accounting.entity.ARVATCorrection;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.repository.ARVATCorrectionRepository;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ARVATCorrectionService;
import com.accounting.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of ARVATCorrectionService for AR VAT correction operations.
 */
@Service
@Transactional(readOnly = true)
public class ARVATCorrectionServiceImpl implements ARVATCorrectionService {

  private static final Logger logger = LoggerFactory.getLogger(ARVATCorrectionServiceImpl.class);
  private static final BigDecimal VAT_CORRECTION_THRESHOLD = new BigDecimal("10000000.00"); // 10M VND default
  private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

  private final ARVATCorrectionRepository arVatCorrectionRepository;
  private final SalesInvoiceRepository salesInvoiceRepository;
  private final SalesInvoiceLineRepository salesInvoiceLineRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;

  @Autowired
  public ARVATCorrectionServiceImpl(
      ARVATCorrectionRepository arVatCorrectionRepository,
      SalesInvoiceRepository salesInvoiceRepository,
      SalesInvoiceLineRepository salesInvoiceLineRepository,
      UserRepository userRepository,
      AuditService auditService) {
    this.arVatCorrectionRepository = arVatCorrectionRepository;
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.salesInvoiceLineRepository = salesInvoiceLineRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
  }

  @Override
  @PreAuthorize("hasAnyRole('ROLE_CHIEF_ACCOUNTANT', 'ROLE_CFO', 'ROLE_ADMIN')")
  @Transactional
  public ARVATCorrectionDTO createCorrection(ARVATCorrectionCreateRequest request) {
    Long companyId = requireCompanyId();
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction request is required");
    }

    UUID invoiceId = Objects.requireNonNull(request.getInvoiceId(), "invoiceId is required");

    SalesInvoice invoice = salesInvoiceRepository
        .findByCompanyIdAndId(companyId, invoiceId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Sales invoice not found: " + invoiceId));

    // Validate invoice is POSTED
    if (invoice.getStatus() != SalesInvoiceStatus.POSTED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "VAT corrections can only be created for POSTED invoices. Current status: " + invoice.getStatus());
    }

    SalesInvoiceLine line = null;
    if (request.getLineItemId() != null) {
      UUID lineId = request.getLineItemId();
      line = salesInvoiceLineRepository
          .findByCompanyIdAndId(companyId, lineId)
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Sales invoice line not found: " + lineId));
      if (!invoice.getId().equals(line.getSalesInvoiceId())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Line does not belong to the specified invoice");
      }
    }

    BigDecimal newAmount = safe(request.getNewVatAmount()).setScale(2, RoundingMode.HALF_UP);
    if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "New VAT amount must be zero or positive");
    }

    BigDecimal oldAmount = line != null ? safe(line.getVatAmount()) : safe(invoice.getVatAmount());
    if (newAmount.compareTo(oldAmount) == 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "New VAT amount must differ from existing amount");
    }

    BigDecimal variance = newAmount.subtract(oldAmount).abs();

    ARVATCorrection correction = new ARVATCorrection();
    correction.setCompanyId(companyId);
    correction.setInvoiceId(invoice.getId());
    correction.setLineItemId(line != null ? line.getId() : null);
    correction.setOldVatAmount(oldAmount);
    correction.setNewVatAmount(newAmount);
    correction.setReason(request.getReason());
    correction.setCorrectedById(getCurrentUserId());
    correction.setCorrectedAt(Instant.now());
    correction.setStatus(ARVATCorrection.Status.PENDING);

    ARVATCorrection saved = arVatCorrectionRepository.save(correction);

    // Log to audit
    auditService.logVatCorrectionCreated(
        companyId,
        saved.getCorrectedById(),
        saved.getId(),
        saved.getInvoiceId(),
        saved.getOldVatAmount(),
        saved.getNewVatAmount(),
        saved.getReason());

    logger.info(
        "Created AR VAT correction {} for invoice {}: {} -> {} (variance: {})",
        saved.getId(), invoiceId, oldAmount, newAmount, variance);

    return mapCorrectionToDTO(saved);
  }

  @Override
  @PreAuthorize("hasAnyRole('ROLE_CHIEF_ACCOUNTANT', 'ROLE_CFO', 'ROLE_ADMIN')")
  @Transactional
  public ARVATCorrectionDTO approveCorrection(UUID correctionId, Long approverId) {
    Long companyId = requireCompanyId();
    UUID targetCorrectionId = Objects.requireNonNull(correctionId, "correctionId is required");

    ARVATCorrection correction = arVatCorrectionRepository
        .findByCompanyIdAndId(companyId, targetCorrectionId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "AR VAT correction not found: " + targetCorrectionId));

    if (correction.getStatus() != ARVATCorrection.Status.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only pending corrections can be approved");
    }

    // Check if approval is required (variance > threshold)
    BigDecimal variance = correction.getNewVatAmount().subtract(correction.getOldVatAmount()).abs();
    if (variance.compareTo(VAT_CORRECTION_THRESHOLD) > 0) {
      // Require Chief Accountant or CFO role for large corrections
      ensureVatCorrectionRole();
    }

    SalesInvoice invoice = salesInvoiceRepository
        .findByCompanyIdAndId(companyId, correction.getInvoiceId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Sales invoice not found: " + correction.getInvoiceId()));

    // Validate invoice is still POSTED
    if (invoice.getStatus() != SalesInvoiceStatus.POSTED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Cannot approve correction for invoice with status: " + invoice.getStatus());
    }

    SalesInvoiceLine line = null;
    if (correction.getLineItemId() != null) {
      line = salesInvoiceLineRepository
          .findByCompanyIdAndId(companyId, correction.getLineItemId())
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND,
                  "Sales invoice line not found: " + correction.getLineItemId()));
    }

    BigDecimal newAmount = correction.getNewVatAmount();
    if (line != null) {
      line.setVatAmount(newAmount);
      salesInvoiceLineRepository.save(line);
      // Recalculate invoice VAT amount
      invoice.setVatAmount(recalculateInvoiceVatAmount(companyId, invoice.getId()));
    } else {
      invoice.setVatAmount(newAmount);
    }
    salesInvoiceRepository.save(invoice);

    // TODO: Regenerate voucher if invoice has been posted
    // This would require integration with VoucherService to update the voucher entries

    Long approver = approverId != null ? approverId : SecurityUtils.getCurrentUserId();
    correction.setStatus(ARVATCorrection.Status.APPROVED);
    correction.setApprovedById(approver);
    correction.setApprovedAt(Instant.now());
    ARVATCorrection saved = arVatCorrectionRepository.save(correction);

    auditService.logVatCorrectionApproved(
        companyId,
        approver,
        saved.getId(),
        saved.getInvoiceId(),
        saved.getOldVatAmount(),
        saved.getNewVatAmount());

    logger.info(
        "Approved AR VAT correction {} for invoice {}: {} -> {}",
        saved.getId(), invoice.getId(), saved.getOldVatAmount(), saved.getNewVatAmount());

    return mapCorrectionToDTO(saved);
  }

  @Override
  @PreAuthorize("hasAnyRole('ROLE_CHIEF_ACCOUNTANT', 'ROLE_CFO', 'ROLE_ADMIN')")
  @Transactional
  public ARVATCorrectionDTO rejectCorrection(UUID correctionId, String reason) {
    Long companyId = requireCompanyId();
    UUID targetCorrectionId = Objects.requireNonNull(correctionId, "correctionId is required");

    if (!StringUtils.hasText(reason)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Rejection reason is required");
    }

    ARVATCorrection correction = arVatCorrectionRepository
        .findByCompanyIdAndId(companyId, targetCorrectionId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "AR VAT correction not found: " + targetCorrectionId));

    if (correction.getStatus() != ARVATCorrection.Status.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only pending corrections can be rejected");
    }

    correction.setStatus(ARVATCorrection.Status.REJECTED);
    ARVATCorrection saved = arVatCorrectionRepository.save(correction);

    // Log rejection to audit
    Long userId = getCurrentUserId();
    auditService.logVatCorrectionCreated(
        companyId,
        userId,
        saved.getId(),
        saved.getInvoiceId(),
        saved.getOldVatAmount(),
        saved.getNewVatAmount(),
        "REJECTED: " + reason);

    logger.info("Rejected AR VAT correction {}: {}", saved.getId(), reason);

    return mapCorrectionToDTO(saved);
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public List<ARVATCorrectionDTO> getCorrections(UUID invoiceId, Map<String, Object> filters) {
    Long companyId = requireCompanyId();
    UUID targetInvoiceId = Objects.requireNonNull(invoiceId, "invoiceId is required");

    ARVATCorrection.Status statusFilter = resolveCorrectionStatus(filters);
    LocalDate startDate = parseLocalDate(filters != null ? filters.get("startDate") : null);
    LocalDate endDate = parseLocalDate(filters != null ? filters.get("endDate") : null);
    Long correctedById = parseLong(filters != null ? filters.get("correctedById") : null);
    Instant startInstant = toStartInstant(startDate);
    Instant endInstant = toEndInstant(endDate);

    List<ARVATCorrection> corrections;
    if (statusFilter != null) {
      corrections = arVatCorrectionRepository.findByCompanyIdAndInvoiceIdAndStatusOrderByCorrectedAtDesc(
          companyId, targetInvoiceId, statusFilter);
    } else {
      corrections = arVatCorrectionRepository.findByCompanyIdAndInvoiceIdOrderByCorrectedAtDesc(
          companyId, targetInvoiceId);
    }

    return corrections.stream()
        .filter(c -> filterByDateRange(c, startInstant, endInstant))
        .filter(c -> filterByUser(c, correctedById))
        .map(this::mapCorrectionToDTO)
        .collect(Collectors.toList());
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public Page<ARVATCorrectionDTO> getCorrections(
      Pageable pageable, UUID invoiceId, ARVATCorrection.Status status) {
    Long companyId = requireCompanyId();
    // TODO: Implement pagination with filters
    // For now, return empty page - will be implemented when needed
    return Page.empty(pageable);
  }

  /**
   * Map ARVATCorrection entity to DTO.
   */
  private ARVATCorrectionDTO mapCorrectionToDTO(ARVATCorrection correction) {
    ARVATCorrectionDTO dto = new ARVATCorrectionDTO();
    dto.setId(correction.getId());
    dto.setInvoiceId(correction.getInvoiceId());
    dto.setLineItemId(correction.getLineItemId());
    dto.setOldVatAmount(correction.getOldVatAmount());
    dto.setNewVatAmount(correction.getNewVatAmount());
    dto.setDifference(correction.getNewVatAmount().subtract(correction.getOldVatAmount()));
    dto.setReason(correction.getReason());
    dto.setStatus(correction.getStatus());
    dto.setCorrectedById(correction.getCorrectedById());
    dto.setCorrectedAt(correction.getCorrectedAt());
    dto.setApprovedById(correction.getApprovedById());
    dto.setApprovedAt(correction.getApprovedAt());

    // Load user names if available
    if (correction.getCorrectedById() != null) {
      userRepository.findById(correction.getCorrectedById())
          .ifPresent(user -> dto.setCorrectedByName(user.getEmail()));
    }
    if (correction.getApprovedById() != null) {
      userRepository.findById(correction.getApprovedById())
          .ifPresent(user -> dto.setApprovedByName(user.getEmail()));
    }

    return dto;
  }

  /**
   * Recalculate invoice VAT amount from line items.
   */
  private BigDecimal recalculateInvoiceVatAmount(Long companyId, UUID invoiceId) {
    return salesInvoiceLineRepository
        .findBySalesInvoiceIdOrderByLineNumberAsc(invoiceId)
        .stream()
        .map(SalesInvoiceLine::getVatAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * Ensure user has VAT correction role (Chief Accountant or CFO).
   */
  private void ensureVatCorrectionRole() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getAuthorities() == null) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Insufficient permissions for VAT corrections");
    }
    boolean allowed = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(
            authority -> "ROLE_CHIEF_ACCOUNTANT".equalsIgnoreCase(authority)
                || "ROLE_CFO".equalsIgnoreCase(authority)
                || "ROLE_ADMIN".equalsIgnoreCase(authority));
    if (!allowed) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only Chief Accountant, CFO, or Admin can perform VAT corrections");
    }
  }

  private Long requireCompanyId() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }
    return companyId;
  }

  private Long getCurrentUserId() {
    try {
      return SecurityUtils.getCurrentUserId();
    } catch (Exception e) {
      logger.warn("Failed to get current user ID: {}", e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user", e);
    }
  }

  private BigDecimal safe(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private ARVATCorrection.Status resolveCorrectionStatus(Map<String, Object> filters) {
    if (filters == null || filters.isEmpty()) {
      return null;
    }
    Object statusObj = filters.get("status");
    if (statusObj instanceof ARVATCorrection.Status status) {
      return status;
    }
    if (statusObj instanceof String text && StringUtils.hasText(text)) {
      try {
        return ARVATCorrection.Status.valueOf(text.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
        logger.warn("Unknown AR VAT correction status filter: {}", text);
      }
    }
    return null;
  }

  private LocalDate parseLocalDate(Object value) {
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    if (value instanceof java.sql.Date sqlDate) {
      return sqlDate.toLocalDate();
    }
    if (value instanceof String text && StringUtils.hasText(text)) {
      return LocalDate.parse(text.trim());
    }
    return null;
  }

  private Instant toStartInstant(LocalDate date) {
    if (date == null) {
      return null;
    }
    return date.atStartOfDay(DEFAULT_ZONE).toInstant();
  }

  private Instant toEndInstant(LocalDate date) {
    if (date == null) {
      return null;
    }
    return date.atTime(LocalTime.MAX).atZone(DEFAULT_ZONE).toInstant();
  }

  private boolean filterByDateRange(ARVATCorrection correction, Instant start, Instant end) {
    Instant correctedAt = correction.getCorrectedAt();
    if (start != null && (correctedAt == null || correctedAt.isBefore(start))) {
      return false;
    }
    if (end != null && (correctedAt == null || correctedAt.isAfter(end))) {
      return false;
    }
    return true;
  }

  private boolean filterByUser(ARVATCorrection correction, Long correctedById) {
    if (correctedById == null) {
      return true;
    }
    return Objects.equals(correction.getCorrectedById(), correctedById);
  }

  private Long parseLong(Object value) {
    if (value instanceof Long l) {
      return l;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && StringUtils.hasText(text)) {
      return Long.parseLong(text.trim());
    }
    return null;
  }
}


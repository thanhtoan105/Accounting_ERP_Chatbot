package com.accounting.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.VoucherCountDTO;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.dto.VoucherLineDTO;
import com.accounting.dto.VoucherListDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.exception.VoucherValidationException;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.service.AuditService;
import com.accounting.service.EmbeddingTriggerService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.VoucherService;
import com.accounting.service.VoucherValidationService;
import com.accounting.service.util.VoucherAuditHelper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.Predicate;

/**
 * Implementation of VoucherService for voucher operations.
 */
@Service
@Transactional
public class VoucherServiceImpl implements VoucherService {

  private static final Logger logger = LoggerFactory.getLogger(VoucherServiceImpl.class);

  private final VoucherRepository voucherRepository;
  private final VoucherLineRepository voucherLineRepository;
  private final UserRepository userRepository;
  private final CustomerRepository customerRepository;
  private final SupplierRepository supplierRepository;
  private final AuditService auditService;
  private final JwtTokenProvider jwtTokenProvider;
  private final VoucherValidationService voucherValidationService;
  private final VoucherAuditHelper voucherAuditHelper;
  private final PeriodManagementService periodManagementService;
  private final EmbeddingTriggerService embeddingTriggerService;

  @PersistenceContext
  private EntityManager entityManager;

  public VoucherServiceImpl(
      VoucherRepository voucherRepository,
      VoucherLineRepository voucherLineRepository,
      UserRepository userRepository,
      CustomerRepository customerRepository,
      SupplierRepository supplierRepository,
      AuditService auditService,
      JwtTokenProvider jwtTokenProvider,
      VoucherValidationService voucherValidationService,
      VoucherAuditHelper voucherAuditHelper,
      PeriodManagementService periodManagementService,
      EmbeddingTriggerService embeddingTriggerService) {
    this.voucherRepository = voucherRepository;
    this.voucherLineRepository = voucherLineRepository;
    this.userRepository = userRepository;
    this.customerRepository = customerRepository;
    this.supplierRepository = supplierRepository;
    this.auditService = auditService;
    this.jwtTokenProvider = jwtTokenProvider;
    this.voucherValidationService = voucherValidationService;
    this.voucherAuditHelper = voucherAuditHelper;
    this.periodManagementService = periodManagementService;
    this.embeddingTriggerService = embeddingTriggerService;
  }

  @Override
  public Page<VoucherListDTO> findAll(
      Pageable pageable, String status, LocalDate dateFrom, LocalDate dateTo, String search, Long accountId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Build specification with company scope and optional filters
    Specification<Voucher> spec = (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always filter by company
      predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));

      // Filter by status
      if (status != null && !status.isBlank()) {
        predicates.add(criteriaBuilder.equal(root.get("status"), status));
      }

      // Filter by date range
      if (dateFrom != null) {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("voucherDate"), dateFrom));
      }
      if (dateTo != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("voucherDate"), dateTo));
      }

      // Search by voucher number or description (unaccented Vietnamese support)
      if (search != null && !search.isBlank()) {
        // Use native PostgreSQL unaccent_search function for accurate Vietnamese
        // matching
        List<UUID> matchingIds = voucherRepository.findIdsByCompanyIdAndSearchTerm(
            companyId, search.trim());
        if (matchingIds.isEmpty()) {
          // No matches found, return empty result by adding impossible condition
          predicates.add(criteriaBuilder.equal(root.get("id"), UUID.randomUUID()));
        } else {
          // Filter to only matching IDs
          predicates.add(root.get("id").in(matchingIds));
        }
      }

      // Filter by account ID (if provided, filter vouchers that have lines with this
      // account)
      if (accountId != null) {
        List<UUID> voucherIdsWithAccount = voucherLineRepository
            .findByCompanyIdAndAccountId(companyId, accountId)
            .stream()
            .map(VoucherLine::getVoucherId)
            .distinct()
            .collect(Collectors.toList());
        if (voucherIdsWithAccount.isEmpty()) {
          // No vouchers with this account, return empty result
          predicates.add(criteriaBuilder.equal(root.get("id"), UUID.randomUUID()));
        } else {
          predicates.add(root.get("id").in(voucherIdsWithAccount));
        }
      }

      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };

    logger.info("[VoucherService] Pageable received: {}", pageable);
    logger.info("[VoucherService] Sort from pageable: {}", pageable.getSort());
    
    Page<Voucher> vouchers = voucherRepository.findAll(spec, pageable);
    
    // Debug: print first 5 voucher dates to verify order
    logger.info("[VoucherService] First 5 voucher dates:");
    vouchers.getContent().stream().limit(5).forEach(v -> 
        logger.info("  - {}: {}", v.getVoucherNumber(), v.getVoucherDate()));
    
    return vouchers.map(this::toListDTO);
  }

  @Override
  public List<VoucherDTO> search(String searchTerm) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Use findAll with search filter (no pagination for search)
    Page<VoucherListDTO> results = findAll(
        Pageable.unpaged(),
        null, // status
        null, // dateFrom
        null, // dateTo
        searchTerm,
        null); // accountId

    // Convert ListDTO to full DTO
    return results.getContent().stream()
        .map(
            listDto -> getVoucherById(listDto.getId())
                .orElseThrow(
                    () -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Voucher not found: " + listDto.getId())))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<VoucherDTO> getVoucherById(UUID voucherId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    return voucherRepository
        .findByCompanyIdAndId(companyId, voucherId)
        .map(this::toDTO);
  }

  @Override
  public VoucherCountDTO getCounts() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    long draftCount = voucherRepository.countByCompanyIdAndStatus(companyId, "draft");
    long postedCount = voucherRepository.countByCompanyIdAndStatus(companyId, "posted");
    long unpostedCount = voucherRepository.countByCompanyIdAndStatus(companyId, "unposted");

    return new VoucherCountDTO(draftCount, postedCount, unpostedCount);
  }

  @Override
  public VoucherDTO create(VoucherCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Get current user ID
    Long enteredBy = getCurrentUserId();

    // Validate voucher using VoucherValidationService
    VoucherValidationResult validationResult = voucherValidationService.validate(request);
    if (!validationResult.isValid()) {
      throwValidationException(validationResult);
    }

    // Auto-determine period from voucher date if not provided
    UUID periodId = request.getPeriodId();
    if (periodId == null) {
      Optional<AccountingPeriodDTO> periodOpt = periodManagementService.findPeriodByDate(request.getDate());
      if (periodOpt.isPresent()) {
        periodId = periodOpt.get().getId();
      } else {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Cannot create voucher: No period found for date " + request.getDate());
      }
    }

    // Validate period is open for voucher creation
    validatePeriodForVoucher(request.getDate(), periodId);

    // Generate voucher number using database function
    int year = request.getDate().getYear();
    String voucherNumber = generateVoucherNumber(year);

    // Create voucher entity
    Voucher voucher = new Voucher();
    voucher.setCompanyId(companyId);
    voucher.setVoucherNumber(voucherNumber);
    voucher.setVoucherDate(request.getDate());
    voucher.setPeriodId(periodId);
    voucher.setDescription(request.getDescription());
    voucher.setStatus("draft"); // Always create as draft
    voucher.setCurrency(
        request.getCurrency() != null ? request.getCurrency() : "VND");
    voucher.setEnteredBy(enteredBy);
    voucher.setCreatedAt(Instant.now());
    voucher.setUpdatedAt(Instant.now());

    List<VoucherLineDTO> ledgerLines = resolveLedgerLines(request);
    if (ledgerLines == null || ledgerLines.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "At least one voucher line is required");
    }

    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    for (VoucherLineDTO lineDto : ledgerLines) {
      totalDebit = totalDebit.add(lineDto.getDebit() != null ? lineDto.getDebit() : BigDecimal.ZERO);
      totalCredit = totalCredit.add(lineDto.getCredit() != null ? lineDto.getCredit() : BigDecimal.ZERO);
    }
    voucher.setTotalDebit(totalDebit);
    voucher.setTotalCredit(totalCredit);

    // Save voucher
    voucher = voucherRepository.save(voucher);

    // Save voucher lines
    int lineNumber = 1;
    List<VoucherLine> lines = new ArrayList<>();
    for (VoucherLineDTO lineDto : ledgerLines) {
      VoucherLine line = new VoucherLine();
      line.setVoucherId(voucher.getId());
      line.setLineNumber(lineNumber++);
      line.setAccountId(lineDto.getAccountId());
      line.setDebit(lineDto.getDebit() != null ? lineDto.getDebit() : BigDecimal.ZERO);
      line.setCredit(lineDto.getCredit() != null ? lineDto.getCredit() : BigDecimal.ZERO);
      line.setDescription(lineDto.getDescription());
      line.setCustomerId(lineDto.getCustomerId());
      line.setSupplierId(lineDto.getSupplierId());
      line.setItemId(lineDto.getItemId());
      line.setCompanyId(companyId);
      lines.add(line);
    }
    voucherLineRepository.saveAll(lines);

    // Trigger embedding for RAG chatbot
    embeddingTriggerService.triggerVoucherEmbedding(voucher, lines, EmbeddingAction.UPSERT);

    // Log audit event for voucher creation
    try {
      com.fasterxml.jackson.databind.JsonNode afterSnapshot = voucherAuditHelper.serializeVoucherToJson(voucher);
      String diffHash = voucherAuditHelper.calculateDiffHash(null, afterSnapshot);
      auditService.logVoucherEvent(
          voucher.getId(),
          voucher.getVoucherNumber(),
          "VOUCHER_CREATED",
          null, // before snapshot (null for create)
          afterSnapshot,
          diffHash,
          null); // HttpServletRequest not available in service layer
    } catch (Exception e) {
      // Non-blocking: log error but don't break main flow
      logger.error("Failed to log audit event for voucher creation: {}", e.getMessage(), e);
    }

    logger.info("Created voucher: {} with {} lines", voucherNumber, lines.size());
    return toDTO(voucher);
  }

  @Override
  public VoucherDTO update(UUID voucherId, VoucherCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Find existing voucher
    Voucher voucher = voucherRepository
        .findByCompanyIdAndId(companyId, voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Capture before snapshot for audit logging
    com.fasterxml.jackson.databind.JsonNode beforeSnapshot = voucherAuditHelper.serializeVoucherToJson(voucher);

    // Validate voucher is in draft status
    if (!"draft".equals(voucher.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot update voucher: only draft vouchers can be updated. Current status: "
              + voucher.getStatus());
    }

    // Optimistic locking: JPA will automatically check version on save
    // If version mismatch, OptimisticLockException will be thrown by JPA

    // Validate voucher using VoucherValidationService
    VoucherValidationResult validationResult = voucherValidationService.validate(request);
    if (!validationResult.isValid()) {
      throwValidationException(validationResult);
    }

    // Auto-determine period from voucher date if not provided
    UUID periodId = request.getPeriodId();
    if (periodId == null) {
      Optional<AccountingPeriodDTO> periodOpt = periodManagementService.findPeriodByDate(request.getDate());
      if (periodOpt.isPresent()) {
        periodId = periodOpt.get().getId();
      } else {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Cannot update voucher: No period found for date " + request.getDate());
      }
    }

    // Validate period is open for voucher update
    validatePeriodForVoucher(request.getDate(), periodId);

    // Update voucher fields (voucher number is not changed on update)
    voucher.setVoucherDate(request.getDate());
    voucher.setPeriodId(periodId);
    voucher.setDescription(request.getDescription());
    if (request.getCurrency() != null) {
      voucher.setCurrency(request.getCurrency());
    }
    voucher.setUpdatedAt(Instant.now());

    List<VoucherLineDTO> ledgerLines = resolveLedgerLines(request);
    if (ledgerLines == null || ledgerLines.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "At least one voucher line is required");
    }

    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    for (VoucherLineDTO lineDto : ledgerLines) {
      totalDebit = totalDebit.add(lineDto.getDebit() != null ? lineDto.getDebit() : BigDecimal.ZERO);
      totalCredit = totalCredit.add(lineDto.getCredit() != null ? lineDto.getCredit() : BigDecimal.ZERO);
    }
    voucher.setTotalDebit(totalDebit);
    voucher.setTotalCredit(totalCredit);

    // Delete existing lines and flush to ensure deletion completes before insert
    voucherLineRepository.deleteByVoucherId(voucherId);
    entityManager.flush(); // Flush delete operation before inserting new lines

    // Save updated voucher
    voucher = voucherRepository.save(voucher);

    // Save new voucher lines
    int lineNumber = 1;
    List<VoucherLine> lines = new ArrayList<>();
    for (VoucherLineDTO lineDto : ledgerLines) {
      VoucherLine line = new VoucherLine();
      line.setVoucherId(voucher.getId());
      line.setLineNumber(lineNumber++);
      line.setAccountId(lineDto.getAccountId());
      line.setDebit(lineDto.getDebit() != null ? lineDto.getDebit() : BigDecimal.ZERO);
      line.setCredit(lineDto.getCredit() != null ? lineDto.getCredit() : BigDecimal.ZERO);
      line.setDescription(lineDto.getDescription());
      line.setCustomerId(lineDto.getCustomerId());
      line.setSupplierId(lineDto.getSupplierId());
      line.setItemId(lineDto.getItemId());
      line.setCompanyId(companyId);
      lines.add(line);
    }
    voucherLineRepository.saveAll(lines);

    // Trigger embedding for RAG chatbot
    embeddingTriggerService.triggerVoucherEmbedding(voucher, lines, EmbeddingAction.UPSERT);

    // Log audit event for voucher update
    try {
      com.fasterxml.jackson.databind.JsonNode afterSnapshot = voucherAuditHelper.serializeVoucherToJson(voucher);
      String diffHash = voucherAuditHelper.calculateDiffHash(beforeSnapshot, afterSnapshot);
      auditService.logVoucherEvent(
          voucher.getId(),
          voucher.getVoucherNumber(),
          "VOUCHER_UPDATED",
          beforeSnapshot,
          afterSnapshot,
          diffHash,
          null); // HttpServletRequest not available in service layer
    } catch (Exception e) {
      // Non-blocking: log error but don't break main flow
      logger.error("Failed to log audit event for voucher update: {}", e.getMessage(), e);
    }

    logger.info("Updated voucher: {} with {} lines", voucher.getVoucherNumber(), lines.size());
    return toDTO(voucher);
  }

  /**
   * Generate voucher number using database function.
   *
   * @param year year for voucher number
   * @return formatted voucher number (VC{YYYY}-{seq})
   */
  private String generateVoucherNumber(int year) {
    Query query = entityManager.createNativeQuery(
        "SELECT get_next_voucher_number(:year)");
    query.setParameter("year", year);
    return (String) query.getSingleResult();
  }

  /**
   * Get current user ID from security context.
   *
   * @return user ID
   */
  private Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user");
    }
    try {
      return Long.parseLong(authentication.getPrincipal().toString());
    } catch (NumberFormatException e) {
      logger.error("Failed to parse user ID from authentication principal", e);
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user");
    }
  }

  /**
   * Throw validation exception with detailed error map.
   *
   * @param validationResult validation result with errors
   */
  private void throwValidationException(VoucherValidationResult validationResult) {
    throw new VoucherValidationException(validationResult);
  }

  private List<VoucherLineDTO> resolveLedgerLines(VoucherCreateRequest request) {
    if (request.getEntryLines() != null && !request.getEntryLines().isEmpty()) {
      return convertEntryLines(request.getEntryLines());
    }
    return request.getLines();
  }

  private List<VoucherLineDTO> convertEntryLines(List<VoucherEntryLineRequest> entryLines) {
    List<VoucherLineDTO> lines = new ArrayList<>();
    if (entryLines == null) {
      return lines;
    }

    for (VoucherEntryLineRequest entry : entryLines) {
      BigDecimal amount = entry.getAmount() != null ? entry.getAmount() : BigDecimal.ZERO;

      VoucherLineDTO debitLine = new VoucherLineDTO();
      debitLine.setAccountId(entry.getDebitAccountId());
      debitLine.setDebit(amount);
      debitLine.setCredit(BigDecimal.ZERO);
      debitLine.setDescription(entry.getDescription());
      debitLine.setCustomerId(entry.getCustomerId());
      debitLine.setSupplierId(entry.getSupplierId());
      debitLine.setItemId(entry.getItemId());
      lines.add(debitLine);

      VoucherLineDTO creditLine = new VoucherLineDTO();
      creditLine.setAccountId(entry.getCreditAccountId());
      creditLine.setDebit(BigDecimal.ZERO);
      creditLine.setCredit(amount);
      creditLine.setDescription(entry.getDescription());
      creditLine.setCustomerId(entry.getCustomerId());
      creditLine.setSupplierId(entry.getSupplierId());
      creditLine.setItemId(entry.getItemId());
      lines.add(creditLine);
    }

    return lines;
  }

  @Override
  public void delete(UUID voucherId, String reason, jakarta.servlet.http.HttpServletRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Deletion reason is required");
    }

    // Find voucher
    Voucher voucher = voucherRepository
        .findByCompanyIdAndId(companyId, voucherId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Validate voucher is in draft status - posted vouchers cannot be deleted (AC
    // #7)
    if ("posted".equals(voucher.getStatus())) {
      // Log blocked deletion attempt in audit trail
      Long deletedByUserId = null;
      String authHeader = request.getHeader("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        try {
          deletedByUserId = jwtTokenProvider.getUserIdFromToken(token);
        } catch (Exception e) {
          logger.warn("Failed to extract user ID from JWT token for blocked deletion audit log", e);
        }
      }
      if (deletedByUserId != null) {
        try {
          auditService.logVoucherDeleted(
              voucherId, voucher.getVoucherNumber(), "Blocked: Posted voucher cannot be deleted", deletedByUserId,
              request);
        } catch (Exception e) {
          logger.error("Failed to log blocked deletion to audit trail", e);
        }
      }
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot delete posted voucher. Only draft vouchers can be deleted.");
    }

    // Also block unposted vouchers that are not draft
    if (!"draft".equals(voucher.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot delete voucher: only draft vouchers can be deleted. Current status: "
              + voucher.getStatus());
    }

    // Validate voucher is not referenced
    if (voucherRepository.isReferenced(voucherId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot delete voucher: voucher is referenced by other vouchers (reversals or other references)");
    }

    // Delete voucher
    voucherRepository.delete(voucher);

    // Log deletion to audit trail
    // Get current user ID from JWT token in Authorization header
    Long deletedByUserId = null;
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      try {
        deletedByUserId = jwtTokenProvider.getUserIdFromToken(token);
      } catch (Exception e) {
        logger.warn(
            "Failed to extract user ID from JWT token for voucher deletion audit log. Voucher ID: {}, Voucher Number: {}, Error: {}",
            voucherId,
            voucher.getVoucherNumber(),
            e.getMessage());
        // Continue with deletion but audit log will be missing - this is a compliance
        // risk
        // Consider failing deletion if audit is critical for your compliance
        // requirements
      }
    } else {
      logger.warn(
          "Missing or invalid Authorization header for voucher deletion audit log. Voucher ID: {}, Voucher Number: {}",
          voucherId,
          voucher.getVoucherNumber());
    }
    if (deletedByUserId != null) {
      try {
        auditService.logVoucherDeleted(
            voucherId, voucher.getVoucherNumber(), reason, deletedByUserId, request);
      } catch (Exception e) {
        logger.error(
            "Failed to log voucher deletion to audit trail. Voucher ID: {}, Voucher Number: {}, Error: {}",
            voucherId,
            voucher.getVoucherNumber(),
            e.getMessage(),
            e);
        // Audit logging failure is critical for compliance - consider failing deletion
        // here
        // For now, we log the error and allow deletion to proceed
      }
    }
  }

  /**
   * Convert entity to list DTO (lightweight for list view).
   */
  private VoucherListDTO toListDTO(Voucher voucher) {
    String enteredByName = getUserName(voucher.getEnteredBy());
    String postedByName = voucher.getPostedBy() != null ? getUserName(voucher.getPostedBy()) : null;

    // Extract AR/AP Entity (customer or supplier name) from voucher lines
    String arApEntity = null;
    Long companyId = voucher.getCompanyId();
    List<VoucherLine> lines = voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucher.getId());

    // Find first line with customer or vendor ID
    for (VoucherLine line : lines) {
      if (line.getCustomerId() != null) {
        arApEntity = customerRepository
            .findByCompanyIdAndId(companyId, line.getCustomerId())
            .map(customer -> customer.getName())
            .orElse(null);
        break;
      } else if (line.getSupplierId() != null) {
        arApEntity = supplierRepository
            .findByCompanyIdAndId(companyId, line.getSupplierId())
            .map(supplier -> supplier.getName())
            .orElse(null);
        break;
      }
    }

    return new VoucherListDTO(
        voucher.getId(),
        voucher.getVoucherNumber(),
        voucher.getVoucherDate(),
        voucher.getDescription(), // Use description as type for now
        voucher.getTotalDebit(),
        voucher.getTotalCredit(),
        voucher.getStatus(),
        enteredByName,
        postedByName,
        arApEntity,
        voucher.getReversalOf() != null, // Has reversal badge
        voucher.getReversedByVoucherId(), // Reversal voucher ID for navigation
        0, // Attachment count - placeholder (deferred to Epic 4/5)
        voucher.getCurrency());
  }

  /**
   * Convert entity to full DTO (with lines).
   */
  private VoucherDTO toDTO(Voucher voucher) {
    String enteredByName = getUserName(voucher.getEnteredBy());
    String postedByName = voucher.getPostedBy() != null ? getUserName(voucher.getPostedBy()) : null;
    String reversedByName = voucher.getReversedBy() != null ? getUserName(voucher.getReversedBy()) : null;

    // Load voucher lines
    List<VoucherLine> lines = voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucher.getId());
    List<VoucherLineDTO> lineDTOs = lines.stream()
        .map(this::toLineDTO)
        .collect(Collectors.toList());

    VoucherDTO dto = new VoucherDTO(
        voucher.getId(),
        voucher.getCompanyId(),
        voucher.getVoucherNumber(),
        voucher.getVoucherDate(),
        voucher.getPeriodId(),
        voucher.getDescription(),
        voucher.getStatus(),
        voucher.getCurrency(),
        voucher.getTotalDebit(),
        voucher.getTotalCredit(),
        voucher.getEnteredBy(),
        enteredByName,
        voucher.getPostedBy(),
        postedByName,
        voucher.getPostedAt(),
        voucher.getReversalOf(),
        voucher.getReversedByVoucherId(),
        voucher.getReversedBy(),
        reversedByName,
        voucher.getCreatedAt(),
        voucher.getUpdatedAt(),
        voucher.getVersion(),
        0 // Attachment count - placeholder
    );
    dto.setLines(lineDTOs);
    return dto;
  }

  /**
   * Convert VoucherLine entity to DTO.
   */
  private VoucherLineDTO toLineDTO(VoucherLine line) {
    VoucherLineDTO dto = new VoucherLineDTO();
    dto.setLineNumber(line.getLineNumber());
    dto.setAccountId(line.getAccountId());
    dto.setDebit(line.getDebit());
    dto.setCredit(line.getCredit());
    dto.setDescription(line.getDescription());
    dto.setCustomerId(line.getCustomerId());
    dto.setSupplierId(line.getSupplierId());
    dto.setItemId(line.getItemId());
    return dto;
  }

  /**
   * Get user name by user ID.
   */
  private String getUserName(Long userId) {
    if (userId == null) {
      return null;
    }
    return userRepository
        .findById(userId)
        .map(User::getFullName)
        .orElse("Unknown");
  }

  /**
   * Validate that voucher date is in an open period.
   * Logs blocked attempts in audit trail.
   *
   * @param voucherDate voucher date to validate
   * @param periodId    optional period ID (if provided, validates specific
   *                    period)
   * @return true if period is open, throws ResponseStatusException if closed
   */
  private boolean validatePeriodForVoucher(LocalDate voucherDate, UUID periodId) {
    try {
      // First validate that date is in an open period
      if (!periodManagementService.isDateInOpenPeriod(voucherDate)) {
        // Find the period for this date to get period details for error message
        Optional<com.accounting.dto.AccountingPeriodDTO> periodOpt = periodManagementService
            .findPeriodByDate(voucherDate);

        String periodName = periodOpt
            .map(com.accounting.dto.AccountingPeriodDTO::getPeriodName)
            .orElse("Unknown Period");

        // Log the blocked attempt in audit trail
        try {
          auditService.logPeriodValidationBlocked(
              periodOpt.map(com.accounting.dto.AccountingPeriodDTO::getId).orElse(null),
              "VOUCHER_CREATION",
              "Cannot create voucher in closed or future period: " + periodName);
        } catch (Exception e) {
          logger.error("Failed to log period validation block to audit trail", e);
        }

        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Cannot create voucher in closed or future period: " + periodName);
      }

      // Validate that the period is open
      if (!periodManagementService.isPeriodOpen(periodId)) {
        Optional<AccountingPeriodDTO> periodOpt = periodManagementService.getPeriodById(periodId);

        String periodName = periodOpt
            .map(AccountingPeriodDTO::getPeriodName)
            .orElse("Unknown Period");

        // Log the blocked attempt in audit trail
        try {
          auditService.logPeriodValidationBlocked(
              periodId,
              "VOUCHER_CREATION",
              "Cannot create voucher in closed period: " + periodName);
        } catch (Exception e) {
          logger.error("Failed to log period validation block to audit trail", e);
        }

        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Cannot create voucher in closed period: " + periodName);
      }

      return true;
    } catch (ResponseStatusException e) {
      // Re-throw ResponseStatusException (period closed)
      throw e;
    } catch (Exception e) {
      logger.error("Failed to validate period for voucher", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Failed to validate period for voucher creation");
    }
  }

  @Override
  public VoucherDTO postSalesInvoiceVoucher(
      com.accounting.entity.SalesInvoice salesInvoice, Long userId) {
    // This method signature exists for test compatibility.
    // The actual implementation is in
    // SalesInvoiceApprovalServiceImpl.postSalesInvoiceVoucher()
    // which creates the voucher and posts it via VoucherPostingService.
    throw new UnsupportedOperationException(
        "Use SalesInvoiceApprovalService.approve() or SalesInvoiceApprovalService.submitForApproval() "
            + "to post sales invoice vouchers. Direct voucher posting for sales invoices is not supported.");
  }
}

package com.accounting.service.impl;

import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherCountDTO;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherLineDTO;
import com.accounting.dto.VoucherListDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.service.AuditService;
import com.accounting.service.VoucherService;
import com.accounting.service.VoucherValidationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private final AuditService auditService;
  private final JwtTokenProvider jwtTokenProvider;
  private final VoucherValidationService voucherValidationService;

  @PersistenceContext
  private EntityManager entityManager;

  public VoucherServiceImpl(
      VoucherRepository voucherRepository,
      VoucherLineRepository voucherLineRepository,
      UserRepository userRepository,
      AuditService auditService,
      JwtTokenProvider jwtTokenProvider,
      VoucherValidationService voucherValidationService) {
    this.voucherRepository = voucherRepository;
    this.voucherLineRepository = voucherLineRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.jwtTokenProvider = jwtTokenProvider;
    this.voucherValidationService = voucherValidationService;
  }

  @Override
  public Page<VoucherListDTO> findAll(
      Pageable pageable, String status, LocalDate dateFrom, LocalDate dateTo, String search) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Build specification with company scope and optional filters
    Specification<Voucher> spec =
        (root, query, criteriaBuilder) -> {
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
            // Use native PostgreSQL unaccent_search function for accurate Vietnamese matching
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

          return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

    Page<Voucher> vouchers = voucherRepository.findAll(spec, pageable);
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
    Page<VoucherListDTO> results =
        findAll(
            Pageable.unpaged(),
            null, // status
            null, // dateFrom
            null, // dateTo
            searchTerm);

    // Convert ListDTO to full DTO
    return results.getContent().stream()
        .map(
            listDto ->
                getVoucherById(listDto.getId())
                    .orElseThrow(
                        () ->
                            new ResponseStatusException(
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

    // TODO: Validate period is open (PeriodService integration when available)
    // For now, we skip period validation - it should be added when PeriodService is implemented

    // Generate voucher number using database function
    int year = request.getDate().getYear();
    String voucherNumber = generateVoucherNumber(year);

    // Create voucher entity
    Voucher voucher = new Voucher();
    voucher.setCompanyId(companyId);
    voucher.setVoucherNumber(voucherNumber);
    voucher.setVoucherDate(request.getDate());
    voucher.setPeriodId(request.getPeriodId());
    voucher.setDescription(request.getDescription());
    voucher.setStatus("draft"); // Always create as draft
    voucher.setCurrency("VND");
    voucher.setEnteredBy(enteredBy);
    voucher.setCreatedAt(Instant.now());
    voucher.setUpdatedAt(Instant.now());

    // Calculate totals from lines
    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    for (VoucherLineDTO lineDto : request.getLines()) {
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
    for (VoucherLineDTO lineDto : request.getLines()) {
      VoucherLine line = new VoucherLine();
      line.setVoucherId(voucher.getId());
      line.setLineNumber(lineNumber++);
      line.setAccountId(lineDto.getAccountId());
      line.setDebit(lineDto.getDebit() != null ? lineDto.getDebit() : BigDecimal.ZERO);
      line.setCredit(lineDto.getCredit() != null ? lineDto.getCredit() : BigDecimal.ZERO);
      line.setDescription(lineDto.getDescription());
      line.setCustomerId(lineDto.getCustomerId());
      line.setVendorId(lineDto.getVendorId());
      line.setCostCenterId(lineDto.getCostCenterId());
      line.setItemId(lineDto.getItemId());
      line.setCompanyId(companyId);
      lines.add(line);
    }
    voucherLineRepository.saveAll(lines);

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

    // TODO: Validate period is open (PeriodService integration when available)

    // Update voucher fields (voucher number is not changed on update)
    voucher.setVoucherDate(request.getDate());
    voucher.setPeriodId(request.getPeriodId());
    voucher.setDescription(request.getDescription());
    voucher.setUpdatedAt(Instant.now());

    // Calculate totals from lines
    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    for (VoucherLineDTO lineDto : request.getLines()) {
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
    for (VoucherLineDTO lineDto : request.getLines()) {
      VoucherLine line = new VoucherLine();
      line.setVoucherId(voucher.getId());
      line.setLineNumber(lineNumber++);
      line.setAccountId(lineDto.getAccountId());
      line.setDebit(lineDto.getDebit() != null ? lineDto.getDebit() : BigDecimal.ZERO);
      line.setCredit(lineDto.getCredit() != null ? lineDto.getCredit() : BigDecimal.ZERO);
      line.setDescription(lineDto.getDescription());
      line.setCustomerId(lineDto.getCustomerId());
      line.setVendorId(lineDto.getVendorId());
      line.setCostCenterId(lineDto.getCostCenterId());
      line.setItemId(lineDto.getItemId());
      line.setCompanyId(companyId);
      lines.add(line);
    }
    voucherLineRepository.saveAll(lines);

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
    // Convert validation errors to a format suitable for HTTP response
    Map<Integer, Map<String, String>> errors = validationResult.getErrors();
    StringBuilder errorMessage = new StringBuilder("Validation failed:\n");
    for (Map.Entry<Integer, Map<String, String>> entry : errors.entrySet()) {
      Integer lineNumber = entry.getKey();
      Map<String, String> lineErrors = entry.getValue();
      for (Map.Entry<String, String> fieldError : lineErrors.entrySet()) {
        errorMessage.append(String.format("Line %d, %s: %s\n", lineNumber, fieldError.getKey(), fieldError.getValue()));
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, errorMessage.toString());
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
    Voucher voucher =
        voucherRepository
            .findByCompanyIdAndId(companyId, voucherId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    // Validate voucher is in draft status
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
        // Continue with deletion but audit log will be missing - this is a compliance risk
        // Consider failing deletion if audit is critical for your compliance requirements
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
        // Audit logging failure is critical for compliance - consider failing deletion here
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
        voucher.getReversalOf() != null, // Has reversal badge
        0, // Attachment count - placeholder (deferred to Epic 4/5)
        voucher.getCurrency());
  }

  /**
   * Convert entity to full DTO (with lines).
   */
  private VoucherDTO toDTO(Voucher voucher) {
    String enteredByName = getUserName(voucher.getEnteredBy());
    String postedByName = voucher.getPostedBy() != null ? getUserName(voucher.getPostedBy()) : null;
    String reversedByName =
        voucher.getReversedBy() != null ? getUserName(voucher.getReversedBy()) : null;

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
    dto.setVendorId(line.getVendorId());
    dto.setCostCenterId(line.getCostCenterId());
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
}

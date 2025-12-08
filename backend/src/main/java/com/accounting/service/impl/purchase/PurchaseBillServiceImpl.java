package com.accounting.service.impl.purchase;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.PurchaseBillCreateRequest;
import com.accounting.dto.PurchaseBillDTO;
import com.accounting.dto.PurchaseBillLineDTO;
import com.accounting.dto.PurchaseBillListDTO;
import com.accounting.dto.PurchaseBillValidationResult;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillLine;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.entity.VatRate;
import com.accounting.repository.PurchaseBillLineRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ApprovalWorkflowService;
import com.accounting.service.AuditService;
import com.accounting.service.PurchaseBillService;
import com.accounting.service.PurchaseBillValidationService;
import com.accounting.service.util.PurchaseBillAuditHelper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;

/**
 * Implementation of PurchaseBillService for purchase bill operations.
 */
@Service
@Transactional
public class PurchaseBillServiceImpl implements PurchaseBillService {

  private static final Logger logger = LoggerFactory.getLogger(PurchaseBillServiceImpl.class);

  private static final int SCALE = 2;
  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  private final PurchaseBillRepository purchaseBillRepository;
  private final PurchaseBillLineRepository purchaseBillLineRepository;
  private final SupplierRepository supplierRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final PurchaseBillValidationService purchaseBillValidationService;
  private final PurchaseBillAuditHelper purchaseBillAuditHelper;
  private final ApprovalWorkflowService approvalWorkflowService;

  @PersistenceContext
  private EntityManager entityManager;

  public PurchaseBillServiceImpl(
      PurchaseBillRepository purchaseBillRepository,
      PurchaseBillLineRepository purchaseBillLineRepository,
      SupplierRepository supplierRepository,
      UserRepository userRepository,
      AuditService auditService,
      PurchaseBillValidationService purchaseBillValidationService,
      PurchaseBillAuditHelper purchaseBillAuditHelper,
      ApprovalWorkflowService approvalWorkflowService) {
    this.purchaseBillRepository = purchaseBillRepository;
    this.purchaseBillLineRepository = purchaseBillLineRepository;
    this.supplierRepository = supplierRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.purchaseBillValidationService = purchaseBillValidationService;
    this.purchaseBillAuditHelper = purchaseBillAuditHelper;
    this.approvalWorkflowService = approvalWorkflowService;
  }

  @Override
  public Page<PurchaseBillListDTO> findAll(
      Pageable pageable,
      Long supplierId,
      PurchaseBillStatus status,
      LocalDate dateFrom,
      LocalDate dateTo,
      String search) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Build specification with company scope and optional filters
    Specification<PurchaseBill> spec = (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always filter by company
      predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));

      // Filter by supplier
      if (supplierId != null) {
        predicates.add(criteriaBuilder.equal(root.get("supplierId"), supplierId));
      }

      // Filter by status
      if (status != null) {
        predicates.add(criteriaBuilder.equal(root.get("status"), status));
      }

      // Filter by date range
      if (dateFrom != null) {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("billDate"), dateFrom));
      }
      if (dateTo != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("billDate"), dateTo));
      }

      // Search by bill number or reference (unaccented Vietnamese support)
      if (search != null && !search.isBlank()) {
        List<UUID> matchingIds = purchaseBillRepository.findIdsByCompanyIdAndSearchTerm(
            companyId, search.trim());
        if (matchingIds.isEmpty()) {
          // No matches found, return empty result
          predicates.add(criteriaBuilder.equal(root.get("id"), UUID.randomUUID()));
        } else {
          predicates.add(root.get("id").in(matchingIds));
        }
      }

      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };

    Page<PurchaseBill> bills = purchaseBillRepository.findAll(spec, pageable);
    return bills.map(this::toListDTO);
  }

  @Override
  public List<PurchaseBillDTO> search(String searchTerm) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Page<PurchaseBillListDTO> results = findAll(
        Pageable.unpaged(),
        null, // supplierId
        null, // status
        null, // dateFrom
        null, // dateTo
        searchTerm);

    return results.getContent().stream()
        .map(listDto -> findById(listDto.getId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Purchase bill not found: " + listDto.getId())))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<PurchaseBillDTO> findById(UUID billId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    return purchaseBillRepository
        .findByCompanyIdAndId(companyId, billId)
        .map(this::toDTO);
  }

  @Override
  public PurchaseBillDTO create(PurchaseBillCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    try {
        Long createdById = SecurityUtils.getCurrentUserId();

        // Validate purchase bill
        PurchaseBillValidationResult validationResult =
            purchaseBillValidationService.validate(request, null);
        if (!validationResult.isValid()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Validation failed: " + formatValidationErrors(validationResult));
        }

        // Verify supplier exists and belongs to company
        Supplier supplier = supplierRepository
            .findByCompanyIdAndId(companyId, request.getSupplierId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Supplier not found: " + request.getSupplierId()));

        // Calculate totals from line items
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal vatAmount = BigDecimal.ZERO;

        for (PurchaseBillLineDTO lineDto : request.getLines()) {
          BigDecimal lineAmount = lineDto.getAmount() != null ? lineDto.getAmount() : BigDecimal.ZERO;
          totalAmount = totalAmount.add(lineAmount);
          BigDecimal lineVat = lineDto.getVatAmount() != null ? lineDto.getVatAmount() : BigDecimal.ZERO;
          vatAmount = vatAmount.add(lineVat);
        }

        // Create purchase bill entity
        PurchaseBill bill = new PurchaseBill();
        bill.setCompanyId(companyId);
        bill.setSupplierId(request.getSupplierId());
        bill.setBillNumber(request.getBillNumber());
        bill.setBillDate(request.getBillDate());
        bill.setDueDate(request.getDueDate());
        bill.setReference(request.getReference());
        bill.setDescription(request.getDescription());
        bill.setStatus(request.getStatus() != null ? request.getStatus() : PurchaseBillStatus.DRAFT);
        bill.setTotalAmount(totalAmount.setScale(SCALE, ROUNDING_MODE));
        bill.setVatAmount(vatAmount.setScale(SCALE, ROUNDING_MODE));
        bill.setCreatedById(createdById);
        bill.setCreatedAt(Instant.now());
        bill.setUpdatedAt(Instant.now());

        // Save purchase bill
        bill = purchaseBillRepository.save(bill);

        // Save purchase bill lines
        int lineNumber = 1;
        List<PurchaseBillLine> lines = new ArrayList<>();
        for (PurchaseBillLineDTO lineDto : request.getLines()) {
          PurchaseBillLine line = new PurchaseBillLine();
          line.setPurchaseBillId(bill.getId());
          line.setLineNumber(lineNumber++);
          line.setAccountId(lineDto.getAccountId());
          line.setDescription(lineDto.getDescription());
          line.setQuantity(lineDto.getQuantity() != null ? lineDto.getQuantity() : BigDecimal.ONE);
          // unitPrice is required and must be positive (validated by DTO @Positive)
          line.setUnitPrice(lineDto.getUnitPrice().setScale(SCALE, ROUNDING_MODE));
          // amount is required and must be positive (validated by DTO @Positive)
          line.setAmount(lineDto.getAmount().setScale(SCALE, ROUNDING_MODE));
          line.setVatRate(lineDto.getVatRate() != null ? lineDto.getVatRate() : VatRate.ZERO);
          line.setVatAmount(
              lineDto.getVatAmount() != null
                  ? lineDto.getVatAmount().setScale(SCALE, ROUNDING_MODE)
                  : BigDecimal.ZERO);
          line.setCostCenterId(lineDto.getCostCenterId());
          line.setItemId(lineDto.getItemId());
          line.setCompanyId(companyId);
          lines.add(line);
        }
        purchaseBillLineRepository.saveAll(lines);

        // Auto-approve bills that don't require approval workflow (below threshold and not sensitive)
        // Only auto-approve if bill is in DRAFT status
        if (bill.getStatus() == PurchaseBillStatus.DRAFT && !approvalWorkflowService.checkApprovalRequired(bill)) {
          try {
            approvalWorkflowService.autoApprove(bill, createdById);
            logger.info("Purchase bill {} auto-approved (amount: {}, threshold check passed)", 
                bill.getBillNumber(), bill.getTotalAmount());
          } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            logger.error("Failed to auto-approve purchase bill {}: {}", bill.getBillNumber(), e.getMessage(), e);
          }
        }

        // Log audit event for purchase bill creation
        try {
          com.fasterxml.jackson.databind.JsonNode afterSnapshot = purchaseBillAuditHelper.serializePurchaseBillToJson(bill);
          String diffHash = purchaseBillAuditHelper.calculateDiffHash(null, afterSnapshot);
          auditService.logPurchaseBillEvent(
              bill.getId(),
              bill.getBillNumber(),
              "PURCHASE_BILL_CREATED",
              null, // before snapshot (null for create)
              afterSnapshot,
              diffHash,
              null); // HttpServletRequest not available in service layer
        } catch (Exception e) {
          // Non-blocking: log error but don't break main flow
          logger.error("Failed to log audit event for purchase bill creation: {}", e.getMessage(), e);
        }

        return toDTO(bill);
    } catch (Exception e) {
        auditService.logPurchaseBillOperationFailed(
            null, 
            request.getBillNumber(), 
            "PURCHASE_BILL_CREATE_FAILED", 
            e.getMessage(), 
            null);
        throw e;
    }
  }

  @Override
  public PurchaseBillDTO update(UUID billId, PurchaseBillCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    try {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // Find existing purchase bill
        PurchaseBill bill = purchaseBillRepository
            .findByCompanyIdAndId(companyId, billId)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Purchase bill not found: " + billId));

        // Validate purchase bill is in DRAFT status
        if (bill.getStatus() != PurchaseBillStatus.DRAFT) {
          throw new ResponseStatusException(
              HttpStatus.CONFLICT,
              "Cannot update purchase bill: only DRAFT bills can be updated. Current status: "
                  + bill.getStatus());
        }

        // Validate creator-only edit (unless admin)
        if (!bill.getCreatedById().equals(currentUserId) && !isAdmin()) {
          throw new ResponseStatusException(
              HttpStatus.FORBIDDEN,
              "Cannot update purchase bill: only the creator or admin can update DRAFT bills");
        }

        // Validate purchase bill
        PurchaseBillValidationResult validationResult =
            purchaseBillValidationService.validate(request, billId);
        if (!validationResult.isValid()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Validation failed: " + formatValidationErrors(validationResult));
        }

        // Update purchase bill
        bill.setSupplierId(request.getSupplierId());
        bill.setBillNumber(request.getBillNumber());
        bill.setBillDate(request.getBillDate());
        bill.setDueDate(request.getDueDate());
        bill.setReference(request.getReference());
        bill.setDescription(request.getDescription());
        bill.setUpdatedAt(Instant.now());

        // Calculate totals from line items
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal vatAmount = BigDecimal.ZERO;

        for (PurchaseBillLineDTO lineDto : request.getLines()) {
          BigDecimal lineAmount = lineDto.getAmount() != null ? lineDto.getAmount() : BigDecimal.ZERO;
          totalAmount = totalAmount.add(lineAmount);
          BigDecimal lineVat = lineDto.getVatAmount() != null ? lineDto.getVatAmount() : BigDecimal.ZERO;
          vatAmount = vatAmount.add(lineVat);
        }

        bill.setTotalAmount(totalAmount.setScale(SCALE, ROUNDING_MODE));
        bill.setVatAmount(vatAmount.setScale(SCALE, ROUNDING_MODE));

        // Delete existing lines
        purchaseBillLineRepository.deleteByPurchaseBillId(billId);
        entityManager.flush();

        // Save updated purchase bill
        bill = purchaseBillRepository.save(bill);

        // Save new purchase bill lines
        int lineNumber = 1;
        List<PurchaseBillLine> lines = new ArrayList<>();
        for (PurchaseBillLineDTO lineDto : request.getLines()) {
          PurchaseBillLine line = new PurchaseBillLine();
          line.setPurchaseBillId(bill.getId());
          line.setLineNumber(lineNumber++);
          line.setAccountId(lineDto.getAccountId());
          line.setDescription(lineDto.getDescription());
          line.setQuantity(lineDto.getQuantity() != null ? lineDto.getQuantity() : BigDecimal.ONE);
          // unitPrice is required and must be positive (validated by DTO @Positive)
          line.setUnitPrice(lineDto.getUnitPrice().setScale(SCALE, ROUNDING_MODE));
          // amount is required and must be positive (validated by DTO @Positive)
          line.setAmount(lineDto.getAmount().setScale(SCALE, ROUNDING_MODE));
          line.setVatRate(lineDto.getVatRate() != null ? lineDto.getVatRate() : VatRate.ZERO);
          line.setVatAmount(
              lineDto.getVatAmount() != null
                  ? lineDto.getVatAmount().setScale(SCALE, ROUNDING_MODE)
                  : BigDecimal.ZERO);
          line.setCostCenterId(lineDto.getCostCenterId());
          line.setItemId(lineDto.getItemId());
          line.setCompanyId(companyId);
          lines.add(line);
        }
        purchaseBillLineRepository.saveAll(lines);

        // Log audit event for purchase bill update
        try {
          // Reload bill to get updated state with lines
          bill = purchaseBillRepository.findById(billId).orElse(bill);
          com.fasterxml.jackson.databind.JsonNode afterSnapshot = purchaseBillAuditHelper.serializePurchaseBillToJson(bill);
          // For update, we don't have before snapshot easily accessible, so pass null
          // The audit service can handle this case
          String diffHash = purchaseBillAuditHelper.calculateDiffHash(null, afterSnapshot);
          auditService.logPurchaseBillEvent(
              bill.getId(),
              bill.getBillNumber(),
              "PURCHASE_BILL_UPDATED",
              null, // before snapshot (could be enhanced to capture before state)
              afterSnapshot,
              diffHash,
              null); // HttpServletRequest not available in service layer
        } catch (Exception e) {
          // Non-blocking: log error but don't break main flow
          logger.error("Failed to log audit event for purchase bill update: {}", e.getMessage(), e);
        }

        return toDTO(bill);
    } catch (Exception e) {
        auditService.logPurchaseBillOperationFailed(
            billId, 
            request.getBillNumber(), 
            "PURCHASE_BILL_UPDATE_FAILED", 
            e.getMessage(), 
            null);
        throw e;
    }
  }

  @Override
  public void delete(UUID billId, String reason, jakarta.servlet.http.HttpServletRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    String billNumber = null;
    try {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // Validate reason is provided
        if (reason == null || reason.isBlank()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Deletion reason is required");
        }

        // Find purchase bill
        PurchaseBill bill = purchaseBillRepository
            .findByCompanyIdAndId(companyId, billId)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Purchase bill not found: " + billId));
        
        billNumber = bill.getBillNumber();

        // Validate purchase bill is in DRAFT status
        if (bill.getStatus() != PurchaseBillStatus.DRAFT) {
          throw new ResponseStatusException(
              HttpStatus.CONFLICT,
              "Cannot delete purchase bill: only DRAFT bills can be deleted. Current status: "
                  + bill.getStatus());
        }

        // Validate creator-only delete (unless admin)
        if (!bill.getCreatedById().equals(currentUserId) && !isAdmin()) {
          throw new ResponseStatusException(
              HttpStatus.FORBIDDEN,
              "Cannot delete purchase bill: only the creator or admin can delete DRAFT bills");
        }

        // Store bill details before deletion for audit logging
        UUID billIdForAudit = bill.getId();

        // Delete purchase bill (lines will be deleted via CASCADE)
        purchaseBillRepository.delete(bill);

        // Log deletion to audit trail
        try {
          auditService.logPurchaseBillDeleted(
              billIdForAudit,
              billNumber,
              reason,
              currentUserId,
              request);
        } catch (Exception e) {
          // Non-blocking: log error but don't break main flow
          logger.error("Failed to log purchase bill deletion to audit trail", e);
        }
    } catch (Exception e) {
        auditService.logPurchaseBillOperationFailed(
            billId, 
            billNumber, 
            "PURCHASE_BILL_DELETE_FAILED", 
            e.getMessage() + (reason != null ? " (Reason: " + reason + ")" : ""), 
            request);
        throw e;
    }
  }

  @Override
  public PurchaseBillDTO saveDraft(PurchaseBillCreateRequest request) {
    try {
        // Draft autosave - same as create/update but always sets status to DRAFT
        PurchaseBillDTO result;
        if (request.getId() != null) {
          // Update existing draft
          result = update(request.getId(), request);
        } else {
          // Create new draft
          request.setStatus(PurchaseBillStatus.DRAFT);
          result = create(request);
        }

        // Log draft save event (in addition to create/update events)
        try {
          PurchaseBill bill = purchaseBillRepository.findById(result.getId())
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase bill not found"));
          com.fasterxml.jackson.databind.JsonNode afterSnapshot = purchaseBillAuditHelper.serializePurchaseBillToJson(bill);
          String diffHash = purchaseBillAuditHelper.calculateDiffHash(null, afterSnapshot);
          auditService.logPurchaseBillEvent(
              bill.getId(),
              bill.getBillNumber(),
              "PURCHASE_BILL_DRAFT_SAVED",
              null,
              afterSnapshot,
              diffHash,
              null);
        } catch (Exception e) {
          // Non-blocking: log error but don't break main flow
          logger.error("Failed to log audit event for draft save: {}", e.getMessage(), e);
        }

        return result;
    } catch (Exception e) {
        // Only log if it wasn't already logged by create/update
        // create/update already throw and log, so we might catch that here
        // However, if the logic *around* create/update fails (e.g. if/else logic), we might want to log
        // But mostly it's redundant if create/update handle it.
        // Let's just rethrow. create/update will have logged the failure.
        throw e;
    }
  }

  @Override
  public PurchaseBillDTO recoverDraft(UUID billId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Find purchase bill
    PurchaseBill bill = purchaseBillRepository
        .findByCompanyIdAndId(companyId, billId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Purchase bill not found: " + billId));

    // Validate bill is in DRAFT status
    if (bill.getStatus() != PurchaseBillStatus.DRAFT) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot recover purchase bill: only DRAFT bills can be recovered. Current status: "
              + bill.getStatus());
    }

    // Validate creator/admin can recover
    if (!bill.getCreatedById().equals(currentUserId) && !isAdmin()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Cannot recover purchase bill: only the creator or admin can recover drafts");
    }

    return toDTO(bill);
  }

  @Override
  public List<PurchaseBillDTO> getDrafts() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Get drafts for current user (or all drafts if admin)
    List<PurchaseBill> drafts;
    if (isAdmin()) {
      drafts = purchaseBillRepository.findByCompanyIdAndStatus(companyId, PurchaseBillStatus.DRAFT);
    } else {
      // Filter by creator
      drafts = purchaseBillRepository.findByCompanyIdAndStatus(companyId, PurchaseBillStatus.DRAFT)
          .stream()
          .filter(bill -> bill.getCreatedById().equals(currentUserId))
          .collect(Collectors.toList());
    }

    return drafts.stream().map(this::toDTO).collect(Collectors.toList());
  }

  @Override
  public boolean checkDuplicate(Long supplierId, String billNumber, LocalDate billDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      return false;
    }

    return purchaseBillValidationService.validateDuplicate(
        supplierId, billNumber, billDate, companyId, null);
  }

  /**
   * Convert entity to list DTO (lightweight for list view).
   */
  private PurchaseBillListDTO toListDTO(PurchaseBill bill) {
    String createdByName = getUserName(bill.getCreatedById());
    String approvedByName =
        bill.getApprovedById() != null ? getUserName(bill.getApprovedById()) : null;

    Supplier supplier =
        supplierRepository
            .findByCompanyIdAndId(bill.getCompanyId(), bill.getSupplierId())
            .orElse(null);

    return new PurchaseBillListDTO(
        bill.getId(),
        bill.getBillNumber(),
        bill.getBillDate(),
        bill.getDueDate(),
        supplier != null ? supplier.getName() : null,
        supplier != null ? supplier.getCode() : null,
        bill.getReference(),
        bill.getStatus(),
        bill.getTotalAmount(),
        bill.getVatAmount(),
        createdByName,
        approvedByName,
        0, // Attachment count - placeholder
        bill.getPostedVoucherId());
  }

  /**
   * Convert entity to full DTO (with lines).
   */
  private PurchaseBillDTO toDTO(PurchaseBill bill) {
    String createdByName = getUserName(bill.getCreatedById());
    String approvedByName =
        bill.getApprovedById() != null ? getUserName(bill.getApprovedById()) : null;

    Supplier supplier =
        supplierRepository
            .findByCompanyIdAndId(bill.getCompanyId(), bill.getSupplierId())
            .orElse(null);

    // Load purchase bill lines
    List<PurchaseBillLine> lines =
        purchaseBillLineRepository.findByPurchaseBillIdOrderByLineNumberAsc(bill.getId());
    List<PurchaseBillLineDTO> lineDTOs =
        lines.stream().map(this::toLineDTO).collect(Collectors.toList());

    PurchaseBillDTO dto =
        new PurchaseBillDTO(
            bill.getId(),
            bill.getCompanyId(),
            bill.getSupplierId(),
            supplier != null ? supplier.getName() : null,
            supplier != null ? supplier.getCode() : null,
            bill.getBillNumber(),
            bill.getBillDate(),
            bill.getDueDate(),
            bill.getReference(),
            bill.getDescription(),
            bill.getStatus(),
            bill.getTotalAmount(),
            bill.getVatAmount(),
            bill.getCreatedById(),
            createdByName,
            bill.getApprovedById(),
            approvedByName,
            bill.getPostedVoucherId(),
            bill.getCreatedAt(),
            bill.getUpdatedAt(),
            0); // Attachment count - placeholder
    dto.setLines(lineDTOs);
    return dto;
  }

  /**
   * Convert PurchaseBillLine entity to DTO.
   */
  private PurchaseBillLineDTO toLineDTO(PurchaseBillLine line) {
    PurchaseBillLineDTO dto = new PurchaseBillLineDTO();
    dto.setLineNumber(line.getLineNumber());
    dto.setAccountId(line.getAccountId());
    dto.setDescription(line.getDescription());
    dto.setQuantity(line.getQuantity());
    dto.setUnitPrice(line.getUnitPrice());
    dto.setAmount(line.getAmount());
    dto.setVatRate(line.getVatRate());
    dto.setVatAmount(line.getVatAmount());
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
    return userRepository.findById(userId).map(User::getFullName).orElse("Unknown");
  }

  /**
   * Check if current user is admin.
   */
  private boolean isAdmin() {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ADMIN"));
  }

  /**
   * Format validation errors for exception message.
   */
  private String formatValidationErrors(PurchaseBillValidationResult result) {
    StringBuilder sb = new StringBuilder();
    if (result.hasHeaderErrors()) {
      result.getHeaderErrors().forEach(
          (field, errors) -> {
            errors.forEach(error -> sb.append(field).append(": ").append(error).append("; "));
          });
    }
    result.getLineErrors().forEach(
        (lineNum, lineErrors) -> {
          lineErrors.forEach(
              (field, errors) -> {
                errors.forEach(
                    error ->
                        sb.append("Line ")
                            .append(lineNum)
                            .append(".")
                            .append(field)
                            .append(": ")
                            .append(error)
                            .append("; "));
              });
        });
    return sb.toString();
  }
}

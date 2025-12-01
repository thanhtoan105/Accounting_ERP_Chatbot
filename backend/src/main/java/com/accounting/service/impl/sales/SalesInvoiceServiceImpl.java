package com.accounting.service.impl.sales;

import com.accounting.dto.SalesInvoiceCreateRequest;
import com.accounting.dto.SalesInvoiceDTO;
import com.accounting.dto.SalesInvoiceLineDTO;
import com.accounting.dto.SalesInvoiceListDTO;
import com.accounting.dto.SalesInvoiceValidationResult;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceLine;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.entity.Customer;
import com.accounting.entity.User;
import com.accounting.entity.VatRate;
import com.accounting.repository.SalesInvoiceLineRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.ARVATService;
import com.accounting.service.SalesInvoiceApprovalService;
import com.accounting.service.SalesInvoiceService;
import com.accounting.service.SalesInvoiceValidationService;
import com.accounting.service.VoucherService;
import com.accounting.service.voucher.VoucherPostingService;
import com.accounting.service.util.SalesInvoiceAuditHelper;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherEntryLineRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * Implementation of SalesInvoiceService for sales invoice operations.
 */
@Service
@Transactional
public class SalesInvoiceServiceImpl implements SalesInvoiceService {

  private static final Logger logger = LoggerFactory.getLogger(SalesInvoiceServiceImpl.class);

  private static final int SCALE = 2;
  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  private final SalesInvoiceRepository salesInvoiceRepository;
  private final SalesInvoiceLineRepository salesInvoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final SalesInvoiceValidationService salesInvoiceValidationService;
  private final SalesInvoiceAuditHelper salesInvoiceAuditHelper;
  private final SalesInvoiceApprovalService salesInvoiceApprovalService;
  private final ARVATService arVatService;
  private final VoucherService voucherService;
  private final VoucherPostingService voucherPostingService;

  @PersistenceContext
  private EntityManager entityManager;

  public SalesInvoiceServiceImpl(
      SalesInvoiceRepository salesInvoiceRepository,
      SalesInvoiceLineRepository salesInvoiceLineRepository,
      CustomerRepository customerRepository,
      UserRepository userRepository,
      AuditService auditService,
      SalesInvoiceValidationService salesInvoiceValidationService,
      SalesInvoiceAuditHelper salesInvoiceAuditHelper,
      SalesInvoiceApprovalService salesInvoiceApprovalService,
      ARVATService arVatService,
      VoucherService voucherService,
      VoucherPostingService voucherPostingService) {
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.salesInvoiceLineRepository = salesInvoiceLineRepository;
    this.customerRepository = customerRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.salesInvoiceValidationService = salesInvoiceValidationService;
    this.salesInvoiceAuditHelper = salesInvoiceAuditHelper;
    this.salesInvoiceApprovalService = salesInvoiceApprovalService;
    this.arVatService = arVatService;
    this.voucherService = voucherService;
    this.voucherPostingService = voucherPostingService;
  }

  @Override
  public Page<SalesInvoiceListDTO> findAll(
      Pageable pageable,
      Long customerId,
      SalesInvoiceStatus status,
      LocalDate dateFrom,
      LocalDate dateTo,
      String search) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Build specification with company scope and optional filters
    Specification<SalesInvoice> spec = (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always filter by company
      predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));

      // Filter by customer
      if (customerId != null) {
        predicates.add(criteriaBuilder.equal(root.get("customerId"), customerId));
      }

      // Filter by status
      if (status != null) {
        predicates.add(criteriaBuilder.equal(root.get("status"), status));
      }

      // Filter by date range
      if (dateFrom != null) {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("invoiceDate"), dateFrom));
      }
      if (dateTo != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("invoiceDate"), dateTo));
      }

      // Search by invoice number or reference (unaccented Vietnamese support)
      if (search != null && !search.isBlank()) {
        List<UUID> matchingIds = salesInvoiceRepository.findIdsByCompanyIdAndSearchTerm(
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

    Page<SalesInvoice> invoices = salesInvoiceRepository.findAll(spec, pageable);
    return invoices.map(this::toListDTO);
  }

  @Override
  public List<SalesInvoiceDTO> search(String searchTerm) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Page<SalesInvoiceListDTO> results = findAll(
        Pageable.unpaged(),
        null, // customerId
        null, // status
        null, // dateFrom
        null, // dateTo
        searchTerm);

    return results.getContent().stream()
        .map(listDto -> findById(listDto.getId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Purchase invoice not found: " + listDto.getId())))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<SalesInvoiceDTO> findById(UUID invoiceId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    return salesInvoiceRepository
        .findByCompanyIdAndId(companyId, invoiceId)
        .map(this::toDTO);
  }

  @Override
  public SalesInvoiceDTO create(SalesInvoiceCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    try {
      Long createdById = SecurityUtils.getCurrentUserId();

      // Validate sales invoice
      SalesInvoiceValidationResult validationResult = salesInvoiceValidationService.validate(request, null);
      if (!validationResult.isValid()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Validation failed: " + formatValidationErrors(validationResult));
      }

      // Verify customer exists and belongs to company
      Customer customer = customerRepository
          .findByCompanyIdAndId(companyId, request.getCustomerId())
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Customer not found: " + request.getCustomerId()));

      // Calculate totals from line items
      BigDecimal totalAmount = BigDecimal.ZERO;
      BigDecimal vatAmount = BigDecimal.ZERO;

      for (SalesInvoiceLineDTO lineDto : request.getLines()) {
        BigDecimal lineAmount = lineDto.getAmount() != null ? lineDto.getAmount() : BigDecimal.ZERO;
        totalAmount = totalAmount.add(lineAmount);
        BigDecimal lineVat = lineDto.getVatAmount() != null ? lineDto.getVatAmount() : BigDecimal.ZERO;
        vatAmount = vatAmount.add(lineVat);
      }

      // Create sales invoice entity
      SalesInvoice invoice = new SalesInvoice();
      invoice.setCompanyId(companyId);
      invoice.setCustomerId(request.getCustomerId());
      invoice.setInvoiceNumber(request.getInvoiceNumber());
      invoice.setInvoiceDate(request.getInvoiceDate());
      invoice.setDueDate(request.getDueDate());
      invoice.setReference(request.getReference());
      invoice.setDescription(request.getDescription());
      invoice.setStatus(request.getStatus() != null ? request.getStatus() : SalesInvoiceStatus.DRAFT);
      invoice.setTotalAmount(totalAmount.setScale(SCALE, ROUNDING_MODE));
      invoice.setVatAmount(vatAmount.setScale(SCALE, ROUNDING_MODE));
      // Initialize remaining balance = total amount (no payments yet)
      invoice.setRemainingBalance(totalAmount.setScale(SCALE, ROUNDING_MODE));
      invoice.setAmountPaid(BigDecimal.ZERO);
      invoice.setCreatedById(createdById);
      invoice.setCreatedAt(Instant.now());
      invoice.setUpdatedAt(Instant.now());

      // Save sales invoice
      invoice = salesInvoiceRepository.save(invoice);

      // Save sales invoice lines
      int lineNumber = 1;
      List<SalesInvoiceLine> lines = new ArrayList<>();
      for (SalesInvoiceLineDTO lineDto : request.getLines()) {
        SalesInvoiceLine line = new SalesInvoiceLine();
        line.setSalesInvoiceId(invoice.getId());
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
      salesInvoiceLineRepository.saveAll(lines);

      // Check if approval is required and route accordingly
      boolean requiresApproval = salesInvoiceApprovalService.checkApprovalRequired(invoice);
      if (requiresApproval) {
        // Submit for approval - this will set status to PENDING_APPROVAL
        try {
          salesInvoiceApprovalService.submitForApproval(invoice.getId(), createdById);
          logger.info(
              "Sales invoice {} submitted for approval (amount: {}, threshold check passed)",
              invoice.getInvoiceNumber(),
              invoice.getTotalAmount());
        } catch (Exception e) {
          logger.error(
              "Failed to submit sales invoice {} for approval: {}",
              invoice.getInvoiceNumber(),
              e.getMessage(),
              e);
          // Don't fail the entire creation - invoice is saved as DRAFT
        }
      } else {
        // Auto-approve and post immediately
        try {
          salesInvoiceApprovalService.autoApprove(invoice, createdById);
          // Auto-approve will handle posting the voucher and setting status to POSTED
          logger.info(
              "Sales invoice {} auto-approved and posted (amount: {} below threshold)",
              invoice.getInvoiceNumber(),
              invoice.getTotalAmount());
        } catch (Exception e) {
          logger.error(
              "Failed to auto-approve sales invoice {}: {}",
              invoice.getInvoiceNumber(),
              e.getMessage(),
              e);
          // Don't fail the entire creation - invoice remains as DRAFT
        }
      }

      // Reload invoice to get updated status from approval workflow
      invoice = salesInvoiceRepository.findById(invoice.getId())
          .orElseThrow(() -> new IllegalStateException("Invoice not found after creation"));

      // Log audit event for sales invoice creation
      try {
        com.fasterxml.jackson.databind.JsonNode afterSnapshot = salesInvoiceAuditHelper
            .serializeSalesInvoiceToJson(invoice);
        String diffHash = salesInvoiceAuditHelper.calculateDiffHash(null, afterSnapshot);
        auditService.logSalesInvoiceEvent(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            "SALES_INVOICE_CREATED",
            null, // before snapshot (null for create)
            afterSnapshot,
            diffHash,
            null); // HttpServletRequest not available in service layer
      } catch (Exception e) {
        // Non-blocking: log error but don't break main flow
        logger.error("Failed to log audit event for sales invoice creation: {}", e.getMessage(), e);
      }

      return toDTO(invoice);
    } catch (Exception e) {
      auditService.logSalesInvoiceOperationFailed(
          null,
          request.getInvoiceNumber(),
          "PURCHASE_INVOICE_CREATE_FAILED",
          e.getMessage(),
          null);
      throw e;
    }
  }

  @Override
  public SalesInvoiceDTO update(UUID invoiceId, SalesInvoiceCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    try {
      Long currentUserId = SecurityUtils.getCurrentUserId();

      // Find existing sales invoice
      SalesInvoice invoice = salesInvoiceRepository
          .findByCompanyIdAndId(companyId, invoiceId)
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Purchase invoice not found: " + invoiceId));

      // Validate sales invoice is in DRAFT status
      if (invoice.getStatus() != SalesInvoiceStatus.DRAFT) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Cannot update sales invoice: only DRAFT invoices can be updated. Current status: "
                + invoice.getStatus());
      }

      // Validate creator-only edit (unless admin)
      if (!invoice.getCreatedById().equals(currentUserId) && !isAdmin()) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Cannot update sales invoice: only the creator or admin can update DRAFT invoices");
      }

      // Validate sales invoice
      SalesInvoiceValidationResult validationResult = salesInvoiceValidationService.validate(request, invoiceId);
      if (!validationResult.isValid()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Validation failed: " + formatValidationErrors(validationResult));
      }

      // Update sales invoice
      invoice.setCustomerId(request.getCustomerId());
      invoice.setInvoiceNumber(request.getInvoiceNumber());
      invoice.setInvoiceDate(request.getInvoiceDate());
      invoice.setDueDate(request.getDueDate());
      invoice.setReference(request.getReference());
      invoice.setDescription(request.getDescription());
      invoice.setUpdatedAt(Instant.now());

      // Calculate totals from line items
      BigDecimal totalAmount = BigDecimal.ZERO;
      BigDecimal vatAmount = BigDecimal.ZERO;

      for (SalesInvoiceLineDTO lineDto : request.getLines()) {
        BigDecimal lineAmount = lineDto.getAmount() != null ? lineDto.getAmount() : BigDecimal.ZERO;
        totalAmount = totalAmount.add(lineAmount);
        BigDecimal lineVat = lineDto.getVatAmount() != null ? lineDto.getVatAmount() : BigDecimal.ZERO;
        vatAmount = vatAmount.add(lineVat);
      }

      invoice.setTotalAmount(totalAmount.setScale(SCALE, ROUNDING_MODE));
      invoice.setVatAmount(vatAmount.setScale(SCALE, ROUNDING_MODE));
      // Recalculate remaining balance = total - already paid
      BigDecimal amountPaid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
      invoice.setRemainingBalance(totalAmount.subtract(amountPaid).setScale(SCALE, ROUNDING_MODE));

      // Delete existing lines
      salesInvoiceLineRepository.deleteBySalesInvoiceId(invoiceId);
      entityManager.flush();

      // Save updated sales invoice
      invoice = salesInvoiceRepository.save(invoice);

      // Save new sales invoice lines
      int lineNumber = 1;
      List<SalesInvoiceLine> lines = new ArrayList<>();
      for (SalesInvoiceLineDTO lineDto : request.getLines()) {
        SalesInvoiceLine line = new SalesInvoiceLine();
        line.setSalesInvoiceId(invoice.getId());
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
      salesInvoiceLineRepository.saveAll(lines);

      // Log audit event for sales invoice update
      try {
        // Reload invoice to get updated state with lines
        invoice = salesInvoiceRepository.findById(invoiceId).orElse(invoice);
        com.fasterxml.jackson.databind.JsonNode afterSnapshot = salesInvoiceAuditHelper
            .serializeSalesInvoiceToJson(invoice);
        // For update, we don't have before snapshot easily accessible, so pass null
        // The audit service can handle this case
        String diffHash = salesInvoiceAuditHelper.calculateDiffHash(null, afterSnapshot);
        auditService.logSalesInvoiceEvent(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            "PURCHASE_INVOICE_UPDATED",
            null, // before snapshot (could be enhanced to capture before state)
            afterSnapshot,
            diffHash,
            null); // HttpServletRequest not available in service layer
      } catch (Exception e) {
        // Non-blocking: log error but don't break main flow
        logger.error("Failed to log audit event for sales invoice update: {}", e.getMessage(), e);
      }

      return toDTO(invoice);
    } catch (Exception e) {
      auditService.logSalesInvoiceOperationFailed(
          invoiceId,
          request.getInvoiceNumber(),
          "PURCHASE_INVOICE_UPDATE_FAILED",
          e.getMessage(),
          null);
      throw e;
    }
  }

  @Override
  public void delete(UUID invoiceId, String reason, jakarta.servlet.http.HttpServletRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    String invoiceNumber = null;
    try {
      Long currentUserId = SecurityUtils.getCurrentUserId();

      // Validate reason is provided
      if (reason == null || reason.isBlank()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Deletion reason is required");
      }

      // Find sales invoice
      SalesInvoice invoice = salesInvoiceRepository
          .findByCompanyIdAndId(companyId, invoiceId)
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Purchase invoice not found: " + invoiceId));

      invoiceNumber = invoice.getInvoiceNumber();

      // Validate sales invoice is in DRAFT status
      if (invoice.getStatus() != SalesInvoiceStatus.DRAFT) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Cannot delete sales invoice: only DRAFT invoices can be deleted. Current status: "
                + invoice.getStatus());
      }

      // Validate creator-only delete (unless admin)
      if (!invoice.getCreatedById().equals(currentUserId) && !isAdmin()) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Cannot delete sales invoice: only the creator or admin can delete DRAFT invoices");
      }

      // Store invoice details before deletion for audit logging
      UUID invoiceIdForAudit = invoice.getId();

      // Delete sales invoice (lines will be deleted via CASCADE)
      salesInvoiceRepository.delete(invoice);

      // Log deletion to audit trail
      try {
        auditService.logSalesInvoiceDeleted(
            invoiceIdForAudit,
            invoiceNumber,
            reason,
            currentUserId,
            request);
      } catch (Exception e) {
        // Non-blocking: log error but don't break main flow
        logger.error("Failed to log sales invoice deletion to audit trail", e);
      }
    } catch (Exception e) {
      auditService.logSalesInvoiceOperationFailed(
          invoiceId,
          invoiceNumber,
          "PURCHASE_INVOICE_DELETE_FAILED",
          e.getMessage() + (reason != null ? " (Reason: " + reason + ")" : ""),
          request);
      throw e;
    }
  }

  @Override
  public SalesInvoiceDTO saveDraft(SalesInvoiceCreateRequest request) {
    try {
      // Draft autosave - same as create/update but always sets status to DRAFT
      SalesInvoiceDTO result;
      if (request.getId() != null) {
        // Update existing draft
        result = update(request.getId(), request);
      } else {
        // Create new draft
        request.setStatus(SalesInvoiceStatus.DRAFT);
        result = create(request);
      }

      // Log draft save event (in addition to create/update events)
      try {
        SalesInvoice invoice = salesInvoiceRepository.findById(result.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        com.fasterxml.jackson.databind.JsonNode afterSnapshot = salesInvoiceAuditHelper
            .serializeSalesInvoiceToJson(invoice);
        String diffHash = salesInvoiceAuditHelper.calculateDiffHash(null, afterSnapshot);
        auditService.logSalesInvoiceEvent(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            "PURCHASE_INVOICE_DRAFT_SAVED",
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
      // However, if the logic *around* create/update fails (e.g. if/else logic), we
      // might want to log
      // But mostly it's redundant if create/update handle it.
      // Let's just rethrow. create/update will have logged the failure.
      throw e;
    }
  }

  @Override
  public SalesInvoiceDTO recoverDraft(UUID invoiceId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Find sales invoice
    SalesInvoice invoice = salesInvoiceRepository
        .findByCompanyIdAndId(companyId, invoiceId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Purchase invoice not found: " + invoiceId));

    // Validate invoice is in DRAFT status
    if (invoice.getStatus() != SalesInvoiceStatus.DRAFT) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot recover sales invoice: only DRAFT invoices can be recovered. Current status: "
              + invoice.getStatus());
    }

    // Validate creator/admin can recover
    if (!invoice.getCreatedById().equals(currentUserId) && !isAdmin()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Cannot recover sales invoice: only the creator or admin can recover drafts");
    }

    return toDTO(invoice);
  }

  @Override
  public List<SalesInvoiceDTO> getDrafts() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Get drafts for current user (or all drafts if admin)
    List<SalesInvoice> drafts;
    if (isAdmin()) {
      drafts = salesInvoiceRepository.findByCompanyIdAndStatus(companyId, SalesInvoiceStatus.DRAFT);
    } else {
      // Filter by creator
      drafts = salesInvoiceRepository.findByCompanyIdAndStatus(companyId, SalesInvoiceStatus.DRAFT)
          .stream()
          .filter(invoice -> invoice.getCreatedById().equals(currentUserId))
          .collect(Collectors.toList());
    }

    return drafts.stream().map(this::toDTO).collect(Collectors.toList());
  }

  @Override
  public boolean checkDuplicate(Long customerId, String invoiceNumber, LocalDate invoiceDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      return false;
    }

    return salesInvoiceValidationService.validateDuplicate(
        customerId, invoiceNumber, invoiceDate, companyId, null);
  }

  /**
   * Convert entity to list DTO (lightweight for list view).
   */
  private SalesInvoiceListDTO toListDTO(SalesInvoice invoice) {
    String createdByName = getUserName(invoice.getCreatedById());
    String approvedByName = invoice.getApprovedById() != null ? getUserName(invoice.getApprovedById()) : null;

    Customer customer = customerRepository
        .findByCompanyIdAndId(invoice.getCompanyId(), invoice.getCustomerId())
        .orElse(null);

    return new SalesInvoiceListDTO(
        invoice.getId(),
        invoice.getInvoiceNumber(),
        invoice.getInvoiceDate(),
        invoice.getDueDate(),
        customer != null ? customer.getName() : null,
        invoice.getReference(),
        invoice.getTotalAmount(),
        invoice.getStatus(),
        0); // attachment count
  }

  /**
   * Convert entity to full DTO (with lines).
   */
  private SalesInvoiceDTO toDTO(SalesInvoice invoice) {
    String createdByName = getUserName(invoice.getCreatedById());
    String approvedByName = invoice.getApprovedById() != null ? getUserName(invoice.getApprovedById()) : null;

    Customer customer = customerRepository
        .findByCompanyIdAndId(invoice.getCompanyId(), invoice.getCustomerId())
        .orElse(null);

    // Load sales invoice lines
    List<SalesInvoiceLine> lines = salesInvoiceLineRepository.findBySalesInvoiceIdOrderByLineNumberAsc(invoice.getId());
    List<SalesInvoiceLineDTO> lineDTOs = lines.stream().map(this::toLineDTO).collect(Collectors.toList());

    SalesInvoiceDTO dto = new SalesInvoiceDTO(
        invoice.getId(),
        invoice.getCompanyId(),
        invoice.getCustomerId(),
        customer != null ? customer.getName() : null,
        customer != null ? customer.getCode() : null,
        invoice.getInvoiceNumber(),
        invoice.getInvoiceDate(),
        invoice.getDueDate(),
        invoice.getReference(),
        invoice.getDescription(),
        invoice.getStatus(),
        invoice.getTotalAmount(),
        invoice.getVatAmount(),
        invoice.getCreatedById(),
        createdByName,
        invoice.getApprovedById(),
        approvedByName,
        invoice.getPostedVoucherId(),
        invoice.getCreatedAt(),
        invoice.getUpdatedAt(),
        0); // Attachment count - placeholder
    dto.setLines(lineDTOs);
    return dto;
  }

  /**
   * Convert SalesInvoiceLine entity to DTO.
   */
  private SalesInvoiceLineDTO toLineDTO(SalesInvoiceLine line) {
    SalesInvoiceLineDTO dto = new SalesInvoiceLineDTO();
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
  private String formatValidationErrors(SalesInvoiceValidationResult result) {
    StringBuilder sb = new StringBuilder();
    if (result.getHeaderErrors() != null && !result.getHeaderErrors().isEmpty()) {
      result.getHeaderErrors().forEach(
          (field, error) -> sb.append(field).append(": ").append(error).append("; "));
    }
    if (result.getLineErrors() != null) {
      result.getLineErrors().forEach(
          (lineNum, lineErrors) -> {
            lineErrors.forEach(
                (field, error) -> sb.append("Line ")
                    .append(lineNum)
                    .append(" - ")
                    .append(field)
                    .append(": ")
                    .append(error)
                    .append("; "));
          });
    }
    return sb.toString();
  }

  @Override
  public SalesInvoiceDTO createCreditNote(UUID originalInvoiceId, SalesInvoiceCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Missing company context");
    }

    Long createdById = SecurityUtils.getCurrentUserId();
    if (createdById == null) {
      throw new IllegalStateException("User not authenticated");
    }

    // Validate original invoice exists and is POSTED (AC-VAT-004)
    SalesInvoice originalInvoice = salesInvoiceRepository
        .findByCompanyIdAndId(companyId, originalInvoiceId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Original invoice not found: " + originalInvoiceId));

    if (originalInvoice.getStatus() != SalesInvoiceStatus.POSTED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Credit notes can only be created for POSTED invoices. Current status: "
              + originalInvoice.getStatus());
    }

    // Validate credit note request
    SalesInvoiceValidationResult validationResult = salesInvoiceValidationService.validate(request, null);
    if (!validationResult.isValid()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Validation failed: " + formatValidationErrors(validationResult));
    }

    // Verify customer matches original invoice
    if (!request.getCustomerId().equals(originalInvoice.getCustomerId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Credit note customer must match original invoice customer");
    }

    // Calculate totals from line items
    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal vatAmount = BigDecimal.ZERO;

    for (SalesInvoiceLineDTO lineDto : request.getLines()) {
      BigDecimal lineAmount = lineDto.getAmount() != null ? lineDto.getAmount() : BigDecimal.ZERO;
      totalAmount = totalAmount.add(lineAmount);
      BigDecimal lineVat = lineDto.getVatAmount() != null ? lineDto.getVatAmount() : BigDecimal.ZERO;
      vatAmount = vatAmount.add(lineVat);
    }

    // Create credit note invoice entity
    SalesInvoice creditNote = new SalesInvoice();
    creditNote.setCompanyId(companyId);
    creditNote.setCustomerId(request.getCustomerId());
    creditNote.setInvoiceNumber(request.getInvoiceNumber());
    creditNote.setInvoiceDate(request.getInvoiceDate());
    creditNote.setDueDate(request.getDueDate());
    // Ensure reference is not blank (required by entity validation)
    String reference = request.getReference();
    if (reference == null || reference.trim().isEmpty()) {
      reference = "Credit Note for " + originalInvoice.getInvoiceNumber();
    }
    creditNote.setReference(reference);
    creditNote.setDescription(request.getDescription());
    creditNote.setStatus(SalesInvoiceStatus.DRAFT); // Credit notes start as DRAFT
    creditNote.setTotalAmount(totalAmount.setScale(SCALE, ROUNDING_MODE));
    creditNote.setVatAmount(vatAmount.setScale(SCALE, ROUNDING_MODE));
    // Credit notes have negative remaining balance (reduces customer debt)
    creditNote.setRemainingBalance(totalAmount.setScale(SCALE, ROUNDING_MODE));
    creditNote.setAmountPaid(BigDecimal.ZERO);
    creditNote.setOriginalInvoiceId(originalInvoiceId); // Link to original invoice
    creditNote.setCreatedById(createdById);
    creditNote.setCreatedAt(Instant.now());
    creditNote.setUpdatedAt(Instant.now());

    // Save credit note invoice
    creditNote = salesInvoiceRepository.save(creditNote);

    // Save credit note lines
    int lineNumber = 1;
    List<SalesInvoiceLine> lines = new ArrayList<>();
    for (SalesInvoiceLineDTO lineDto : request.getLines()) {
      SalesInvoiceLine line = new SalesInvoiceLine();
      line.setSalesInvoiceId(creditNote.getId());
      line.setLineNumber(lineNumber++);
      line.setAccountId(lineDto.getAccountId());
      line.setDescription(lineDto.getDescription());
      line.setQuantity(lineDto.getQuantity() != null ? lineDto.getQuantity() : BigDecimal.ONE);
      line.setUnitPrice(lineDto.getUnitPrice().setScale(SCALE, ROUNDING_MODE));
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
    salesInvoiceLineRepository.saveAll(lines);

    // Generate inverted GL splits using ARVATService (AC-VAT-004)
    List<VoucherEntryLineRequest> entryLines = arVatService.generateCreditNoteGLSplit(creditNote, originalInvoice);

    if (entryLines.isEmpty()) {
      throw new IllegalStateException(
          "Credit note " + creditNote.getInvoiceNumber()
              + " has no monetary value to post. Cannot create voucher.");
    }

    // Create and post voucher with inverted GL splits
    VoucherCreateRequest voucherRequest = new VoucherCreateRequest();
    voucherRequest.setDate(creditNote.getInvoiceDate());
    voucherRequest.setDescription(
        String.format(
            "Credit Note %s for Invoice %s - %s",
            creditNote.getInvoiceNumber(),
            originalInvoice.getInvoiceNumber(),
            creditNote.getReference() != null ? creditNote.getReference() : "Credit note posting"));
    voucherRequest.setEntryLines(entryLines);

    VoucherDTO voucher = voucherService.create(voucherRequest);
    voucherPostingService.postVoucher(voucher.getId(), null);

    // Update credit note with posted voucher ID and status
    creditNote.setPostedVoucherId(voucher.getId());
    creditNote.setStatus(SalesInvoiceStatus.POSTED);
    creditNote.setApprovedById(createdById); // Credit notes are auto-approved
    creditNote = salesInvoiceRepository.save(creditNote);

    logger.info(
        "Created and posted credit note {} (voucher {}) for original invoice {}",
        creditNote.getInvoiceNumber(),
        voucher.getVoucherNumber(),
        originalInvoice.getInvoiceNumber());

    // Audit logging: Link credit note to original invoice (AC-VAT-004)
    // Note: HttpServletRequest is not available in service layer, pass null
    // The audit service should handle null request gracefully
    try {
      auditService.logCreditNoteCreation(
          companyId,
          createdById,
          creditNote.getId(),
          creditNote.getInvoiceNumber(),
          originalInvoice.getId(),
          originalInvoice.getInvoiceNumber(),
          null); // HttpServletRequest not available in service layer
    } catch (Exception e) {
      logger.warn("Failed to log credit note creation audit: {}", e.getMessage());
      // Don't fail the entire operation if audit logging fails
    }

    // Convert to DTO and return
    return toDTO(creditNote);
  }
}

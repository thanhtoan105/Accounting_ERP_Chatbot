package com.accounting.service.impl.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.APPaymentCreateRequest;
import com.accounting.dto.APPaymentDTO;
import com.accounting.dto.APPaymentListDTO;
import com.accounting.dto.PaymentAllocationDTO;
import com.accounting.dto.PaymentAllocationRequest;
import com.accounting.dto.PurchaseBillDTO;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.entity.APPayment;
import com.accounting.entity.BankAccount;
import com.accounting.entity.PaymentAllocation;
import com.accounting.entity.PaymentStatus;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.repository.APPaymentRepository;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.PaymentAllocationRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AccountBalanceService;
import com.accounting.service.ApprovalWorkflowService;
import com.accounting.service.AuditService;
import com.accounting.service.CompanySettingsService;
import com.accounting.service.EmbeddingTriggerService;
import com.accounting.service.PaymentService;
import com.accounting.service.PaymentValidationService;
import com.accounting.service.PurchaseBillService;
import com.accounting.service.VoucherService;
import com.accounting.service.voucher.VoucherPostingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;

/**
 * Implementation of PaymentService for AP payment operations.
 * Handles payment creation, FIFO allocation, posting, and cancellation.
 */
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

  private static final int SCALE = 2;
  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
  private static final String PAYMENT_NUMBER_PREFIX = "PAY-";

  private final APPaymentRepository paymentRepository;
  private final PaymentAllocationRepository allocationRepository;
  private final PurchaseBillRepository purchaseBillRepository;
  private final SupplierRepository supplierRepository;
  private final BankAccountRepository bankAccountRepository;
  private final UserRepository userRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final PaymentValidationService paymentValidationService;
  private final AccountBalanceService accountBalanceService;
  private final VoucherService voucherService;
  private final VoucherPostingService voucherPostingService;
  private final ApprovalWorkflowService approvalWorkflowService;
  private final PurchaseBillService purchaseBillService;
  private final AuditService auditService;
  private final CompanySettingsService companySettingsService;
  private final ObjectMapper objectMapper;
  private final EmbeddingTriggerService embeddingTriggerService;
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private com.accounting.service.APAgingService agingService;

  @PersistenceContext
  private EntityManager entityManager;

  public PaymentServiceImpl(
      APPaymentRepository paymentRepository,
      PaymentAllocationRepository allocationRepository,
      PurchaseBillRepository purchaseBillRepository,
      SupplierRepository supplierRepository,
      BankAccountRepository bankAccountRepository,
      UserRepository userRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      PaymentValidationService paymentValidationService,
      AccountBalanceService accountBalanceService,
      VoucherService voucherService,
      VoucherPostingService voucherPostingService,
      ApprovalWorkflowService approvalWorkflowService,
      PurchaseBillService purchaseBillService,
      AuditService auditService,
      CompanySettingsService companySettingsService,
      ObjectMapper objectMapper,
      EmbeddingTriggerService embeddingTriggerService) {
    this.paymentRepository = paymentRepository;
    this.allocationRepository = allocationRepository;
    this.purchaseBillRepository = purchaseBillRepository;
    this.supplierRepository = supplierRepository;
    this.bankAccountRepository = bankAccountRepository;
    this.userRepository = userRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.paymentValidationService = paymentValidationService;
    this.accountBalanceService = accountBalanceService;
    this.voucherService = voucherService;
    this.voucherPostingService = voucherPostingService;
    this.approvalWorkflowService = approvalWorkflowService;
    this.purchaseBillService = purchaseBillService;
    this.auditService = auditService;
    this.companySettingsService = companySettingsService;
    this.objectMapper = objectMapper;
    this.embeddingTriggerService = embeddingTriggerService;
  }

  @Override
  public Page<APPaymentListDTO> findAll(
      Pageable pageable,
      Long supplierId,
      PaymentStatus status,
      LocalDate dateFrom,
      LocalDate dateTo,
      String search,
      Boolean standalone) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Build specification with company scope and optional filters
    Specification<APPayment> spec = (root, query, criteriaBuilder) -> {
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
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("paymentDate"), dateFrom));
      }
      if (dateTo != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("paymentDate"), dateTo));
      }

      // Filter by standalone
      if (standalone != null) {
        predicates.add(criteriaBuilder.equal(root.get("isStandalone"), standalone));
      }

      // Search by payment number, reference, or payee
      if (search != null && !search.isBlank()) {
        List<UUID> matchingIds = paymentRepository.findIdsByCompanyIdAndSearchTerm(companyId, search.trim());
        if (matchingIds.isEmpty()) {
          // No matches found, return empty result
          predicates.add(criteriaBuilder.equal(root.get("id"), UUID.randomUUID()));
        } else {
          predicates.add(root.get("id").in(matchingIds));
        }
      }

      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };

    Page<APPayment> payments = paymentRepository.findAll(spec, pageable);
    return payments.map(this::toListDTO);
  }

  @Override
  public Optional<APPaymentDTO> findById(UUID paymentId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    return paymentRepository
        .findByCompanyIdAndId(companyId, paymentId)
        .map(this::toDTO);
  }

  @Override
  public APPaymentDTO create(APPaymentCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    try {
      Long createdById = SecurityUtils.getCurrentUserId();

      // Validate account selection (either cash or bank, not both)
      if (request.getCashAccountId() == null && request.getBankAccountId() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Either cash account or bank account must be specified");
      }
      if (request.getCashAccountId() != null && request.getBankAccountId() != null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Cannot specify both cash account and bank account");
      }

      // Validate supplier exists
      supplierRepository
          .findByCompanyIdAndId(companyId, request.getSupplierId())
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Supplier not found: " + request.getSupplierId()));

      // Validate account exists
      Long accountId = request.getCashAccountId() != null ? request.getCashAccountId() : request.getBankAccountId();
      bankAccountRepository
          .findByCompanyIdAndId(companyId, accountId)
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Bank account not found: " + accountId));

      // Validate standalone payment (if applicable)
      if (request.getIsStandalone() != null && request.getIsStandalone()) {
        var validationResult = paymentValidationService.validateStandalonePayment(
            toEntity(request, companyId, createdById), createdById);
        if (!validationResult.isValid()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, formatValidationErrors(validationResult));
        }
      }

      // Validate account balance
      var balanceValidation = paymentValidationService.validateAccountBalance(accountId, request.getAmount());
      if (balanceValidation.hasErrors()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, formatValidationErrors(balanceValidation));
      }

      // Validate payment proof (if required)
      var proofValidation = paymentValidationService.validatePaymentProof(
          request.getAmount(), request.getPaymentProofUrl());
      if (!proofValidation.isValid()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, formatValidationErrors(proofValidation));
      }

      // Generate payment number
      String paymentNumber = generatePaymentNumber(request.getPaymentDate(), companyId);

      // Create payment entity
      APPayment payment = new APPayment();
      payment.setCompanyId(companyId);
      payment.setSupplierId(request.getSupplierId());
      payment.setPaymentNumber(paymentNumber);
      payment.setPaymentDate(request.getPaymentDate());
      payment.setDueDate(request.getDueDate());
      payment.setCashAccountId(request.getCashAccountId());
      payment.setBankAccountId(request.getBankAccountId());
      payment.setPayee(request.getPayee());
      payment.setAmount(request.getAmount().setScale(SCALE, ROUNDING_MODE));
      payment.setReference(request.getReference());
      payment.setPaymentMethod(request.getPaymentMethod());
      payment.setPaymentProofUrl(request.getPaymentProofUrl());
      payment.setIsStandalone(
          request.getIsStandalone() != null ? request.getIsStandalone() : false);
      payment.setStatus(PaymentStatus.DRAFT);
      payment.setCreatedById(createdById);

      // Save payment
      payment = paymentRepository.save(payment);

      // Handle allocations
      if (!payment.getIsStandalone()) {
        // Validate supplier has open bills
        var supplierValidation = paymentValidationService.validateSupplierHasOpenBills(request.getSupplierId());
        if (!supplierValidation.isValid()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, formatValidationErrors(supplierValidation));
        }

        // If allocations not provided, perform FIFO allocation
        List<PaymentAllocationRequest> allocations = request.getAllocations();
        if (allocations == null || allocations.isEmpty()) {
          List<PaymentAllocationDTO> fifoAllocations = allocateFIFO(request.getAmount(), request.getSupplierId());
          allocations = fifoAllocations.stream()
              .map(
                  dto -> new PaymentAllocationRequest(
                      dto.getPurchaseBillId(), dto.getAllocatedAmount()))
              .collect(Collectors.toList());
        }

        // Validate allocations
        var allocationValidation = paymentValidationService.validateAllocations(allocations, request.getAmount());
        if (!allocationValidation.isValid()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, formatValidationErrors(allocationValidation));
        }

        // Save allocations
        saveAllocations(payment.getId(), allocations, companyId);
      }

      // Check if approval is required based on payment amount and threshold
      BigDecimal approvalThreshold = getApprovalThreshold();
      if (payment.getAmount().compareTo(approvalThreshold) > 0) {
        // Payment exceeds threshold - requires approval
        payment.setStatus(PaymentStatus.PENDING_APPROVAL);
        logger.info(
            "Payment {} requires approval (amount: {}, threshold: {})",
            payment.getPaymentNumber(),
            payment.getAmount(),
            approvalThreshold);
      } else {
        // Payment below threshold - auto-approved (stays in DRAFT, can be posted
        // directly)
        logger.info(
            "Payment {} auto-approved (amount: {}, threshold: {})",
            payment.getPaymentNumber(),
            payment.getAmount(),
            approvalThreshold);
      }

      // Log audit event
      try {
        JsonNode afterSnapshot = serializePaymentToJson(payment);
        auditService.logPaymentEvent(
            payment.getId(),
            payment.getPaymentNumber(),
            "PAYMENT_CREATED",
            null, // before snapshot (null for create)
            afterSnapshot,
            null, // diff hash (null for create)
            null); // HttpServletRequest not available in service layer
      } catch (Exception e) {
        logger.error("Failed to log audit event for payment creation: {}", e.getMessage(), e);
      }

      return toDTO(payment);
    } catch (Exception e) {
      auditService.logPaymentOperationFailed(
          null,
          null,
          "PAYMENT_CREATE_FAILED",
          e.getMessage(),
          null);
      throw e;
    }
  }

  @Override
  public APPaymentDTO update(UUID paymentId, APPaymentCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    try {
      // Find existing payment
      APPayment payment = paymentRepository
          .findByCompanyIdAndId(companyId, paymentId)
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

      // Validate payment is in DRAFT status
      if (payment.getStatus() != PaymentStatus.DRAFT) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Cannot update payment: only DRAFT payments can be updated. Current status: "
                + payment.getStatus());
      }

      // Update payment fields
      payment.setPaymentDate(request.getPaymentDate());
      payment.setDueDate(request.getDueDate());
      payment.setCashAccountId(request.getCashAccountId());
      payment.setBankAccountId(request.getBankAccountId());
      payment.setPayee(request.getPayee());
      payment.setAmount(request.getAmount().setScale(SCALE, ROUNDING_MODE));
      payment.setReference(request.getReference());
      payment.setPaymentMethod(request.getPaymentMethod());
      payment.setPaymentProofUrl(request.getPaymentProofUrl());

      // Save payment
      payment = paymentRepository.save(payment);

      // Update allocations if provided
      if (!payment.getIsStandalone() && request.getAllocations() != null) {
        // Delete existing allocations
        allocationRepository.deleteByPaymentId(paymentId);

        // Validate new allocations
        var allocationValidation = paymentValidationService.validateAllocations(
            request.getAllocations(), request.getAmount());
        if (!allocationValidation.isValid()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, formatValidationErrors(allocationValidation));
        }

        // Save new allocations
        saveAllocations(payment.getId(), request.getAllocations(), companyId);
      }

      // Audit logging for update
      try {
        JsonNode afterSnapshot = serializePaymentToJson(payment);
        auditService.logPaymentEvent(
            payment.getId(),
            payment.getPaymentNumber(),
            "PAYMENT_UPDATED",
            null, // before snapshot (could be captured before update)
            afterSnapshot,
            null, // diff hash
            null);
      } catch (Exception e) {
        logger.error("Failed to log audit event for payment update: {}", e.getMessage(), e);
      }

      return toDTO(payment);
    } catch (Exception e) {
      auditService.logPaymentOperationFailed(
          paymentId,
          null,
          "PAYMENT_UPDATE_FAILED",
          e.getMessage(),
          null);
      throw e;
    }
  }

  @Override
  public void delete(UUID paymentId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    try {
      // Find payment
      APPayment payment = paymentRepository
          .findByCompanyIdAndId(companyId, paymentId)
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

      // Validate payment is in DRAFT status
      if (payment.getStatus() != PaymentStatus.DRAFT) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Cannot delete payment: only DRAFT payments can be deleted. Current status: "
                + payment.getStatus());
      }

      // Delete payment (allocations will be deleted via CASCADE)
      paymentRepository.delete(payment);

      // Audit logging for deletion
      try {
        auditService.logPaymentEvent(
            paymentId,
            payment.getPaymentNumber(),
            "PAYMENT_DELETED",
            null,
            null,
            null,
            null);
      } catch (Exception e) {
        logger.error("Failed to log audit event for payment deletion: {}", e.getMessage(), e);
      }
    } catch (Exception e) {
      auditService.logPaymentOperationFailed(
          paymentId,
          null,
          "PAYMENT_DELETE_FAILED",
          e.getMessage(),
          null);
      throw e;
    }
  }

  @Override
  public APPaymentDTO allocateManually(
      UUID paymentId, List<PaymentAllocationRequest> allocations) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Find payment
    APPayment payment = paymentRepository
        .findByCompanyIdAndId(companyId, paymentId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

    // Validate payment is in DRAFT status
    if (payment.getStatus() != PaymentStatus.DRAFT) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot update allocations: only DRAFT payments can be modified. Current status: "
              + payment.getStatus());
    }

    // Validate allocations
    var allocationValidation = paymentValidationService.validateAllocations(allocations, payment.getAmount());
    if (!allocationValidation.isValid()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, formatValidationErrors(allocationValidation));
    }

    // Delete existing allocations
    allocationRepository.deleteByPaymentId(paymentId);

    // Save new allocations
    saveAllocations(payment.getId(), allocations, companyId);

    return toDTO(payment);
  }

  @Override
  public List<PaymentAllocationDTO> allocateFIFO(BigDecimal paymentAmount, Long supplierId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Get open/unpaid bills for supplier (status=POSTED, sorted by due_date ASC)
    List<PurchaseBill> openBills = purchaseBillRepository
        .findByCompanyIdAndStatus(companyId, PurchaseBillStatus.POSTED)
        .stream()
        .filter(bill -> bill.getSupplierId().equals(supplierId))
        .filter(
            bill -> {
              BigDecimal remainingBalance = calculateRemainingBalance(bill.getId());
              return remainingBalance.compareTo(BigDecimal.ZERO) > 0;
            })
        .sorted(Comparator.comparing(PurchaseBill::getDueDate))
        .collect(Collectors.toList());

    if (openBills.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "No open/unpaid bills found for supplier: " + supplierId);
    }

    // Allocate payment amount to bills in FIFO order
    List<PaymentAllocationDTO> allocations = new ArrayList<>();
    BigDecimal remainingAmount = paymentAmount;
    int allocationOrder = 1;

    for (PurchaseBill bill : openBills) {
      if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
        break;
      }

      BigDecimal billRemainingBalance = calculateRemainingBalance(bill.getId());
      BigDecimal allocatedAmount = remainingAmount.min(billRemainingBalance);

      PaymentAllocationDTO allocation = new PaymentAllocationDTO();
      allocation.setPurchaseBillId(bill.getId());
      allocation.setPurchaseBillNumber(bill.getBillNumber());
      allocation.setPurchaseBillDate(bill.getBillDate());
      allocation.setPurchaseBillDueDate(bill.getDueDate());
      allocation.setPurchaseBillTotalAmount(bill.getTotalAmount());
      allocation.setPurchaseBillRemainingBalance(billRemainingBalance);
      allocation.setAllocatedAmount(allocatedAmount.setScale(SCALE, ROUNDING_MODE));
      allocation.setAllocationOrder(allocationOrder++);

      allocations.add(allocation);
      remainingAmount = remainingAmount.subtract(allocatedAmount);
    }

    // Check if payment amount exceeds total open bills
    if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          String.format(
              "Payment amount (%s) exceeds total open bills balance. Unallocated amount: %s",
              paymentAmount, remainingAmount));
    }

    return allocations;
  }

  @Override
  public APPaymentDTO postPayment(UUID paymentId) {
    // AC6.3-09: Performance telemetry - track post latency
    long startTime = System.currentTimeMillis();

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Find payment
    APPayment payment = paymentRepository
        .findByCompanyIdAndId(companyId, paymentId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

    // Validate payment is in DRAFT or PENDING_APPROVAL status
    if (payment.getStatus() != PaymentStatus.DRAFT
        && payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot post payment: only DRAFT or PENDING_APPROVAL payments can be posted. Current status: "
              + payment.getStatus());
    }

    // AC6.3-08: If payment is PENDING_APPROVAL, validate approver role and
    // maker-checker
    if (payment.getStatus() == PaymentStatus.PENDING_APPROVAL) {
      boolean isApprover = org.springframework.security.core.context.SecurityContextHolder.getContext()
          .getAuthentication()
          .getAuthorities()
          .stream()
          .anyMatch(
              auth -> auth.getAuthority().equals("ROLE_CHIEF_ACCOUNTANT")
                  || auth.getAuthority().equals("ROLE_CFO")
                  || auth.getAuthority().equals("ROLE_ADMIN"));

      if (!isApprover) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Cannot post payment: PENDING_APPROVAL payments require Chief Accountant, CFO, or Admin role");
      }

      // Validate approver ≠ creator (maker-checker pattern)
      if (payment.getCreatedById().equals(currentUserId)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Cannot post payment: approver must be different from creator (maker-checker pattern)");
      }

      logger.info(
          "Payment {} (PENDING_APPROVAL) approved by user {} (creator: {})",
          payment.getPaymentNumber(),
          currentUserId,
          payment.getCreatedById());
    }

    // Get allocations
    List<PaymentAllocation> allocations = allocationRepository.findByCompanyIdAndPaymentIdOrderByAllocationOrder(
        companyId, paymentId);

    // AC6.3-05: Validate and get bank account with GL account code
    Long bankAccountEntityId = payment.getCashAccountId() != null ? payment.getCashAccountId()
        : payment.getBankAccountId();
    BankAccount bankAccount = bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountEntityId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Bank/Cash account not found: " + bankAccountEntityId));

    // Validate account is active
    if (!Boolean.TRUE.equals(bankAccount.getActive())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot post payment: selected account is inactive");
    }

    // Validate account has GL account code (AC6.3-05)
    if (bankAccount.getGlAccountCode() == null || bankAccount.getGlAccountCode().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot post payment: account has no GL account code configured");
    }

    // Look up COA account ID from GL account code (AC6.3-05 - mirror Story 6.2
    // pattern)
    Long glCreditAccountId = chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, bankAccount.getGlAccountCode())
        .map(account -> account.getId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "GL account not found for code: " + bankAccount.getGlAccountCode()));

    // Create voucher for payment posting
    VoucherCreateRequest voucherRequest = new VoucherCreateRequest();
    voucherRequest.setDate(payment.getPaymentDate());
    voucherRequest.setDescription(
        String.format(
            "Payment %s to %s - %s",
            payment.getPaymentNumber(),
            getSupplierName(payment.getSupplierId()),
            payment.getReference() != null ? payment.getReference() : ""));

    List<VoucherEntryLineRequest> entryLines = new ArrayList<>();

    // AC6.3-04 & AC6.3-05: Handle standalone expense payments vs allocated supplier
    // payments
    if (payment.getIsStandalone()) {
      // Standalone expense payment: Dr Expense (6xx/8xx), Cr Cash/Bank (via
      // glAccountCode)
      VoucherEntryLineRequest entryLine = new VoucherEntryLineRequest();
      entryLine.setDebitAccountId(getExpenseAccountId(companyId)); // Expense account (6xx/8xx)
      entryLine.setCreditAccountId(glCreditAccountId); // Cash/Bank via glAccountCode
      entryLine.setAmount(payment.getAmount());
      entryLine.setDescription("Standalone expense payment - " + payment.getPayee());
      entryLine.setSupplierId(payment.getSupplierId());
      entryLines.add(entryLine);

      logger.info(
          "Posting standalone expense payment {}: Dr {} Cr {} Amount {}",
          payment.getPaymentNumber(),
          "Expense",
          bankAccount.getGlAccountCode(),
          payment.getAmount());
    } else {
      // Allocated supplier payment: Dr AP 331, Cr Cash/Bank (via glAccountCode)
      for (PaymentAllocation allocation : allocations) {
        PurchaseBill bill = purchaseBillRepository
            .findById(allocation.getPurchaseBillId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Purchase bill not found: " + allocation.getPurchaseBillId()));

        VoucherEntryLineRequest entryLine = new VoucherEntryLineRequest();
        entryLine.setDebitAccountId(getAccountsPayableAccountId(payment.getCompanyId())); // AP account (331)
        entryLine.setCreditAccountId(glCreditAccountId); // Cash/Bank via glAccountCode (1111/1121)
        entryLine.setAmount(allocation.getAllocatedAmount());
        entryLine.setDescription(
            String.format("Payment allocation to bill %s", bill.getBillNumber()));
        entryLine.setSupplierId(payment.getSupplierId());
        entryLines.add(entryLine);
      }
    }

    voucherRequest.setEntryLines(entryLines);

    // Create and post voucher
    var voucherDTO = voucherService.create(voucherRequest);
    voucherPostingService.postVoucher(voucherDTO.getId(), null);

    // Update payment status and link voucher
    payment.setStatus(PaymentStatus.POSTED);
    payment.setLinkedVoucherId(voucherDTO.getId());
    payment.setPostedAt(Instant.now());
    payment.setApprovedById(currentUserId);
    payment = paymentRepository.save(payment);

    // Update bill statuses and remaining balances (only for non-standalone
    // payments)
    if (!payment.getIsStandalone()) {
      for (PaymentAllocation allocation : allocations) {
        PurchaseBill bill = purchaseBillRepository
            .findById(allocation.getPurchaseBillId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Purchase bill not found: " + allocation.getPurchaseBillId()));

        BigDecimal remainingBalance = calculateRemainingBalance(bill.getId());
        BigDecimal newRemainingBalance = remainingBalance.subtract(allocation.getAllocatedAmount());

        if (newRemainingBalance.compareTo(BigDecimal.ZERO) == 0) {
          bill.setStatus(PurchaseBillStatus.PAID);
        } else {
          bill.setStatus(PurchaseBillStatus.PARTIALLY_PAID);
        }

        purchaseBillRepository.save(bill);
      }
    }

    // Invalidate aging cache when payment is posted (affects remaining balances)
    if (agingService != null
        && agingService instanceof com.accounting.service.impl.ap.APAgingServiceImpl) {
      try {
        ((com.accounting.service.impl.ap.APAgingServiceImpl) agingService).invalidateAgingCache();
      } catch (Exception e) {
        logger.warn("Failed to invalidate aging cache after payment post: {}", e.getMessage());
      }
    }

    // Log audit event
    try {
      JsonNode afterSnapshot = serializePaymentToJson(payment);
      auditService.logPaymentEvent(
          payment.getId(),
          payment.getPaymentNumber(),
          "PAYMENT_POSTED",
          null, // before snapshot (could be captured before status change)
          afterSnapshot,
          null, // diff hash
          null); // HttpServletRequest not available in service layer
    } catch (Exception e) {
      logger.error("Failed to log audit event for payment posting: {}", e.getMessage(), e);
    }

    // AC6.3-09: Log performance telemetry
    long endTime = System.currentTimeMillis();
    long latencyMs = endTime - startTime;
    logger.info(
        "Payment {} posted successfully in {} ms (target: ≤10000 ms)",
        payment.getPaymentNumber(),
        latencyMs);
    if (latencyMs > 10000) {
      logger.warn(
          "Payment posting latency {} ms exceeds target of 10000 ms for payment {}",
          latencyMs,
          payment.getPaymentNumber());
    }

    // Trigger embedding for RAG chatbot (fire-and-forget)
    String supplierName = getSupplierName(payment.getSupplierId());
    String bankAccountName = bankAccount.getBankName();
    embeddingTriggerService.triggerAPPaymentEmbedding(
        payment, allocations, supplierName, bankAccountName, EmbeddingAction.UPSERT);

    return toDTO(payment);
  }

  @Override
  public APPaymentDTO cancelPayment(UUID paymentId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Find payment
    APPayment payment = paymentRepository
        .findByCompanyIdAndId(companyId, paymentId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

    // Validate payment is in DRAFT status
    if (payment.getStatus() != PaymentStatus.DRAFT) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot cancel payment: only DRAFT payments can be cancelled. Current status: "
              + payment.getStatus());
    }

    // Update payment status
    payment.setStatus(PaymentStatus.CANCELLED);
    payment = paymentRepository.save(payment);

    // Delete allocations (they will be deleted via CASCADE, but we can also delete
    // explicitly)
    allocationRepository.deleteByPaymentId(paymentId);

    return toDTO(payment);
  }

  @Override
  public List<PurchaseBillDTO> getOpenBillsForSupplier(Long supplierId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Get open/unpaid bills (status=POSTED, remaining_balance > 0)
    List<PurchaseBill> openBills = purchaseBillRepository
        .findByCompanyIdAndStatus(companyId, PurchaseBillStatus.POSTED)
        .stream()
        .filter(bill -> bill.getSupplierId().equals(supplierId))
        .filter(
            bill -> {
              BigDecimal remainingBalance = calculateRemainingBalance(bill.getId());
              return remainingBalance.compareTo(BigDecimal.ZERO) > 0;
            })
        .sorted(Comparator.comparing(PurchaseBill::getDueDate))
        .collect(Collectors.toList());

    // Convert to DTOs
    return openBills.stream()
        .map(bill -> purchaseBillService.findById(bill.getId()).orElse(null))
        .filter(dto -> dto != null)
        .collect(Collectors.toList());
  }

  /**
   * Generate unique payment number in format PAY-YYYY-XXXXX.
   */
  private String generatePaymentNumber(LocalDate paymentDate, Long companyId) {
    int year = paymentDate.getYear();
    LocalDate yearStart = LocalDate.of(year, 1, 1);
    LocalDate yearEnd = LocalDate.of(year + 1, 1, 1);

    // Find the highest sequence number for this year
    int maxSequence = 0;
    List<APPayment> existingPayments = paymentRepository.findByCompanyIdAndPaymentDateBetween(companyId, yearStart,
        yearEnd);
    for (APPayment existing : existingPayments) {
      String existingNumber = existing.getPaymentNumber();
      if (existingNumber.startsWith(PAYMENT_NUMBER_PREFIX + year + "-")) {
        try {
          String sequenceStr = existingNumber.substring(PAYMENT_NUMBER_PREFIX.length() + 5);
          int sequence = Integer.parseInt(sequenceStr);
          maxSequence = Math.max(maxSequence, sequence);
        } catch (NumberFormatException e) {
          // Ignore invalid format
        }
      }
    }

    // Generate new sequence number
    int newSequence = maxSequence + 1;
    return String.format("%s%d-%05d", PAYMENT_NUMBER_PREFIX, year, newSequence);
  }

  /**
   * Save payment allocations.
   */
  private void saveAllocations(
      UUID paymentId, List<PaymentAllocationRequest> allocations, Long companyId) {
    int allocationOrder = 1;
    List<PaymentAllocation> allocationEntities = new ArrayList<>();

    for (PaymentAllocationRequest request : allocations) {
      PaymentAllocation allocation = new PaymentAllocation();
      allocation.setCompanyId(companyId);
      allocation.setPaymentId(paymentId);
      allocation.setPurchaseBillId(request.getPurchaseBillId());
      allocation.setAllocatedAmount(request.getAllocatedAmount().setScale(SCALE, ROUNDING_MODE));
      allocation.setAllocationOrder(allocationOrder++);
      allocationEntities.add(allocation);
    }

    allocationRepository.saveAll(allocationEntities);
  }

  /**
   * Calculate remaining balance for a purchase bill.
   */
  private BigDecimal calculateRemainingBalance(UUID billId) {
    PurchaseBill bill = purchaseBillRepository
        .findById(billId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Purchase bill not found: " + billId));

    BigDecimal totalAllocated = allocationRepository.calculateTotalAllocatedAmount(billId);
    return bill.getTotalAmount().subtract(totalAllocated);
  }

  /**
   * Get Accounts Payable account ID (code 331) for the company.
   * TT200 standard: Account 331 is "Phải trả người bán" (Accounts Payable).
   *
   * @param companyId company ID
   * @return account ID for AP account (331)
   * @throws ResponseStatusException if account not found
   */
  private Long getAccountsPayableAccountId(Long companyId) {
    return chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, "331")
        .map(account -> account.getId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Accounts Payable account (331) not found in chart of accounts. "
                    + "Please ensure the chart of accounts is properly configured."));
  }

  /**
   * Serialize payment entity to JSON node for audit logging.
   *
   * @param payment payment entity
   * @return JSON node representation of payment
   */
  private JsonNode serializePaymentToJson(APPayment payment) {
    try {
      return objectMapper.valueToTree(payment);
    } catch (Exception e) {
      logger.warn("Failed to serialize payment to JSON for audit: {}", e.getMessage());
      // Return minimal JSON structure if serialization fails
      com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
      node.put("id", payment.getId().toString());
      node.put("paymentNumber", payment.getPaymentNumber());
      node.put("amount", payment.getAmount().toString());
      node.put("status", payment.getStatus().toString());
      return node;
    }
  }

  /**
   * Convert entity to list DTO.
   */
  private APPaymentListDTO toListDTO(APPayment payment) {
    Supplier supplier = supplierRepository
        .findByCompanyIdAndId(payment.getCompanyId(), payment.getSupplierId())
        .orElse(null);

    BankAccount cashAccount = null;
    if (payment.getCashAccountId() != null) {
      cashAccount = bankAccountRepository
          .findByCompanyIdAndId(payment.getCompanyId(), payment.getCashAccountId())
          .orElse(null);
    }

    BankAccount bankAccount = null;
    if (payment.getBankAccountId() != null) {
      bankAccount = bankAccountRepository
          .findByCompanyIdAndId(payment.getCompanyId(), payment.getBankAccountId())
          .orElse(null);
    }

    // Count allocations
    int allocationCount = allocationRepository.findByPaymentIdOrderByAllocationOrder(payment.getId()).size();

    APPaymentListDTO dto = new APPaymentListDTO();
    dto.setId(payment.getId());
    dto.setPaymentNumber(payment.getPaymentNumber());
    dto.setPaymentDate(payment.getPaymentDate());
    dto.setSupplierId(payment.getSupplierId());
    dto.setSupplierName(supplier != null ? supplier.getName() : null);
    dto.setSupplierCode(supplier != null ? supplier.getCode() : null);
    dto.setAmount(payment.getAmount());
    dto.setCashAccountId(payment.getCashAccountId());
    dto.setCashAccountName(cashAccount != null ? cashAccount.getAccountNumber() : null);
    dto.setBankAccountId(payment.getBankAccountId());
    dto.setBankAccountName(bankAccount != null ? bankAccount.getAccountNumber() : null);
    dto.setPaymentMethod(payment.getPaymentMethod());
    dto.setIsStandalone(payment.getIsStandalone());
    dto.setStatus(payment.getStatus());
    dto.setAllocationCount(allocationCount);
    dto.setLinkedVoucherId(payment.getLinkedVoucherId());

    return dto;
  }

  /**
   * Convert entity to full DTO.
   */
  private APPaymentDTO toDTO(APPayment payment) {
    Supplier supplier = supplierRepository
        .findByCompanyIdAndId(payment.getCompanyId(), payment.getSupplierId())
        .orElse(null);

    BankAccount cashAccount = null;
    if (payment.getCashAccountId() != null) {
      cashAccount = bankAccountRepository
          .findByCompanyIdAndId(payment.getCompanyId(), payment.getCashAccountId())
          .orElse(null);
    }

    BankAccount bankAccount = null;
    if (payment.getBankAccountId() != null) {
      bankAccount = bankAccountRepository
          .findByCompanyIdAndId(payment.getCompanyId(), payment.getBankAccountId())
          .orElse(null);
    }

    String createdByName = getUserName(payment.getCreatedById());
    String approvedByName = payment.getApprovedById() != null ? getUserName(payment.getApprovedById()) : null;

    // Load allocations
    List<PaymentAllocation> allocations = allocationRepository.findByCompanyIdAndPaymentIdOrderByAllocationOrder(
        payment.getCompanyId(), payment.getId());
    List<PaymentAllocationDTO> allocationDTOs = allocations.stream().map(this::toAllocationDTO)
        .collect(Collectors.toList());

    APPaymentDTO dto = new APPaymentDTO();
    dto.setId(payment.getId());
    dto.setCompanyId(payment.getCompanyId());
    dto.setSupplierId(payment.getSupplierId());
    dto.setSupplierName(supplier != null ? supplier.getName() : null);
    dto.setSupplierCode(supplier != null ? supplier.getCode() : null);
    dto.setPaymentNumber(payment.getPaymentNumber());
    dto.setPaymentDate(payment.getPaymentDate());
    dto.setDueDate(payment.getDueDate());
    dto.setCashAccountId(payment.getCashAccountId());
    dto.setCashAccountName(cashAccount != null ? cashAccount.getAccountNumber() : null);
    dto.setCashAccountNumber(cashAccount != null ? cashAccount.getAccountNumber() : null);
    dto.setBankAccountId(payment.getBankAccountId());
    dto.setBankAccountName(bankAccount != null ? bankAccount.getAccountNumber() : null);
    dto.setBankAccountNumber(bankAccount != null ? bankAccount.getAccountNumber() : null);
    dto.setPayee(payment.getPayee());
    dto.setAmount(payment.getAmount());
    dto.setReference(payment.getReference());
    dto.setPaymentMethod(payment.getPaymentMethod());
    dto.setPaymentProofUrl(payment.getPaymentProofUrl());
    dto.setIsStandalone(payment.getIsStandalone());
    dto.setStatus(payment.getStatus());
    dto.setCreatedById(payment.getCreatedById());
    dto.setCreatedByName(createdByName);
    dto.setApprovedById(payment.getApprovedById());
    dto.setApprovedByName(approvedByName);
    dto.setLinkedVoucherId(payment.getLinkedVoucherId());
    dto.setCreatedAt(payment.getCreatedAt());
    dto.setUpdatedAt(payment.getUpdatedAt());
    dto.setPostedAt(payment.getPostedAt());
    dto.setAllocations(allocationDTOs);

    return dto;
  }

  /**
   * Convert PaymentAllocation entity to DTO.
   */
  private PaymentAllocationDTO toAllocationDTO(PaymentAllocation allocation) {
    PurchaseBill bill = purchaseBillRepository
        .findById(allocation.getPurchaseBillId())
        .orElse(null);

    PaymentAllocationDTO dto = new PaymentAllocationDTO();
    dto.setId(allocation.getId());
    dto.setPaymentId(allocation.getPaymentId());
    dto.setPurchaseBillId(allocation.getPurchaseBillId());
    dto.setPurchaseBillNumber(bill != null ? bill.getBillNumber() : null);
    dto.setPurchaseBillDate(bill != null ? bill.getBillDate() : null);
    dto.setPurchaseBillDueDate(bill != null ? bill.getDueDate() : null);
    dto.setPurchaseBillTotalAmount(bill != null ? bill.getTotalAmount() : null);
    dto.setPurchaseBillRemainingBalance(
        bill != null ? calculateRemainingBalance(bill.getId()) : null);
    dto.setAllocatedAmount(allocation.getAllocatedAmount());
    dto.setAllocationOrder(allocation.getAllocationOrder());
    dto.setCreatedAt(allocation.getCreatedAt());

    return dto;
  }

  /**
   * Convert request to entity (for validation).
   */
  private APPayment toEntity(APPaymentCreateRequest request, Long companyId, Long createdById) {
    APPayment payment = new APPayment();
    payment.setCompanyId(companyId);
    payment.setSupplierId(request.getSupplierId());
    payment.setPaymentDate(request.getPaymentDate());
    payment.setCashAccountId(request.getCashAccountId());
    payment.setBankAccountId(request.getBankAccountId());
    payment.setAmount(request.getAmount());
    payment.setIsStandalone(request.getIsStandalone());
    payment.setCreatedById(createdById);
    return payment;
  }

  /**
   * Get supplier name by ID.
   */
  private String getSupplierName(Long supplierId) {
    return supplierRepository
        .findById(supplierId)
        .map(Supplier::getName)
        .orElse("Unknown Supplier");
  }

  /**
   * Get user name by ID.
   */
  private String getUserName(Long userId) {
    if (userId == null) {
      return null;
    }
    return userRepository.findById(userId).map(User::getFullName).orElse("Unknown");
  }

  /**
   * Get approval threshold from company settings.
   */
  private BigDecimal getApprovalThreshold() {
    try {
      var settings = companySettingsService.getCurrentCompanySettings();
      if (settings.getApprovalThresholdAmount() != null) {
        return settings.getApprovalThresholdAmount();
      }
    } catch (Exception e) {
      logger.warn("Failed to get approval threshold from company settings", e);
    }
    // Default threshold: 20,000,000 VND
    return new BigDecimal("20000000.00");
  }

  /**
   * Format validation errors for exception message.
   */
  private String formatValidationErrors(com.accounting.dto.PaymentValidationResult result) {
    StringBuilder sb = new StringBuilder();
    result.getFieldErrors().forEach(
        (field, error) -> sb.append(field).append(": ").append(error).append("; "));
    return sb.toString();
  }

  /**
   * Get default expense account ID for standalone payments.
   * AC6.3-04: Uses account 6421 (Chi phí quản lý kinh doanh - Operating Expense)
   * as default.
   *
   * @param companyId company ID
   * @return account ID for default expense account
   * @throws ResponseStatusException if account not found
   */
  private Long getExpenseAccountId(Long companyId) {
    // Try common expense accounts in order of preference
    String[] expenseAccountCodes = { "6421", "6411", "642", "641", "6" };

    for (String code : expenseAccountCodes) {
      var account = chartOfAccountsRepository.findByCompanyIdAndCode(companyId, code);
      if (account.isPresent() && Boolean.TRUE.equals(account.get().getPostable())) {
        return account.get().getId();
      }
    }

    throw new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Default expense account (6421/6411/642/641) not found in chart of accounts. "
            + "Please ensure the chart of accounts is properly configured.");
  }

  @Override
  public APPaymentDTO approvePayment(UUID paymentId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Find payment
    APPayment payment = paymentRepository
        .findByCompanyIdAndId(companyId, paymentId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

    // Validate payment is in PENDING_APPROVAL status
    if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot approve payment: only PENDING_APPROVAL payments can be approved. Current status: "
              + payment.getStatus());
    }

    // AC6.3-08: Validate approver role
    boolean isApprover = org.springframework.security.core.context.SecurityContextHolder.getContext()
        .getAuthentication()
        .getAuthorities()
        .stream()
        .anyMatch(
            auth -> auth.getAuthority().equals("ROLE_CHIEF_ACCOUNTANT")
                || auth.getAuthority().equals("ROLE_CFO")
                || auth.getAuthority().equals("ROLE_ADMIN"));

    if (!isApprover) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Cannot approve payment: requires Chief Accountant, CFO, or Admin role");
    }

    // AC6.3-08: Validate approver ≠ creator (maker-checker pattern)
    if (payment.getCreatedById().equals(currentUserId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Cannot approve payment: approver must be different from creator (maker-checker pattern)");
    }

    // Update status to DRAFT (approved, ready for posting)
    payment.setStatus(PaymentStatus.DRAFT);
    payment.setApprovedById(currentUserId);
    payment = paymentRepository.save(payment);

    // AC6.3-10: Audit logging
    try {
      JsonNode afterSnapshot = serializePaymentToJson(payment);
      auditService.logPaymentEvent(
          payment.getId(),
          payment.getPaymentNumber(),
          "PAYMENT_APPROVED",
          null,
          afterSnapshot,
          null,
          null);
    } catch (Exception e) {
      logger.error("Failed to log audit event for payment approval: {}", e.getMessage(), e);
    }

    logger.info(
        "Payment {} approved by user {} (creator: {})",
        payment.getPaymentNumber(),
        currentUserId,
        payment.getCreatedById());

    return toDTO(payment);
  }

  @Override
  public APPaymentDTO rejectPayment(UUID paymentId, String reason) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Rejection reason is required");
    }

    // Find payment
    APPayment payment = paymentRepository
        .findByCompanyIdAndId(companyId, paymentId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

    // Validate payment is in PENDING_APPROVAL status
    if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot reject payment: only PENDING_APPROVAL payments can be rejected. Current status: "
              + payment.getStatus());
    }

    // AC6.3-08: Validate approver role
    boolean isApprover = org.springframework.security.core.context.SecurityContextHolder.getContext()
        .getAuthentication()
        .getAuthorities()
        .stream()
        .anyMatch(
            auth -> auth.getAuthority().equals("ROLE_CHIEF_ACCOUNTANT")
                || auth.getAuthority().equals("ROLE_CFO")
                || auth.getAuthority().equals("ROLE_ADMIN"));

    if (!isApprover) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Cannot reject payment: requires Chief Accountant, CFO, or Admin role");
    }

    // Update status to REJECTED
    payment.setStatus(PaymentStatus.REJECTED);
    payment.setReference(
        (payment.getReference() != null ? payment.getReference() + " | " : "")
            + "REJECTED: " + reason);
    payment = paymentRepository.save(payment);

    // AC6.3-10: Audit logging
    try {
      JsonNode afterSnapshot = serializePaymentToJson(payment);
      auditService.logPaymentEvent(
          payment.getId(),
          payment.getPaymentNumber(),
          "PAYMENT_REJECTED",
          null,
          afterSnapshot,
          null,
          null);
    } catch (Exception e) {
      logger.error("Failed to log audit event for payment rejection: {}", e.getMessage(), e);
    }

    logger.info(
        "Payment {} rejected by user {} with reason: {}",
        payment.getPaymentNumber(),
        currentUserId,
        reason);

    return toDTO(payment);
  }

  @Override
  public APPaymentDTO reversePayment(UUID paymentId, String reason) {
    // AC6.3-10: Performance telemetry - track reversal latency
    long startTime = System.currentTimeMillis();

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Validate reason is provided (mandatory per AC6.3-10)
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Reversal reason is required");
    }

    // Find payment
    APPayment payment = paymentRepository
        .findByCompanyIdAndId(companyId, paymentId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

    // Validate payment is in POSTED status
    if (payment.getStatus() != PaymentStatus.POSTED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot reverse payment: only POSTED payments can be reversed. Current status: "
              + payment.getStatus());
    }

    // Get bank account for GL account code lookup
    Long bankAccountEntityId = payment.getCashAccountId() != null ? payment.getCashAccountId()
        : payment.getBankAccountId();
    BankAccount bankAccount = bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountEntityId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Bank/Cash account not found: " + bankAccountEntityId));

    // Look up GL account ID
    Long glAccountId = chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, bankAccount.getGlAccountCode())
        .map(account -> account.getId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "GL account not found for code: " + bankAccount.getGlAccountCode()));

    // Create reversing voucher (swap Dr/Cr from original)
    VoucherCreateRequest reversingVoucherRequest = new VoucherCreateRequest();
    reversingVoucherRequest.setDate(LocalDate.now());
    reversingVoucherRequest.setDescription(
        String.format(
            "Reversal of Payment %s - Reason: %s",
            payment.getPaymentNumber(),
            reason));

    List<VoucherEntryLineRequest> entryLines = new ArrayList<>();

    if (payment.getIsStandalone()) {
      // Reverse standalone: Cr Expense (6xx/8xx), Dr Cash/Bank
      VoucherEntryLineRequest entryLine = new VoucherEntryLineRequest();
      entryLine.setDebitAccountId(glAccountId); // Cash/Bank (was Credit)
      entryLine.setCreditAccountId(getExpenseAccountId(companyId)); // Expense (was Debit)
      entryLine.setAmount(payment.getAmount());
      entryLine.setDescription("Reversal - Standalone expense payment");
      entryLine.setSupplierId(payment.getSupplierId());
      entryLines.add(entryLine);
    } else {
      // Reverse allocated: Cr AP 331, Dr Cash/Bank
      List<PaymentAllocation> allocations = allocationRepository
          .findByCompanyIdAndPaymentIdOrderByAllocationOrder(companyId, paymentId);

      for (PaymentAllocation allocation : allocations) {
        PurchaseBill bill = purchaseBillRepository
            .findById(allocation.getPurchaseBillId())
            .orElse(null);

        VoucherEntryLineRequest entryLine = new VoucherEntryLineRequest();
        entryLine.setDebitAccountId(glAccountId); // Cash/Bank (was Credit)
        entryLine.setCreditAccountId(getAccountsPayableAccountId(payment.getCompanyId())); // AP (was Debit)
        entryLine.setAmount(allocation.getAllocatedAmount());
        entryLine.setDescription(
            String.format("Reversal - Payment allocation to bill %s",
                bill != null ? bill.getBillNumber() : allocation.getPurchaseBillId()));
        entryLine.setSupplierId(payment.getSupplierId());
        entryLines.add(entryLine);

        // Revert bill status
        if (bill != null) {
          if (bill.getStatus() == PurchaseBillStatus.PAID) {
            bill.setStatus(PurchaseBillStatus.PARTIALLY_PAID);
          } else if (bill.getStatus() == PurchaseBillStatus.PARTIALLY_PAID) {
            // Check if this was the only payment
            BigDecimal otherPayments = allocationRepository
                .calculateTotalAllocatedAmount(bill.getId())
                .subtract(allocation.getAllocatedAmount());
            if (otherPayments.compareTo(BigDecimal.ZERO) == 0) {
              bill.setStatus(PurchaseBillStatus.POSTED);
            }
          }
          purchaseBillRepository.save(bill);
        }
      }
    }

    reversingVoucherRequest.setEntryLines(entryLines);

    // Create and post reversing voucher
    var reversingVoucherDTO = voucherService.create(reversingVoucherRequest);
    voucherPostingService.postVoucher(reversingVoucherDTO.getId(), null);

    // Update payment status
    payment.setStatus(PaymentStatus.REVERSED);
    payment.setReference(
        (payment.getReference() != null ? payment.getReference() + " | " : "")
            + "REVERSED: " + reason);
    payment = paymentRepository.save(payment);

    // Invalidate aging cache
    if (agingService != null
        && agingService instanceof com.accounting.service.impl.ap.APAgingServiceImpl) {
      try {
        ((com.accounting.service.impl.ap.APAgingServiceImpl) agingService).invalidateAgingCache();
      } catch (Exception e) {
        logger.warn("Failed to invalidate aging cache after payment reversal: {}", e.getMessage());
      }
    }

    // AC6.3-10: Audit logging with cross-reference
    try {
      JsonNode afterSnapshot = serializePaymentToJson(payment);
      auditService.logPaymentEvent(
          payment.getId(),
          payment.getPaymentNumber(),
          "PAYMENT_REVERSED",
          null,
          afterSnapshot,
          null,
          null);
    } catch (Exception e) {
      logger.error("Failed to log audit event for payment reversal: {}", e.getMessage(), e);
    }

    // Performance telemetry
    long endTime = System.currentTimeMillis();
    long latencyMs = endTime - startTime;
    logger.info(
        "Payment {} reversed successfully in {} ms by user {} with reason: {}",
        payment.getPaymentNumber(),
        latencyMs,
        currentUserId,
        reason);

    // Trigger embedding deletion for RAG chatbot (fire-and-forget)
    String supplierName = getSupplierName(payment.getSupplierId());
    String bankAccountName = bankAccount.getBankName();
    List<PaymentAllocation> allAllocations = allocationRepository
        .findByCompanyIdAndPaymentIdOrderByAllocationOrder(companyId, paymentId);
    embeddingTriggerService.triggerAPPaymentEmbedding(
        payment, allAllocations, supplierName, bankAccountName, EmbeddingAction.DELETE);

    return toDTO(payment);
  }
}

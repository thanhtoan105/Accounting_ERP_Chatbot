package com.accounting.service.impl.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.ARPaymentCreateRequest;
import com.accounting.dto.ARPaymentDTO;
import com.accounting.dto.ARPaymentListDTO;
import com.accounting.dto.ReceiptAllocationDTO;
import com.accounting.dto.ReceiptAllocationRequest;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.entity.ARPayment;
import com.accounting.entity.BankAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.ReceiptAllocation;
import com.accounting.entity.ReceiptStatus;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.entity.User;
import com.accounting.repository.ARPaymentRepository;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.ReceiptAllocationRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.CompanySettingsService;
import com.accounting.service.ReceiptService;
import com.accounting.service.ReceiptValidationService;
import com.accounting.service.VoucherService;
import com.accounting.service.voucher.VoucherPostingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Implementation of ReceiptService for AR receipt operations.
 * Handles receipt creation, allocation to invoices, posting with voucher
 * generation, and reversal.
 */
@Service
@Transactional
public class ReceiptServiceImpl implements ReceiptService {

  private static final Logger logger = LoggerFactory.getLogger(ReceiptServiceImpl.class);

  private static final int SCALE = 2;
  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
  private static final String RECEIPT_NUMBER_PREFIX = "CR-";

  private final ARPaymentRepository receiptRepository;
  private final ReceiptAllocationRepository allocationRepository;
  private final SalesInvoiceRepository salesInvoiceRepository;
  private final CustomerRepository customerRepository;
  private final BankAccountRepository bankAccountRepository;
  private final UserRepository userRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final ReceiptValidationService receiptValidationService;
  private final VoucherService voucherService;
  private final VoucherPostingService voucherPostingService;
  private final AuditService auditService;
  private final CompanySettingsService companySettingsService;
  private final ObjectMapper objectMapper;
  private final EntityManager entityManager;
  private final com.accounting.service.ARAgingService arAgingService;

  public ReceiptServiceImpl(
      ARPaymentRepository receiptRepository,
      ReceiptAllocationRepository allocationRepository,
      SalesInvoiceRepository salesInvoiceRepository,
      CustomerRepository customerRepository,
      BankAccountRepository bankAccountRepository,
      UserRepository userRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      ReceiptValidationService receiptValidationService,
      VoucherService voucherService,
      VoucherPostingService voucherPostingService,
      AuditService auditService,
      CompanySettingsService companySettingsService,
      ObjectMapper objectMapper,
      EntityManager entityManager,
      com.accounting.service.ARAgingService arAgingService) {
    this.receiptRepository = receiptRepository;
    this.allocationRepository = allocationRepository;
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.customerRepository = customerRepository;
    this.bankAccountRepository = bankAccountRepository;
    this.userRepository = userRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.receiptValidationService = receiptValidationService;
    this.voucherService = voucherService;
    this.voucherPostingService = voucherPostingService;
    this.auditService = auditService;
    this.companySettingsService = companySettingsService;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
    this.arAgingService = arAgingService;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ARPaymentListDTO> findAll(Pageable pageable, Map<String, Object> filters) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<ARPayment> query = cb.createQuery(ARPayment.class);
    Root<ARPayment> root = query.from(ARPayment.class);

    List<Predicate> predicates = new ArrayList<>();
    predicates.add(cb.equal(root.get("companyId"), companyId));

    // Apply filters
    if (filters.containsKey("customerId")) {
      predicates.add(cb.equal(root.get("customerId"), filters.get("customerId")));
    }

    if (filters.containsKey("status")) {
      Object statusObj = filters.get("status");
      ReceiptStatus status;
      if (statusObj instanceof ReceiptStatus) {
        status = (ReceiptStatus) statusObj;
      } else {
        status = ReceiptStatus.valueOf(statusObj.toString());
      }
      predicates.add(cb.equal(root.get("status"), status));
    }

    if (filters.containsKey("dateFrom")) {
      LocalDate dateFrom = (LocalDate) filters.get("dateFrom");
      predicates.add(cb.greaterThanOrEqualTo(root.get("receiptDate"), dateFrom));
    }

    if (filters.containsKey("dateTo")) {
      LocalDate dateTo = (LocalDate) filters.get("dateTo");
      predicates.add(cb.lessThanOrEqualTo(root.get("receiptDate"), dateTo));
    }

    if (filters.containsKey("standalone")) {
      Boolean standalone = (Boolean) filters.get("standalone");
      predicates.add(cb.equal(root.get("isStandalone"), standalone));
    }

    if (filters.containsKey("search") && filters.get("search") != null) {
      String search = "%" + filters.get("search").toString().toLowerCase() + "%";
      predicates.add(
          cb.or(
              cb.like(cb.lower(root.get("receiptNumber")), search),
              cb.like(cb.lower(root.get("payee")), search),
              cb.like(cb.lower(root.get("reference")), search)));
    }

    query.where(predicates.toArray(new Predicate[0]));

    // Apply sorting
    if (pageable.getSort().isSorted()) {
      pageable
          .getSort()
          .forEach(
              order -> {
                if (order.isAscending()) {
                  query.orderBy(cb.asc(root.get(order.getProperty())));
                } else {
                  query.orderBy(cb.desc(root.get(order.getProperty())));
                }
              });
    } else {
      query.orderBy(cb.desc(root.get("receiptDate")));
    }

    // Execute query with pagination
    List<ARPayment> receipts = entityManager
        .createQuery(query)
        .setFirstResult((int) pageable.getOffset())
        .setMaxResults(pageable.getPageSize())
        .getResultList();

    // Get total count - rebuild predicates with countRoot
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<ARPayment> countRoot = countQuery.from(ARPayment.class);
    countQuery.select(cb.count(countRoot));

    // Rebuild predicates for count query using countRoot
    List<Predicate> countPredicates = new ArrayList<>();
    countPredicates.add(cb.equal(countRoot.get("companyId"), companyId));

    // Apply filters with countRoot
    if (filters.containsKey("customerId")) {
      countPredicates.add(cb.equal(countRoot.get("customerId"), filters.get("customerId")));
    }

    if (filters.containsKey("status")) {
      Object statusObj = filters.get("status");
      ReceiptStatus status;
      if (statusObj instanceof ReceiptStatus) {
        status = (ReceiptStatus) statusObj;
      } else {
        status = ReceiptStatus.valueOf(statusObj.toString());
      }
      countPredicates.add(cb.equal(countRoot.get("status"), status));
    }

    if (filters.containsKey("dateFrom")) {
      LocalDate dateFrom = (LocalDate) filters.get("dateFrom");
      countPredicates.add(cb.greaterThanOrEqualTo(countRoot.get("receiptDate"), dateFrom));
    }

    if (filters.containsKey("dateTo")) {
      LocalDate dateTo = (LocalDate) filters.get("dateTo");
      countPredicates.add(cb.lessThanOrEqualTo(countRoot.get("receiptDate"), dateTo));
    }

    if (filters.containsKey("standalone")) {
      Boolean standalone = (Boolean) filters.get("standalone");
      countPredicates.add(cb.equal(countRoot.get("isStandalone"), standalone));
    }

    if (filters.containsKey("search") && filters.get("search") != null) {
      String search = "%" + filters.get("search").toString().toLowerCase() + "%";
      countPredicates.add(
          cb.or(
              cb.like(cb.lower(countRoot.get("receiptNumber")), search),
              cb.like(cb.lower(countRoot.get("payee")), search),
              cb.like(cb.lower(countRoot.get("reference")), search)));
    }

    countQuery.where(countPredicates.toArray(new Predicate[0]));
    Long totalCount = entityManager.createQuery(countQuery).getSingleResult();

    // Convert to DTOs
    List<ARPaymentListDTO> dtos = receipts.stream().map(this::convertToListDTO).collect(Collectors.toList());

    return new PageImpl<>(dtos, pageable, totalCount);
  }

  @Override
  @Transactional(readOnly = true)
  public ARPaymentDTO findById(UUID receiptId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    ARPayment receipt = receiptRepository
        .findByCompanyIdAndId(companyId, receiptId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Receipt not found: " + receiptId));

    return convertToDTO(receipt);
  }

  @Override
  public ARPaymentDTO create(ARPaymentCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Validate customer exists
    Customer customer = customerRepository
        .findByCompanyIdAndId(companyId, request.getCustomerId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Customer not found: " + request.getCustomerId()));

    // Validate bank/cash account
    if (request.getCashAccountId() != null && request.getBankAccountId() != null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot specify both cash and bank account");
    }

    if (request.getCashAccountId() == null && request.getBankAccountId() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Must specify either cash or bank account");
    }

    Long accountId = request.getCashAccountId() != null
        ? request.getCashAccountId()
        : request.getBankAccountId();
    BankAccount account = bankAccountRepository
        .findByCompanyIdAndId(companyId, accountId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Account not found: " + accountId));

    // Validate account is active (AC6.2-03)
    if (!Boolean.TRUE.equals(account.getActive())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot create receipt: selected account is inactive");
    }

    // Validate account has GL account code for posting (AC6.2-03)
    if (account.getGlAccountCode() == null || account.getGlAccountCode().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot create receipt: account has no GL account code configured");
    }

    // Create receipt entity
    ARPayment receipt = new ARPayment();
    receipt.setCompanyId(companyId);
    receipt.setCustomerId(request.getCustomerId());
    receipt.setReceiptDate(request.getReceiptDate());
    receipt.setReceiptNumber(generateReceiptNumber(receipt.getReceiptDate()));
    receipt.setCashAccountId(request.getCashAccountId());
    receipt.setBankAccountId(request.getBankAccountId());
    receipt.setPayee(
        request.getPayee() != null && !request.getPayee().isBlank()
            ? request.getPayee()
            : customer.getName());
    receipt.setAmount(request.getAmount());
    receipt.setReference(request.getReference());
    receipt.setPaymentMethod(request.getPaymentMethod());
    receipt.setReceiptProofUrl(request.getReceiptProofUrl());
    receipt.setIsStandalone(request.getIsStandalone() != null ? request.getIsStandalone() : false);
    receipt.setStatus(ReceiptStatus.DRAFT);
    receipt.setCreatedById(currentUserId);
    receipt.setCreatedAt(Instant.now());
    receipt.setUpdatedAt(Instant.now());

    receipt = receiptRepository.save(receipt);

    // Create allocations if provided
    if (request.getAllocations() != null && !request.getAllocations().isEmpty()) {
      saveAllocations(receipt.getId(), request.getAllocations());
    }

    // AC6.2-09: Check if receipt amount exceeds threshold - requires approval
    // before posting
    BigDecimal approvalThreshold = getReceiptApprovalThreshold();
    if (receipt.getAmount().compareTo(approvalThreshold) > 0) {
      receipt.setStatus(ReceiptStatus.PENDING_APPROVAL);
      receipt = receiptRepository.save(receipt);
      logger.info(
          "Receipt {} requires approval (amount: {}, threshold: {})",
          receipt.getReceiptNumber(),
          receipt.getAmount(),
          approvalThreshold);
    } else {
      logger.info(
          "Receipt {} auto-approved for posting (amount: {}, threshold: {})",
          receipt.getReceiptNumber(),
          receipt.getAmount(),
          approvalThreshold);
    }

    // Log audit event
    logAuditEvent(receipt, "CREATE", null, receipt);

    return convertToDTO(receipt);
  }

  @Override
  public ARPaymentDTO update(UUID receiptId, ARPaymentCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    ARPayment receipt = receiptRepository
        .findByCompanyIdAndId(companyId, receiptId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Receipt not found: " + receiptId));

    // Only DRAFT or PENDING_APPROVAL receipts can be updated
    if (receipt.getStatus() != ReceiptStatus.DRAFT
        && receipt.getStatus() != ReceiptStatus.PENDING_APPROVAL) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only DRAFT or PENDING_APPROVAL receipts can be updated");
    }

    // Update fields
    receipt.setCustomerId(request.getCustomerId());
    receipt.setReceiptDate(request.getReceiptDate());
    receipt.setCashAccountId(request.getCashAccountId());
    receipt.setBankAccountId(request.getBankAccountId());
    receipt.setPayee(request.getPayee());
    receipt.setAmount(request.getAmount());
    receipt.setReference(request.getReference());
    receipt.setPaymentMethod(request.getPaymentMethod());
    receipt.setReceiptProofUrl(request.getReceiptProofUrl());
    receipt.setIsStandalone(request.getIsStandalone() != null ? request.getIsStandalone() : false);
    receipt.setUpdatedAt(Instant.now());

    receipt = receiptRepository.save(receipt);

    // Update allocations
    if (request.getAllocations() != null) {
      // Delete existing allocations
      allocationRepository.deleteByReceiptId(receiptId);
      // Create new allocations
      saveAllocations(receiptId, request.getAllocations());
    }

    // Log audit event
    logAuditEvent(receipt, "UPDATE", null, receipt);

    return convertToDTO(receipt);
  }

  @Override
  public void delete(UUID receiptId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    ARPayment receipt = receiptRepository
        .findByCompanyIdAndId(companyId, receiptId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Receipt not found: " + receiptId));

    // Only DRAFT receipts can be deleted
    if (receipt.getStatus() != ReceiptStatus.DRAFT) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only DRAFT receipts can be deleted");
    }

    // Delete allocations first
    allocationRepository.deleteByReceiptId(receiptId);

    // Delete receipt
    receiptRepository.delete(receipt);

    // Log audit event
    logAuditEvent(receipt, "DELETE", receipt, null);
  }

  @Override
  public ARPaymentDTO allocateInvoices(
      UUID receiptId, List<ReceiptAllocationRequest> allocations) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    ARPayment receipt = receiptRepository
        .findByCompanyIdAndId(companyId, receiptId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Receipt not found: " + receiptId));

    // Only DRAFT or PENDING_APPROVAL receipts can be allocated
    if (receipt.getStatus() != ReceiptStatus.DRAFT
        && receipt.getStatus() != ReceiptStatus.PENDING_APPROVAL) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only DRAFT or PENDING_APPROVAL receipts can have allocations modified");
    }

    // Validate allocations
    var validationResult = receiptValidationService.validateAllocations(allocations, receipt.getAmount());
    if (!validationResult.isValid()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Allocation validation failed: " + validationResult.getFieldErrors());
    }

    // Delete existing allocations
    allocationRepository.deleteByReceiptId(receiptId);

    // Save new allocations
    saveAllocations(receiptId, allocations);

    receipt.setUpdatedAt(Instant.now());
    receipt = receiptRepository.save(receipt);

    return convertToDTO(receipt);
  }

  @Override
  public ARPaymentDTO updateAllocations(
      UUID receiptId, List<ReceiptAllocationRequest> allocations) {
    return allocateInvoices(receiptId, allocations);
  }

  @Override
  public ARPaymentDTO postReceipt(UUID receiptId) {
    // AC6.2-07: Performance telemetry - track post latency
    long startTime = System.currentTimeMillis();

    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Find receipt
    ARPayment receipt = receiptRepository
        .findByCompanyIdAndId(companyId, receiptId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Receipt not found: " + receiptId));

    // Validate receipt is in DRAFT or PENDING_APPROVAL status
    if (receipt.getStatus() != ReceiptStatus.DRAFT
        && receipt.getStatus() != ReceiptStatus.PENDING_APPROVAL) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Cannot post receipt: only DRAFT or PENDING_APPROVAL receipts can be posted. Current status: "
              + receipt.getStatus());
    }

    // AC6.2-09: If receipt is PENDING_APPROVAL, validate approver role and
    // maker-checker
    if (receipt.getStatus() == ReceiptStatus.PENDING_APPROVAL) {
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
            "Cannot post receipt: PENDING_APPROVAL receipts require Chief Accountant, CFO, or Admin role");
      }

      // Validate approver ≠ creator (maker-checker pattern)
      if (receipt.getCreatedById().equals(currentUserId)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Cannot post receipt: approver must be different from creator (maker-checker pattern)");
      }

      logger.info(
          "Receipt {} (PENDING_APPROVAL) approved by user {} (creator: {})",
          receipt.getReceiptNumber(),
          currentUserId,
          receipt.getCreatedById());
    }

    // Get allocations
    List<ReceiptAllocation> allocations = allocationRepository.findByReceiptIdOrderByAllocationOrderAsc(receiptId);

    // Validate and get bank account with GL account code
    Long bankAccountEntityId = receipt.getCashAccountId() != null
        ? receipt.getCashAccountId()
        : receipt.getBankAccountId();
    BankAccount bankAccount = bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountEntityId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Bank/Cash account not found: " + bankAccountEntityId));

    // Validate account is active (AC6.2-03)
    if (!Boolean.TRUE.equals(bankAccount.getActive())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot post receipt: selected account is inactive");
    }

    // Validate account has GL account code (AC6.2-03)
    if (bankAccount.getGlAccountCode() == null || bankAccount.getGlAccountCode().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Cannot post receipt: account has no GL account code configured");
    }

    // Look up COA account ID from GL account code (AC6.2-04)
    Long glDebitAccountId = chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, bankAccount.getGlAccountCode())
        .map(account -> account.getId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "GL account not found for code: " + bankAccount.getGlAccountCode()));

    // Create voucher for receipt posting
    // Dr Cash/Bank (via glAccountCode 1111/1121) - receipt amount
    // Cr AR 131 (Accounts Receivable) for allocated receipts OR Cr 711 (Other
    // Income) for standalone
    VoucherCreateRequest voucherRequest = new VoucherCreateRequest();
    voucherRequest.setDate(receipt.getReceiptDate());
    voucherRequest.setDescription(
        String.format(
            "Receipt %s from %s - %s",
            receipt.getReceiptNumber(),
            getCustomerName(receipt.getCustomerId()),
            receipt.getReference() != null ? receipt.getReference() : ""));

    List<VoucherEntryLineRequest> entryLines = new ArrayList<>();

    // For each allocation, create a voucher entry line:
    // Debit: Cash/Bank (via glAccountCode) - allocated amount per invoice
    // Credit: AR 131 (Accounts Receivable) - allocated amount per invoice
    for (ReceiptAllocation allocation : allocations) {
      SalesInvoice invoice = salesInvoiceRepository
          .findById(allocation.getSalesInvoiceId())
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND,
                  "Sales invoice not found: " + allocation.getSalesInvoiceId()));

      VoucherEntryLineRequest entryLine = new VoucherEntryLineRequest();
      entryLine.setDebitAccountId(glDebitAccountId); // Cash/Bank GL account (via glAccountCode)
      entryLine.setCreditAccountId(
          getAccountsReceivableAccountId(receipt.getCompanyId())); // AR account (131)
      entryLine.setAmount(allocation.getAllocatedAmount());
      entryLine.setDescription(
          String.format("Receipt allocation to invoice %s", invoice.getInvoiceNumber()));
      entryLine.setCustomerId(receipt.getCustomerId());
      entryLines.add(entryLine);
    }

    // If standalone receipt or partial allocation, create entry for unallocated
    // amount
    BigDecimal totalAllocated = allocations.stream()
        .map(ReceiptAllocation::getAllocatedAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (receipt.getIsStandalone() || totalAllocated.compareTo(receipt.getAmount()) < 0) {
      BigDecimal unallocatedAmount = receipt.getAmount().subtract(totalAllocated);
      if (unallocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
        VoucherEntryLineRequest entryLine = new VoucherEntryLineRequest();
        entryLine.setDebitAccountId(glDebitAccountId); // Cash/Bank GL account (via glAccountCode)

        // AC6.2-04: Standalone receipts credit Other Income (711), allocated receipts
        // credit AR (131)
        if (receipt.getIsStandalone()) {
          entryLine.setCreditAccountId(getOtherIncomeAccountId(receipt.getCompanyId())); // Other Income (711)
          entryLine.setDescription("Standalone receipt - Other income");
        } else {
          entryLine.setCreditAccountId(getAccountsReceivableAccountId(receipt.getCompanyId())); // AR (131)
          entryLine.setDescription("Receipt - Unallocated/Advance payment");
        }

        entryLine.setAmount(unallocatedAmount);
        entryLine.setCustomerId(receipt.getCustomerId());
        entryLines.add(entryLine);
      }
    }

    voucherRequest.setEntryLines(entryLines);

    // Create and post voucher
    var voucherDTO = voucherService.create(voucherRequest);
    voucherPostingService.postVoucher(voucherDTO.getId(), null);

    // Update receipt status and link voucher
    receipt.setStatus(ReceiptStatus.POSTED);
    receipt.setLinkedVoucherId(voucherDTO.getId());
    receipt.setPostedAt(Instant.now());
    receipt.setPostedById(currentUserId);
    receipt = receiptRepository.save(receipt);

    // Update invoice statuses and remaining balances (AC6.2-02/AC6.2-04)
    for (ReceiptAllocation allocation : allocations) {
      SalesInvoice invoice = salesInvoiceRepository
          .findById(allocation.getSalesInvoiceId())
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND,
                  "Sales invoice not found: " + allocation.getSalesInvoiceId()));

      // Calculate new remaining balance after this allocation
      BigDecimal newRemainingBalance = calculateRemainingBalance(invoice.getId());

      // Update invoice balance fields
      BigDecimal newAmountPaid = invoice.getTotalAmount().subtract(newRemainingBalance);
      invoice.setAmountPaid(newAmountPaid);
      invoice.setRemainingBalance(newRemainingBalance);

      // Update status based on remaining balance
      if (newRemainingBalance.compareTo(BigDecimal.ZERO) <= 0) {
        invoice.setStatus(SalesInvoiceStatus.PAID);
        invoice.setRemainingBalance(BigDecimal.ZERO); // Ensure no negative balance
      } else {
        invoice.setStatus(SalesInvoiceStatus.PARTIALLY_PAID);
      }

      salesInvoiceRepository.save(invoice);
    }

    // Invalidate AR aging cache when receipt is posted
    if (arAgingService != null) {
      try {
        arAgingService.invalidateAgingCache();
      } catch (Exception e) {
        logger.warn("Failed to invalidate AR aging cache after receipt post: {}", e.getMessage());
      }
    }

    // Log audit event
    logAuditEvent(receipt, "POST", null, receipt);

    // AC6.2-07: Performance telemetry - log post latency
    long elapsedMs = System.currentTimeMillis() - startTime;
    logger.info(
        "Receipt post completed: receiptId={}, receiptNumber={}, amount={}, latencyMs={}, companyId={}",
        receiptId, receipt.getReceiptNumber(), receipt.getAmount(), elapsedMs, companyId);

    // Warn if latency exceeds 10 second target (NFR)
    if (elapsedMs > 10000) {
      logger.warn(
          "Receipt post latency exceeded 10s target: receiptId={}, latencyMs={}",
          receiptId, elapsedMs);
    }

    return convertToDTO(receipt);
  }

  @Override
  public ARPaymentDTO reverseReceipt(UUID receiptId, String reason) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Long currentUserId = SecurityUtils.getCurrentUserId();

    // Find original receipt
    ARPayment originalReceipt = receiptRepository
        .findByCompanyIdAndId(companyId, receiptId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Receipt not found: " + receiptId));

    // Validate receipt is POSTED
    if (originalReceipt.getStatus() != ReceiptStatus.POSTED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only POSTED receipts can be reversed");
    }

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reversal reason is required");
    }

    if (reason.length() < 10) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Reversal reason must be at least 10 characters");
    }

    // Get original allocations
    List<ReceiptAllocation> originalAllocations = allocationRepository
        .findByReceiptIdOrderByAllocationOrderAsc(receiptId);

    // Create reversal receipt (negative amounts)
    ARPayment reversalReceipt = new ARPayment();
    reversalReceipt.setCompanyId(companyId);
    reversalReceipt.setCustomerId(originalReceipt.getCustomerId());
    reversalReceipt.setReceiptDate(LocalDate.now());
    reversalReceipt.setReceiptNumber(
        generateReceiptNumber(reversalReceipt.getReceiptDate()) + "-REV");
    reversalReceipt.setCashAccountId(originalReceipt.getCashAccountId());
    reversalReceipt.setBankAccountId(originalReceipt.getBankAccountId());
    reversalReceipt.setPayee(originalReceipt.getPayee());
    reversalReceipt.setAmount(originalReceipt.getAmount().negate());
    reversalReceipt.setReference("Reversal of " + originalReceipt.getReceiptNumber());
    reversalReceipt.setPaymentMethod(originalReceipt.getPaymentMethod());
    reversalReceipt.setIsStandalone(false);
    reversalReceipt.setStatus(ReceiptStatus.POSTED);
    reversalReceipt.setReversalReason(reason);
    reversalReceipt.setOriginalReceiptId(originalReceipt.getId());
    reversalReceipt.setCreatedById(currentUserId);
    reversalReceipt.setPostedById(currentUserId);
    reversalReceipt.setCreatedAt(Instant.now());
    reversalReceipt.setUpdatedAt(Instant.now());
    reversalReceipt.setPostedAt(Instant.now());

    reversalReceipt = receiptRepository.save(reversalReceipt);

    // Create reversal voucher
    VoucherCreateRequest voucherRequest = new VoucherCreateRequest();
    voucherRequest.setDate(reversalReceipt.getReceiptDate());
    voucherRequest.setDescription(
        String.format(
            "Reversal of receipt %s - %s", originalReceipt.getReceiptNumber(), reason));

    List<VoucherEntryLineRequest> entryLines = new ArrayList<>();

    // Get GL account ID from bank account's glAccountCode (AC6.2-04)
    Long bankAccountEntityId = originalReceipt.getCashAccountId() != null
        ? originalReceipt.getCashAccountId()
        : originalReceipt.getBankAccountId();
    Long glCreditAccountId = getGlAccountIdFromBankAccount(companyId, bankAccountEntityId);

    // Create reversal voucher entries (opposite of original)
    // Dr AR 131 (or Other Income 711 for standalone), Cr Cash/Bank (via
    // glAccountCode)
    for (ReceiptAllocation originalAllocation : originalAllocations) {
      VoucherEntryLineRequest entryLine = new VoucherEntryLineRequest();
      entryLine.setDebitAccountId(getAccountsReceivableAccountId(companyId)); // AR account (131)
      entryLine.setCreditAccountId(glCreditAccountId); // Cash/Bank GL account (via glAccountCode)
      entryLine.setAmount(originalAllocation.getAllocatedAmount());
      entryLine.setDescription("Reversal - " + reason);
      entryLine.setCustomerId(originalReceipt.getCustomerId());
      entryLines.add(entryLine);

      // Create reversal allocation
      ReceiptAllocation reversalAllocation = new ReceiptAllocation();
      reversalAllocation.setCompanyId(companyId);
      reversalAllocation.setReceiptId(reversalReceipt.getId());
      reversalAllocation.setSalesInvoiceId(originalAllocation.getSalesInvoiceId());
      reversalAllocation.setAllocatedAmount(originalAllocation.getAllocatedAmount().negate());
      reversalAllocation.setAllocationOrder(originalAllocation.getAllocationOrder());
      reversalAllocation.setCreatedAt(Instant.now());
      allocationRepository.save(reversalAllocation);

      // Update invoice status and balance fields after reversal (AC6.2-05)
      SalesInvoice invoice = salesInvoiceRepository
          .findById(originalAllocation.getSalesInvoiceId())
          .orElseThrow(
              () -> new ResponseStatusException(
                  HttpStatus.NOT_FOUND,
                  "Sales invoice not found: " + originalAllocation.getSalesInvoiceId()));

      // Recalculate remaining balance after reversal allocation
      BigDecimal newRemainingBalance = calculateRemainingBalance(invoice.getId());

      // Update invoice balance fields
      BigDecimal newAmountPaid = invoice.getTotalAmount().subtract(newRemainingBalance);
      invoice.setAmountPaid(newAmountPaid.max(BigDecimal.ZERO)); // Ensure no negative
      invoice.setRemainingBalance(newRemainingBalance.min(invoice.getTotalAmount())); // Cap at total

      // Update status based on remaining balance
      if (newRemainingBalance.compareTo(BigDecimal.ZERO) > 0
          && newRemainingBalance.compareTo(invoice.getTotalAmount()) < 0) {
        invoice.setStatus(SalesInvoiceStatus.PARTIALLY_PAID);
      } else if (newRemainingBalance.compareTo(invoice.getTotalAmount()) >= 0) {
        invoice.setStatus(SalesInvoiceStatus.POSTED);
      }

      salesInvoiceRepository.save(invoice);
    }

    // Handle standalone receipt reversal or unallocated amount reversal
    // (AC6.2-04/AC6.2-05)
    BigDecimal totalAllocatedOriginal = originalAllocations.stream()
        .map(ReceiptAllocation::getAllocatedAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (originalReceipt.getIsStandalone() || totalAllocatedOriginal.compareTo(originalReceipt.getAmount()) < 0) {
      BigDecimal unallocatedAmount = originalReceipt.getAmount().subtract(totalAllocatedOriginal);
      if (unallocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
        VoucherEntryLineRequest entryLine = new VoucherEntryLineRequest();
        entryLine.setCreditAccountId(glCreditAccountId); // Cash/Bank GL account (via glAccountCode)

        // Reversal: Debit the account that was originally credited
        if (originalReceipt.getIsStandalone()) {
          entryLine.setDebitAccountId(getOtherIncomeAccountId(companyId)); // Other Income (711)
          entryLine.setDescription("Reversal - Standalone receipt - " + reason);
        } else {
          entryLine.setDebitAccountId(getAccountsReceivableAccountId(companyId)); // AR (131)
          entryLine.setDescription("Reversal - Unallocated/Advance payment - " + reason);
        }

        entryLine.setAmount(unallocatedAmount);
        entryLine.setCustomerId(originalReceipt.getCustomerId());
        entryLines.add(entryLine);
      }
    }

    voucherRequest.setEntryLines(entryLines);

    // Create and post reversal voucher
    var voucherDTO = voucherService.create(voucherRequest);
    voucherPostingService.postVoucher(voucherDTO.getId(), null);

    // Link reversal voucher to reversal receipt
    reversalReceipt.setLinkedVoucherId(voucherDTO.getId());
    reversalReceipt = receiptRepository.save(reversalReceipt);

    // Update original receipt status to REVERSED and link to reversal
    originalReceipt.setStatus(ReceiptStatus.REVERSED);
    originalReceipt.setReversingReceiptId(reversalReceipt.getId());
    originalReceipt.setUpdatedAt(Instant.now());
    receiptRepository.save(originalReceipt);

    // Note: AR aging cache invalidation would happen here if service is available

    // Log audit event
    logAuditEvent(originalReceipt, "REVERSE", originalReceipt, originalReceipt);

    return convertToDTO(originalReceipt);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> getOpenInvoicesForCustomer(Long customerId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Get open invoices efficiently using JPQL
    List<SalesInvoice> openInvoices = salesInvoiceRepository.findOpenInvoicesByCustomerId(companyId, customerId);

    // Convert to map format for API response
    return openInvoices.stream()
        .map(
            invoice -> {
              Map<String, Object> map = new HashMap<>();
              map.put("id", invoice.getId());
              map.put("invoiceNumber", invoice.getInvoiceNumber());
              map.put("invoiceDate", invoice.getInvoiceDate().toString());
              map.put("dueDate", invoice.getDueDate() != null ? invoice.getDueDate().toString() : null);
              map.put("totalAmount", invoice.getTotalAmount());
              map.put("remainingBalance", invoice.getRemainingBalance());
              map.put("status", invoice.getStatus().name());
              return map;
            })
        .collect(Collectors.toList());
  }

  @Override
  public String generateReceiptNumber(LocalDate receiptDate) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    int year = receiptDate.getYear();
    String yearStr = String.valueOf(year);

    // Find the highest existing receipt number for this company and year
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<String> query = cb.createQuery(String.class);
    Root<ARPayment> root = query.from(ARPayment.class);

    query
        .select(root.get("receiptNumber"))
        .where(
            cb.and(
                cb.equal(root.get("companyId"), companyId),
                cb.like(root.get("receiptNumber"), RECEIPT_NUMBER_PREFIX + yearStr + "%")))
        .orderBy(cb.desc(root.get("receiptNumber")));

    List<String> existingNumbers = entityManager.createQuery(query).setMaxResults(1).getResultList();

    int nextSequence = 1;
    if (!existingNumbers.isEmpty()) {
      String lastNumber = existingNumbers.get(0);
      // Extract sequence from format: RCP-YYYY-XXXXX
      String sequencePart = lastNumber.substring(lastNumber.lastIndexOf('-') + 1);
      try {
        nextSequence = Integer.parseInt(sequencePart) + 1;
      } catch (NumberFormatException e) {
        logger.warn("Failed to parse sequence from receipt number: {}", lastNumber);
        nextSequence = 1;
      }
    }

    return String.format("%s%s-%05d", RECEIPT_NUMBER_PREFIX, yearStr, nextSequence);
  }

  // Private helper methods

  private void saveAllocations(UUID receiptId, List<ReceiptAllocationRequest> allocationRequests) {
    Long companyId = CompanyContext.getCompanyId();

    for (int i = 0; i < allocationRequests.size(); i++) {
      ReceiptAllocationRequest request = allocationRequests.get(i);
      ReceiptAllocation allocation = new ReceiptAllocation();
      allocation.setCompanyId(companyId);
      allocation.setReceiptId(receiptId);
      allocation.setSalesInvoiceId(request.getSalesInvoiceId());
      allocation.setAllocatedAmount(request.getAllocatedAmount());
      allocation.setAllocationOrder(i + 1);
      allocation.setCreatedAt(Instant.now());
      allocationRepository.save(allocation);
    }
  }

  private BigDecimal calculateRemainingBalance(UUID invoiceId) {
    SalesInvoice invoice = salesInvoiceRepository
        .findById(invoiceId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Sales invoice not found: " + invoiceId));

    BigDecimal totalAllocated = allocationRepository.sumAllocatedAmountBySalesInvoiceIdAndPostedReceipts(invoiceId);

    return invoice.getTotalAmount().subtract(totalAllocated != null ? totalAllocated : BigDecimal.ZERO);
  }

  private Long getAccountsReceivableAccountId(Long companyId) {
    return chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, "131")
        .map(account -> account.getId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Accounts Receivable account (131) not found"));
  }

  /**
   * Get Other Income GL account ID (711) for standalone receipts.
   * AC6.2-04: Standalone receipts credit Other Income instead of AR.
   */
  private Long getOtherIncomeAccountId(Long companyId) {
    return chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, "711")
        .map(account -> account.getId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Other Income account (711) not found"));
  }

  /**
   * Look up GL account ID from bank account's glAccountCode.
   * Used for voucher entries to ensure correct GL posting.
   */
  private Long getGlAccountIdFromBankAccount(Long companyId, Long bankAccountEntityId) {
    BankAccount bankAccount = bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountEntityId)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Bank/Cash account not found: " + bankAccountEntityId));

    if (bankAccount.getGlAccountCode() == null || bankAccount.getGlAccountCode().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Bank account has no GL account code configured");
    }

    return chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, bankAccount.getGlAccountCode())
        .map(account -> account.getId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "GL account not found for code: " + bankAccount.getGlAccountCode()));
  }

  private String getCustomerName(Long customerId) {
    return customerRepository
        .findById(customerId)
        .map(Customer::getName)
        .orElse("Unknown Customer");
  }

  private ARPaymentListDTO convertToListDTO(ARPayment receipt) {
    ARPaymentListDTO dto = new ARPaymentListDTO();
    dto.setId(receipt.getId());
    dto.setReceiptNumber(receipt.getReceiptNumber());
    dto.setReceiptDate(receipt.getReceiptDate());
    dto.setCustomerId(receipt.getCustomerId());
    dto.setCustomerName(getCustomerName(receipt.getCustomerId()));
    dto.setCustomerCode(
        customerRepository.findById(receipt.getCustomerId()).map(Customer::getCode).orElse(null));
    dto.setAmount(receipt.getAmount());
    dto.setPaymentMethod(receipt.getPaymentMethod());
    dto.setStatus(receipt.getStatus());
    dto.setIsStandalone(receipt.getIsStandalone());

    // Get account names
    if (receipt.getCashAccountId() != null) {
      dto.setCashAccountName(
          bankAccountRepository
              .findById(receipt.getCashAccountId())
              .map(BankAccount::getAccountNumber)
              .orElse(null));
    }
    if (receipt.getBankAccountId() != null) {
      dto.setBankAccountName(
          bankAccountRepository
              .findById(receipt.getBankAccountId())
              .map(BankAccount::getAccountNumber)
              .orElse(null));
    }

    // Count allocations
    dto.setAllocationCount(
        allocationRepository.findByReceiptIdOrderByAllocationOrderAsc(receipt.getId()).size());

    // Note: ARPaymentListDTO does not have createdAt/postedAt fields

    return dto;
  }

  private ARPaymentDTO convertToDTO(ARPayment receipt) {
    ARPaymentDTO dto = new ARPaymentDTO();
    dto.setId(receipt.getId());
    dto.setCompanyId(receipt.getCompanyId());
    dto.setCustomerId(receipt.getCustomerId());
    dto.setCustomerName(getCustomerName(receipt.getCustomerId()));
    dto.setCustomerCode(
        customerRepository.findById(receipt.getCustomerId()).map(Customer::getCode).orElse(null));
    dto.setReceiptNumber(receipt.getReceiptNumber());
    dto.setReceiptDate(receipt.getReceiptDate());
    dto.setCashAccountId(receipt.getCashAccountId());
    dto.setBankAccountId(receipt.getBankAccountId());

    // Get account details
    if (receipt.getCashAccountId() != null) {
      dto.setCashAccountNumber(
          bankAccountRepository
              .findById(receipt.getCashAccountId())
              .map(BankAccount::getAccountNumber)
              .orElse(null));
    }
    if (receipt.getBankAccountId() != null) {
      dto.setBankAccountNumber(
          bankAccountRepository
              .findById(receipt.getBankAccountId())
              .map(BankAccount::getAccountNumber)
              .orElse(null));
    }

    dto.setPayee(receipt.getPayee());
    dto.setAmount(receipt.getAmount());
    dto.setReference(receipt.getReference());
    dto.setPaymentMethod(receipt.getPaymentMethod());
    dto.setReceiptProofUrl(receipt.getReceiptProofUrl());
    dto.setStatus(receipt.getStatus());
    dto.setIsStandalone(receipt.getIsStandalone());
    dto.setCreatedById(receipt.getCreatedById());
    dto.setCreatedByName(
        userRepository.findById(receipt.getCreatedById()).map(User::getFullName).orElse(null));
    dto.setPostedById(receipt.getPostedById());
    dto.setPostedByName(
        receipt.getPostedById() != null
            ? userRepository.findById(receipt.getPostedById()).map(User::getFullName).orElse(null)
            : null);
    dto.setLinkedVoucherId(receipt.getLinkedVoucherId());
    dto.setReversalReason(receipt.getReversalReason());
    dto.setOriginalReceiptId(receipt.getOriginalReceiptId());
    dto.setOriginalReceiptNumber(
        receipt.getOriginalReceiptId() != null
            ? receiptRepository
                .findById(receipt.getOriginalReceiptId())
                .map(ARPayment::getReceiptNumber)
                .orElse(null)
            : null);
    dto.setReversingReceiptId(receipt.getReversingReceiptId());
    dto.setReversingReceiptNumber(
        receipt.getReversingReceiptId() != null
            ? receiptRepository
                .findById(receipt.getReversingReceiptId())
                .map(ARPayment::getReceiptNumber)
                .orElse(null)
            : null);
    dto.setCreatedAt(receipt.getCreatedAt());
    dto.setUpdatedAt(receipt.getUpdatedAt());
    dto.setPostedAt(receipt.getPostedAt());

    // Get allocations
    List<ReceiptAllocation> allocations = allocationRepository
        .findByReceiptIdOrderByAllocationOrderAsc(receipt.getId());
    dto.setAllocations(
        allocations.stream().map(this::convertAllocationToDTO).collect(Collectors.toList()));

    return dto;
  }

  private ReceiptAllocationDTO convertAllocationToDTO(ReceiptAllocation allocation) {
    ReceiptAllocationDTO dto = new ReceiptAllocationDTO();
    dto.setId(allocation.getId());
    dto.setSalesInvoiceId(allocation.getSalesInvoiceId());

    // Get invoice details
    salesInvoiceRepository
        .findById(allocation.getSalesInvoiceId())
        .ifPresent(
            invoice -> {
              dto.setSalesInvoiceNumber(invoice.getInvoiceNumber());
              dto.setSalesInvoiceDate(invoice.getInvoiceDate());
              dto.setSalesInvoiceDueDate(invoice.getDueDate());
              dto.setSalesInvoiceTotalAmount(invoice.getTotalAmount());
              dto.setSalesInvoiceRemainingBalance(calculateRemainingBalance(invoice.getId()));
            });

    dto.setAllocatedAmount(allocation.getAllocatedAmount());
    dto.setAllocationOrder(allocation.getAllocationOrder());

    return dto;
  }

  private void logAuditEvent(
      ARPayment receipt, String action, ARPayment beforeState, ARPayment afterState) {
    try {
      JsonNode beforeSnapshot = beforeState != null ? serializeReceiptToJson(beforeState) : null;
      JsonNode afterSnapshot = afterState != null ? serializeReceiptToJson(afterState) : null;

      auditService.logPaymentEvent(
          receipt.getId(),
          receipt.getReceiptNumber(),
          "RECEIPT_" + action,
          beforeSnapshot,
          afterSnapshot,
          null,
          null);
    } catch (Exception e) {
      logger.error("Failed to log audit event for receipt {}: {}", receipt.getId(), e.getMessage());
    }
  }

  private JsonNode serializeReceiptToJson(ARPayment receipt) {
    try {
      return objectMapper.valueToTree(convertToDTO(receipt));
    } catch (Exception e) {
      logger.error("Failed to serialize receipt to JSON: {}", e.getMessage());
      return objectMapper.createObjectNode();
    }
  }

  /**
   * Get receipt approval threshold from company settings.
   * Uses salesInvoiceApprovalThresholdAmount as receipts are AR operations.
   * AC6.2-09: Receipts above this threshold require maker-checker approval.
   *
   * @return approval threshold amount, defaults to 100,000,000 VND
   */
  private BigDecimal getReceiptApprovalThreshold() {
    try {
      var settings = companySettingsService.getCurrentCompanySettings();
      if (settings.getSalesInvoiceApprovalThresholdAmount() != null) {
        return settings.getSalesInvoiceApprovalThresholdAmount();
      }
    } catch (Exception e) {
      logger.warn("Failed to get receipt approval threshold from company settings: {}", e.getMessage());
    }
    // Default threshold: 100,000,000 VND (same as sales invoice threshold)
    return new BigDecimal("100000000.00");
  }
}

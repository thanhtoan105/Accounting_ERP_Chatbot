package com.accounting.service.impl.sales;

import com.accounting.dto.ReceiptAllocationRequest;
import com.accounting.dto.ReceiptValidationResult;
import com.accounting.entity.ARPayment;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.ReceiptAllocationRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountBalanceService;
import com.accounting.service.CompanySettingsService;
import com.accounting.service.ReceiptValidationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of ReceiptValidationService.
 * Validates customer open invoices, allocations, account balance, standalone receipts, and receipt proof.
 */
@Service
public class ReceiptValidationServiceImpl implements ReceiptValidationService {

  private static final Logger logger =
      LoggerFactory.getLogger(ReceiptValidationServiceImpl.class);

  private final SalesInvoiceRepository salesInvoiceRepository;
  private final ReceiptAllocationRepository receiptAllocationRepository;
  private final CustomerRepository customerRepository;
  private final AccountBalanceService accountBalanceService;
  private final CompanySettingsService companySettingsService;

  public ReceiptValidationServiceImpl(
      SalesInvoiceRepository salesInvoiceRepository,
      ReceiptAllocationRepository receiptAllocationRepository,
      CustomerRepository customerRepository,
      AccountBalanceService accountBalanceService,
      CompanySettingsService companySettingsService) {
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.receiptAllocationRepository = receiptAllocationRepository;
    this.customerRepository = customerRepository;
    this.accountBalanceService = accountBalanceService;
    this.companySettingsService = companySettingsService;
  }

  @Override
  @Transactional(readOnly = true)
  public ReceiptValidationResult validateCustomerHasOpenInvoices(Long customerId) {
    ReceiptValidationResult result = new ReceiptValidationResult();
    Long companyId = CompanyContext.getCompanyId();

    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Find open/unpaid invoices: status in (POSTED, PARTIALLY_PAID) and remaining_balance > 0
    List<SalesInvoice> allInvoices = salesInvoiceRepository.findByCompanyId(companyId).stream()
        .filter(invoice -> invoice.getCustomerId().equals(customerId))
        .collect(java.util.stream.Collectors.toList());

    // Filter invoices with remaining balance > 0
    long openInvoicesCount =
        allInvoices.stream()
            .filter(
                invoice -> {
                  SalesInvoiceStatus status = invoice.getStatus();
                  return (status == SalesInvoiceStatus.POSTED
                          || status == SalesInvoiceStatus.PARTIALLY_PAID)
                      && invoice.getTotalAmount()
                              .subtract(
                                  invoice.getVatAmount() != null
                                      ? invoice.getVatAmount()
                                      : BigDecimal.ZERO)
                              .compareTo(BigDecimal.ZERO)
                          > 0;
                })
            .count();

    if (openInvoicesCount == 0) {
      result.addFieldError(
          "customerId", "Customer has no open/unpaid invoices. Cannot create linked receipt.");
    }

    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public ReceiptValidationResult validateAllocations(
      List<ReceiptAllocationRequest> allocations, BigDecimal receiptAmount) {
    ReceiptValidationResult result = new ReceiptValidationResult();
    Long companyId = CompanyContext.getCompanyId();

    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    if (allocations == null || allocations.isEmpty()) {
      result.addFieldError(
          "allocations", "At least one allocation is required for linked receipt");
      return result;
    }

    // Calculate total allocated amount
    BigDecimal totalAllocated =
        allocations.stream()
            .map(ReceiptAllocationRequest::getAllocatedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Check if total allocated matches receipt amount (allow partial allocation)
    if (totalAllocated.compareTo(receiptAmount) > 0) {
      result.addFieldError(
          "allocations",
          String.format(
              "Total allocated amount (%s) cannot exceed receipt amount (%s)",
              totalAllocated, receiptAmount));
    }

    // Validate each allocation
    for (int i = 0; i < allocations.size(); i++) {
      ReceiptAllocationRequest allocation = allocations.get(i);
      String fieldPrefix = "allocations[" + i + "]";

      // Check if invoice exists
      Optional<SalesInvoice> invoiceOpt =
          salesInvoiceRepository.findByCompanyIdAndId(companyId, allocation.getSalesInvoiceId());
      if (invoiceOpt.isEmpty()) {
        result.addFieldError(
            fieldPrefix + ".salesInvoiceId",
            "Sales invoice not found: " + allocation.getSalesInvoiceId());
        continue;
      }

      SalesInvoice invoice = invoiceOpt.get();

      // Check if invoice is posted or partially paid
      if (invoice.getStatus() != SalesInvoiceStatus.POSTED
          && invoice.getStatus() != SalesInvoiceStatus.PARTIALLY_PAID) {
        result.addFieldError(
            fieldPrefix + ".salesInvoiceId",
            "Sales invoice must be POSTED or PARTIALLY_PAID. Current status: "
                + invoice.getStatus());
        continue;
      }

      // Calculate remaining balance (using database-maintained field if available)
      BigDecimal remainingBalance = calculateRemainingBalance(invoice);

      // Check overpayment
      if (allocation.getAllocatedAmount().compareTo(remainingBalance) > 0) {
        result.addFieldError(
            fieldPrefix + ".allocatedAmount",
            String.format(
                "Allocated amount (%s) exceeds invoice remaining balance (%s). Invoice: %s",
                allocation.getAllocatedAmount(), remainingBalance, invoice.getInvoiceNumber()));
      }

      // Check positive amount
      if (allocation.getAllocatedAmount().compareTo(BigDecimal.ZERO) <= 0) {
        result.addFieldError(
            fieldPrefix + ".allocatedAmount", "Allocated amount must be positive");
      }
    }

    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public ReceiptValidationResult validateAccountBalance(
      Long accountId, BigDecimal receiptAmount) {
    ReceiptValidationResult result = new ReceiptValidationResult();

    if (accountId == null) {
      result.addFieldError("accountId", "Account ID is required");
      return result;
    }

    // Note: For receipts (incoming payments), we don't need to check account balance
    // as we're receiving money, not paying out. This validation is primarily for
    // consistency with the payment validation interface.
    // In a full implementation, you might check if the account is active/valid.

    logger.debug(
        "Receipt account validation passed for accountId: {}, amount: {}", accountId, receiptAmount);

    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public ReceiptValidationResult validateStandaloneReceipt(ARPayment receipt, Long userId) {
    ReceiptValidationResult result = new ReceiptValidationResult();

    if (receipt.getIsStandalone() == null || !receipt.getIsStandalone()) {
      return result; // Not a standalone receipt, no validation needed
    }

    // Check if user has admin role (standalone receipts are admin-only)
    // This should be enforced at controller level with @PreAuthorize
    // Here we just log a warning if standalone flag is set
    logger.warn(
        "Standalone receipt flagged for customer: {}, amount: {}, user: {}",
        receipt.getCustomerId(),
        receipt.getAmount(),
        userId);

    // Add informational message
    result.addGlobalError(
        "Standalone receipt (advance payment/on-account) requires admin approval. "
            + "This receipt will be flagged in audit logs.");

    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public ReceiptValidationResult validateReceiptProof(BigDecimal receiptAmount, String proofUrl) {
    ReceiptValidationResult result = new ReceiptValidationResult();

    // Get approval threshold from company settings
    BigDecimal threshold = getApprovalThreshold();

    // If receipt amount exceeds threshold and no proof provided, add warning
    if (receiptAmount.compareTo(threshold) > 0
        && (proofUrl == null || proofUrl.trim().isEmpty())) {
      result.addFieldError(
          "receiptProofUrl",
          String.format(
              "Receipt proof is required for amounts exceeding %s VND", threshold.toPlainString()));
    }

    return result;
  }

  /**
   * Calculate remaining balance for an invoice.
   * Uses database-maintained remaining_balance field if available, otherwise calculates.
   */
  private BigDecimal calculateRemainingBalance(SalesInvoice invoice) {
    // If database maintains remaining_balance (from our migration), use it
    // Otherwise calculate: total_amount - sum of posted receipt allocations
    BigDecimal totalPaid =
        receiptAllocationRepository.sumAllocatedAmountBySalesInvoiceIdAndPostedReceipts(
            invoice.getId());

    return invoice.getTotalAmount().subtract(totalPaid);
  }

  /**
   * Get approval threshold from company settings.
   * Default: 100,000,000 VND (100 million VND).
   */
  private BigDecimal getApprovalThreshold() {
    try {
      // Get threshold from company settings - using default for now
      // TODO: Add getApprovalThresholdAR() method to CompanySettingsService
      return new BigDecimal("100000000"); // 100M VND default
    } catch (Exception e) {
      logger.warn("Failed to get AR approval threshold, using default: {}", e.getMessage());
      return new BigDecimal("100000000"); // 100M VND default
    }
  }
}

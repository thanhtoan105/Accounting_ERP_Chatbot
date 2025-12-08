package com.accounting.service.impl.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.PaymentAllocationRequest;
import com.accounting.dto.PaymentValidationResult;
import com.accounting.entity.APPayment;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.repository.PaymentAllocationRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountBalanceService;
import com.accounting.service.CompanySettingsService;
import com.accounting.service.PaymentValidationService;

/**
 * Implementation of PaymentValidationService.
 * Validates supplier open bills, allocations, account balance, standalone payments, and payment proof.
 */
@Service
public class PaymentValidationServiceImpl implements PaymentValidationService {

  private static final Logger logger =
      LoggerFactory.getLogger(PaymentValidationServiceImpl.class);

  private final PurchaseBillRepository purchaseBillRepository;
  private final PaymentAllocationRepository paymentAllocationRepository;
  private final SupplierRepository supplierRepository;
  private final AccountBalanceService accountBalanceService;
  private final CompanySettingsService companySettingsService;

  public PaymentValidationServiceImpl(
      PurchaseBillRepository purchaseBillRepository,
      PaymentAllocationRepository paymentAllocationRepository,
      SupplierRepository supplierRepository,
      AccountBalanceService accountBalanceService,
      CompanySettingsService companySettingsService) {
    this.purchaseBillRepository = purchaseBillRepository;
    this.paymentAllocationRepository = paymentAllocationRepository;
    this.supplierRepository = supplierRepository;
    this.accountBalanceService = accountBalanceService;
    this.companySettingsService = companySettingsService;
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentValidationResult validateSupplierHasOpenBills(Long supplierId) {
    PaymentValidationResult result = new PaymentValidationResult();
    Long companyId = CompanyContext.getCompanyId();

    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Find open/unpaid bills: status=POSTED and remaining_balance > 0
    List<PurchaseBill> openBills =
        purchaseBillRepository.findByCompanyIdAndStatus(companyId, PurchaseBillStatus.POSTED);

    // Filter bills with remaining balance > 0
    long openBillsCount =
        openBills.stream()
            .filter(
                bill -> {
                  BigDecimal remainingBalance = calculateRemainingBalance(bill.getId());
                  return remainingBalance.compareTo(BigDecimal.ZERO) > 0;
                })
            .count();

    if (openBillsCount == 0) {
      result.addFieldError(
          "supplierId", "Supplier has no open/unpaid bills. Cannot create linked payment.");
    }

    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentValidationResult validateAllocations(
      List<PaymentAllocationRequest> allocations, BigDecimal paymentAmount) {
    PaymentValidationResult result = new PaymentValidationResult();
    Long companyId = CompanyContext.getCompanyId();

    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    if (allocations == null || allocations.isEmpty()) {
      result.addFieldError("allocations", "At least one allocation is required for linked payment");
      return result;
    }

    // Calculate total allocated amount
    BigDecimal totalAllocated =
        allocations.stream()
            .map(PaymentAllocationRequest::getAllocatedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Check if total allocated matches payment amount
    if (totalAllocated.compareTo(paymentAmount) != 0) {
      result.addFieldError(
          "allocations",
          String.format(
              "Total allocated amount (%s) must equal payment amount (%s)",
              totalAllocated, paymentAmount));
    }

    // Validate each allocation
    for (int i = 0; i < allocations.size(); i++) {
      PaymentAllocationRequest allocation = allocations.get(i);
      String fieldPrefix = "allocations[" + i + "]";

      // Check if bill exists
      Optional<PurchaseBill> billOpt =
          purchaseBillRepository.findByCompanyIdAndId(companyId, allocation.getPurchaseBillId());
      if (billOpt.isEmpty()) {
        result.addFieldError(
            fieldPrefix + ".purchaseBillId", "Purchase bill not found: " + allocation.getPurchaseBillId());
        continue;
      }

      PurchaseBill bill = billOpt.get();

      // Check if bill is posted
      if (bill.getStatus() != PurchaseBillStatus.POSTED) {
        result.addFieldError(
            fieldPrefix + ".purchaseBillId",
            "Purchase bill must be POSTED. Current status: " + bill.getStatus());
        continue;
      }

      // Calculate remaining balance
      BigDecimal remainingBalance = calculateRemainingBalance(bill.getId());

      // Check overpayment
      if (allocation.getAllocatedAmount().compareTo(remainingBalance) > 0) {
        result.addFieldError(
            fieldPrefix + ".allocatedAmount",
            String.format(
                "Allocated amount (%s) exceeds bill remaining balance (%s). Bill: %s",
                allocation.getAllocatedAmount(), remainingBalance, bill.getBillNumber()));
      }
    }

    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentValidationResult validateAccountBalance(Long accountId, BigDecimal paymentAmount) {
    PaymentValidationResult result = new PaymentValidationResult();

    if (accountId == null) {
      result.addFieldError("accountId", "Account ID is required");
      return result;
    }

    if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
      result.addFieldError("amount", "Payment amount must be positive");
      return result;
    }

    // Check sufficient balance
    boolean sufficient = accountBalanceService.validateSufficientBalance(accountId, paymentAmount);
    if (!sufficient) {
      AccountBalanceService.OverdraftResult overdraftResult =
          accountBalanceService.checkOverdraft(accountId, paymentAmount);

      if (overdraftResult.getStatus() == AccountBalanceService.OverdraftStatus.BLOCK) {
        result.addFieldError("amount", overdraftResult.getMessage());
      } else if (overdraftResult.getStatus() == AccountBalanceService.OverdraftStatus.WARNING) {
        result.addWarning("amount", overdraftResult.getMessage());
      }
    }

    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentValidationResult validateStandalonePayment(APPayment payment, Long userId) {
    PaymentValidationResult result = new PaymentValidationResult();

    if (payment.getIsStandalone() == null || !payment.getIsStandalone()) {
      return result; // Not a standalone payment, no validation needed
    }

    // Check if user has admin role
    boolean isAdmin =
        SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

    if (!isAdmin) {
      result.addFieldError(
          "isStandalone", "Standalone payments are only allowed for administrators");
    } else {
      result.addWarning(
          "isStandalone",
          "Standalone payment: This payment is not linked to any purchase bill. Ensure proper accounting treatment.");
    }

    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentValidationResult validatePaymentProof(BigDecimal paymentAmount, String proofUrl) {
    PaymentValidationResult result = new PaymentValidationResult();

    // Get approval threshold from company settings
    BigDecimal threshold = getApprovalThreshold();

    // If payment amount exceeds threshold, proof is required
    if (paymentAmount != null && paymentAmount.compareTo(threshold) > 0) {
      if (proofUrl == null || proofUrl.isBlank()) {
        result.addFieldError(
            "paymentProofUrl",
            String.format(
                "Payment proof is required for payments exceeding threshold (%s)",
                threshold));
      }
    }

    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentValidationResult suggestQuickAddSupplier(String supplierName) {
    PaymentValidationResult result = new PaymentValidationResult();
    Long companyId = CompanyContext.getCompanyId();

    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Check if supplier exists in master data (search by name contains)
    boolean supplierExists =
        supplierRepository.findByCompanyId(companyId).stream()
            .anyMatch(s -> s.getName() != null && s.getName().equalsIgnoreCase(supplierName));

    if (!supplierExists) {
      result.addWarning(
          "supplierName",
          String.format(
              "Supplier '%s' not found in master data. Consider adding to master data for better tracking.",
              supplierName));
    }

    return result;
  }

  /**
   * Calculate remaining balance for a purchase bill.
   * remaining_balance = total_amount - sum of allocated payments
   */
  private BigDecimal calculateRemainingBalance(java.util.UUID billId) {
    PurchaseBill bill =
        purchaseBillRepository
            .findById(billId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Purchase bill not found: " + billId));

    BigDecimal totalAllocated = paymentAllocationRepository.calculateTotalAllocatedAmount(billId);
    return bill.getTotalAmount().subtract(totalAllocated);
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
}

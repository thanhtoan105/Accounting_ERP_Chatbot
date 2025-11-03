package com.accounting.service.impl.voucher;

import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherLineDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.VoucherValidationService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of VoucherValidationService.
 * Validates double-entry balance, leaf-only accounts, and required dimensions.
 */
@Service
public class VoucherValidationServiceImpl implements VoucherValidationService {

  private static final Logger logger =
      LoggerFactory.getLogger(VoucherValidationServiceImpl.class);

  private final ChartOfAccountsRepository chartOfAccountsRepository;

  public VoucherValidationServiceImpl(ChartOfAccountsRepository chartOfAccountsRepository) {
    this.chartOfAccountsRepository = chartOfAccountsRepository;
  }

  @Override
  public VoucherValidationResult validate(VoucherCreateRequest request) {
    VoucherValidationResult result = new VoucherValidationResult(true, new HashMap<>());
    Long companyId = CompanyContext.getCompanyId();

    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
      result.addError(0, "general", "At least one line item is required");
      return result;
    }

    List<VoucherLineDTO> lines = request.getLines();
    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;

    // Validate each line and accumulate totals
    for (int i = 0; i < lines.size(); i++) {
      VoucherLineDTO line = lines.get(i);
      int lineNumber = i + 1; // 1-based line numbers for user-facing errors

      // Validate line-level business rules
      validateLine(line, lineNumber, companyId, result);

      // Accumulate totals (only if line is valid for totals)
      if (!hasLineErrors(result, lineNumber)) {
        totalDebit = totalDebit.add(line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO);
        totalCredit =
            totalCredit.add(line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO);
      }
    }

    // Validate double-entry balance (Total Debit == Total Credit)
    if (totalDebit.compareTo(totalCredit) != 0) {
      result.addError(
          0,
          "balance",
          String.format(
              "Double-entry balance error: Total Debit (%s) must equal Total Credit (%s)",
              totalDebit, totalCredit));
    }

    return result;
  }

  /**
   * Validate a single line item.
   *
   * @param line line item to validate
   * @param lineNumber 1-based line number for error reporting
   * @param companyId company ID for account lookups
   * @param result validation result to add errors to
   */
  private void validateLine(
      VoucherLineDTO line, int lineNumber, Long companyId, VoucherValidationResult result) {

    // Validate account ID is provided
    if (line.getAccountId() == null) {
      result.addError(lineNumber, "accountId", "Account is required");
      return; // Can't validate further without account
    }

    // Validate account exists and is postable (leaf-only)
    ChartOfAccount account =
        chartOfAccountsRepository
            .findById(line.getAccountId())
            .orElse(null);

    if (account == null) {
      result.addError(lineNumber, "accountId", "Account not found");
      return;
    }

    // Validate company scoping
    if (!account.getCompanyId().equals(companyId)) {
      result.addError(
          lineNumber, "accountId", "Account does not belong to your company");
      return;
    }

    // Validate leaf-only (postable accounts only)
    if (Boolean.FALSE.equals(account.getPostable())) {
      result.addError(
          lineNumber,
          "accountId",
          "Only leaf (postable) accounts can be used. Account "
              + account.getCode()
              + " is not postable.");
    }

    // Validate debit/credit amounts
    BigDecimal debit = line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO;
    BigDecimal credit = line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO;

    if (debit.compareTo(BigDecimal.ZERO) < 0) {
      result.addError(lineNumber, "debit", "Debit amount must be non-negative");
    }

    if (credit.compareTo(BigDecimal.ZERO) < 0) {
      result.addError(lineNumber, "credit", "Credit amount must be non-negative");
    }

    // Validate mutual exclusivity: either debit OR credit must be 0
    if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0) {
      result.addError(
          lineNumber,
          "debit",
          "Debit and credit cannot both be greater than zero in the same line");
      result.addError(
          lineNumber,
          "credit",
          "Debit and credit cannot both be greater than zero in the same line");
    }

    // Validate at least one is > 0
    if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
      result.addError(
          lineNumber, "debit", "Either debit or credit must be greater than zero");
      result.addError(
          lineNumber, "credit", "Either debit or credit must be greater than zero");
    }

    // Validate required dimensions based on account code
    String accountCode = account.getCode();
    validateDimensions(line, lineNumber, accountCode, result);
  }

  /**
   * Validate required dimensions based on account type.
   *
   * @param line line item
   * @param lineNumber line number for error reporting
   * @param accountCode account code (e.g., "131", "331", "154", "621")
   * @param result validation result
   */
  private void validateDimensions(
      VoucherLineDTO line, int lineNumber, String accountCode, VoucherValidationResult result) {

    // Account 131 (Accounts Receivable) requires customer_id
    if ("131".equals(accountCode)) {
      if (line.getCustomerId() == null) {
        result.addError(
            lineNumber,
            "customerId",
            "Customer is required for Accounts Receivable (account 131)");
      }
    }

    // Account 331 (Accounts Payable) requires vendor_id
    if ("331".equals(accountCode)) {
      if (line.getVendorId() == null) {
        result.addError(
            lineNumber,
            "vendorId",
            "Vendor is required for Accounts Payable (account 331)");
      }
    }

    // Account 154 (Work in Progress) or 621 (Cost of Goods Sold) requires cost_center_id
    if ("154".equals(accountCode) || "621".equals(accountCode)) {
      if (line.getCostCenterId() == null) {
        result.addError(
            lineNumber,
            "costCenterId",
            "Cost Center is required for account " + accountCode);
      }
    }

    // Note: Additional dimension rules can be added here based on business requirements
  }

  /**
   * Check if a line has any errors in the validation result.
   *
   * @param result validation result
   * @param lineNumber line number to check
   * @return true if line has errors, false otherwise
   */
  private boolean hasLineErrors(VoucherValidationResult result, int lineNumber) {
    Map<Integer, Map<String, String>> errors = result.getErrors();
    if (errors == null) {
      return false;
    }
    Map<String, String> lineErrors = errors.get(lineNumber);
    return lineErrors != null && !lineErrors.isEmpty();
  }
}


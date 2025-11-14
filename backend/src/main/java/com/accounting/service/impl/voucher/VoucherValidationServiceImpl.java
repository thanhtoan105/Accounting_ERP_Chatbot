package com.accounting.service.impl.voucher;

import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherEntryLineRequest;
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
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of VoucherValidationService.
 * Validates double-entry balance, leaf-only accounts, and required dimensions.
 */
@Service
public class VoucherValidationServiceImpl implements VoucherValidationService {

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

    if (request == null) {
      result.addError(0, "general", "Request payload is required");
      return result;
    }

    List<VoucherEntryLineRequest> entryLines = request.getEntryLines();
    if (entryLines != null && !entryLines.isEmpty()) {
      validateEntryLines(entryLines, companyId, result);
      return result;
    }

    List<VoucherLineDTO> ledgerLines = request.getLines();
    if (ledgerLines != null && !ledgerLines.isEmpty()) {
      validateLedgerLines(ledgerLines, companyId, result);
      return result;
    }

    result.addError(0, "general", "At least one line item is required");
    return result;
  }

  /**
   * Validate a single line item.
   *
   * @param line       line item to validate
   * @param lineNumber 1-based line number for error reporting
   * @param companyId  company ID for account lookups
   * @param result     validation result to add errors to
   */
  private void validateEntryLines(
      List<VoucherEntryLineRequest> lines, Long companyId, VoucherValidationResult result) {

    BigDecimal totalAmount = BigDecimal.ZERO;
    Map<Long, ChartOfAccount> accountCache = new HashMap<>();

    for (int i = 0; i < lines.size(); i++) {
      VoucherEntryLineRequest line = lines.get(i);
      int lineNumber = i + 1;
      validateEntryLine(line, lineNumber, companyId, result, accountCache);

      if (!result.hasErrorsForLine(lineNumber)) {
        totalAmount = totalAmount.add(line.getAmount() != null ? line.getAmount() : BigDecimal.ZERO);
      }
    }

    if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
      result.addError(0, "amount", "Total amount must be greater than 0");
    }
  }

  private void validateEntryLine(
      VoucherEntryLineRequest line,
      int lineNumber,
      Long companyId,
      VoucherValidationResult result,
      Map<Long, ChartOfAccount> accountCache) {

    if (line.getDebitAccountId() == null) {
      result.addError(lineNumber, "debitAccount", "Debit account is required");
    }
    if (line.getCreditAccountId() == null) {
      result.addError(lineNumber, "creditAccount", "Credit account is required");
    }

    if (line.getDebitAccountId() != null
        && line.getCreditAccountId() != null
        && Objects.equals(line.getDebitAccountId(), line.getCreditAccountId())) {
      result.addError(lineNumber, "creditAccount", "Debit and credit accounts must be different");
    }

    if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      result.addError(lineNumber, "amount", "Amount must be greater than 0");
    }

    ChartOfAccount debitAccount = resolveAccount(line.getDebitAccountId(), companyId, lineNumber, "debitAccount",
        result, accountCache);
    ChartOfAccount creditAccount = resolveAccount(line.getCreditAccountId(), companyId, lineNumber, "creditAccount",
        result, accountCache);

    if (debitAccount != null) {
      validateAccountIsPostable(debitAccount, lineNumber, "debitAccount", result);
      validateDimensionRequirements(
          debitAccount,
          line.getCustomerId(),
          line.getSupplierId(),
          line.getCostCenterId(),
          lineNumber,
          result);
    }

    if (creditAccount != null) {
      validateAccountIsPostable(creditAccount, lineNumber, "creditAccount", result);
      validateDimensionRequirements(
          creditAccount,
          line.getCustomerId(),
          line.getSupplierId(),
          line.getCostCenterId(),
          lineNumber,
          result);
    }
  }

  private void validateLedgerLines(
      List<VoucherLineDTO> lines, Long companyId, VoucherValidationResult result) {

    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    Map<Long, ChartOfAccount> accountCache = new HashMap<>();

    for (int i = 0; i < lines.size(); i++) {
      VoucherLineDTO line = lines.get(i);
      int lineNumber = i + 1;

      ChartOfAccount account = resolveAccount(line.getAccountId(), companyId, lineNumber, "accountId", result,
          accountCache);
      BigDecimal debit = line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO;
      BigDecimal credit = line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO;

      if (debit.compareTo(BigDecimal.ZERO) < 0) {
        result.addError(lineNumber, "debit", "Debit amount must be non-negative");
      }

      if (credit.compareTo(BigDecimal.ZERO) < 0) {
        result.addError(lineNumber, "credit", "Credit amount must be non-negative");
      }

      if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
        result.addError(lineNumber, "debit", "Either debit or credit must be greater than zero");
        result.addError(lineNumber, "credit", "Either debit or credit must be greater than zero");
      }

      if (!result.hasErrorsForLine(lineNumber)) {
        totalDebit = totalDebit.add(debit);
        totalCredit = totalCredit.add(credit);
      }

      if (account != null) {
        validateAccountIsPostable(account, lineNumber, "accountId", result);
        validateDimensionRequirements(
            account,
            line.getCustomerId(),
            line.getVendorId(),
            line.getCostCenterId(),
            lineNumber,
            result);
      }
    }

    if (totalDebit.compareTo(totalCredit) != 0) {
      result.addError(
          0,
          "balance",
          String.format(
              "Double-entry balance error: Total Debit (%s) must equal Total Credit (%s)",
              totalDebit, totalCredit));
    }
  }

  private ChartOfAccount resolveAccount(
      Long accountId,
      Long companyId,
      int lineNumber,
      String fieldName,
      VoucherValidationResult result,
      Map<Long, ChartOfAccount> cache) {

    if (accountId == null) {
      return null;
    }

    if (cache.containsKey(accountId)) {
      return cache.get(accountId);
    }

    ChartOfAccount account = chartOfAccountsRepository.findById(accountId).orElse(null);
    if (account == null) {
      result.addError(lineNumber, fieldName, "Account not found");
      cache.put(accountId, null);
      return null;
    }

    if (!companyId.equals(account.getCompanyId())) {
      result.addError(lineNumber, fieldName, "Account does not belong to your company");
      cache.put(accountId, null);
      return null;
    }

    cache.put(accountId, account);
    return account;
  }

  private void validateAccountIsPostable(
      ChartOfAccount account, int lineNumber, String fieldName, VoucherValidationResult result) {
    if (Boolean.FALSE.equals(account.getPostable())) {
      result.addError(
          lineNumber,
          fieldName,
          "Account "
              + account.getCode()
              + " is not postable. Please select a leaf level account.");
    }
  }

  /**
   * Validate required dimensions based on account type.
   *
   * @param line        line item
   * @param lineNumber  line number for error reporting
   * @param accountCode account code (e.g., "131", "331", "154", "621")
   * @param result      validation result
   */
  private void validateDimensionRequirements(
      ChartOfAccount account,
      Long customerId,
      Long supplierId,
      Long costCenterId,
      int lineNumber,
      VoucherValidationResult result) {

    String accountCode = account.getCode();

    if (accountCode.startsWith("131") && customerId == null) {
      result.addError(lineNumber, "customerId", "Customer is required for account " + accountCode);
    }

    if (accountCode.startsWith("331") && supplierId == null) {
      result.addError(lineNumber, "supplierId", "Supplier is required for account " + accountCode);
    }

    if ((accountCode.startsWith("154") || accountCode.startsWith("621")) && costCenterId == null) {
      result.addError(
          lineNumber, "costCenterId", "Cost Center is required for account " + accountCode);
    }
  }
}

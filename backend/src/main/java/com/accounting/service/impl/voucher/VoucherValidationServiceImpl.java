package com.accounting.service.impl.voucher;

import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.dto.VoucherLineDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.entity.AccountControl;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AccountControlService;
import com.accounting.service.AuditService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.VoucherValidationService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of VoucherValidationService.
 * Validates double-entry balance, leaf-only accounts, required dimensions, and
 * negative amounts.
 * 
 * Validation Rules:
 * - Leaf-only validation: Only accounts with postable=true AND no child
 * accounts can be used
 * - BigDecimal double-entry: Total Debit must equal Total Credit using
 * BigDecimal with HALF_UP rounding
 * (Precision: 19 digits, Scale: 2 decimal places)
 * - Negative amount blocking: Negative debit/credit values are blocked and
 * logged as fraud
 * - Required dimensions: Company/config-driven via account_controls table
 * - Bulk validation: All errors collected and returned at once
 */
@Service
public class VoucherValidationServiceImpl implements VoucherValidationService {

  private static final Logger logger = LoggerFactory.getLogger(VoucherValidationServiceImpl.class);

  // BigDecimal precision and scale constants
  // Precision: 19 digits (total), Scale: 2 decimal places
  // Rounding mode: HALF_UP (rounds 0.5 up, standard for financial calculations)
  private static final int SCALE = 2; // Number of decimal places
  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  // Tolerance for rounding differences (0.01 VND)
  private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.01");

  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final AccountControlService accountControlService;
  private final AuditService auditService;
  private final PeriodManagementService periodManagementService;

  public VoucherValidationServiceImpl(
      ChartOfAccountsRepository chartOfAccountsRepository,
      AccountControlService accountControlService,
      AuditService auditService,
      PeriodManagementService periodManagementService) {
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.accountControlService = accountControlService;
    this.auditService = auditService;
    this.periodManagementService = periodManagementService;
  }

  /**
   * Get current HTTP request from RequestContextHolder for audit logging.
   */
  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
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

    // Validate period status (OPEN/CLOSED) - check if period is open for voucher operations
    if (request.getDate() != null) {
      try {
        java.util.Optional<com.accounting.dto.AccountingPeriodDTO> periodOpt =
            periodManagementService.findPeriodByDate(request.getDate());
        if (periodOpt.isPresent()) {
          com.accounting.dto.AccountingPeriodDTO period = periodOpt.get();
          if (!periodManagementService.isPeriodOpen(period.getId())) {
            result.addError(0, "period", "Cannot create voucher in closed period: " + period.getPeriodName());
            result.setValid(false);
            // Log blocked attempt in audit trail
            auditService.logPeriodValidationBlocked(
                period.getId(),
                "voucher_creation",
                "Period is closed: " + period.getPeriodName());
          }
        } else {
          result.addError(0, "period", "No period found for date: " + request.getDate());
          result.setValid(false);
        }
      } catch (Exception e) {
        logger.warn("Failed to validate period for voucher date {}: {}", request.getDate(), e.getMessage());
        result.addError(0, "period", "Failed to validate period: " + e.getMessage());
        result.setValid(false);
      }
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

    // Validate negative amounts (block and log as fraud)
    if (line.getAmount() != null && line.getAmount().compareTo(BigDecimal.ZERO) < 0) {
      result.addError(lineNumber, "amount", "Amount must be non-negative");
      // Log fraud detection - need to resolve account first
      ChartOfAccount account = line.getDebitAccountId() != null
          ? resolveAccount(line.getDebitAccountId(), companyId, lineNumber, "debitAccount", result, accountCache)
          : null;
      logFraudDetection("NEGATIVE_AMOUNT", account, lineNumber, line.getAmount());
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
          null, // itemId not available in VoucherEntryLineRequest yet
          companyId,
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
          null, // itemId not available in VoucherEntryLineRequest yet
          companyId,
          lineNumber,
          result);
    }
  }

  /**
   * Validate ledger lines with BigDecimal double-entry validation and negative
   * amount blocking.
   * Uses HALF_UP rounding mode for balance checks.
   */
  private void validateLedgerLines(
      List<VoucherLineDTO> lines, Long companyId, VoucherValidationResult result) {

    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    Map<Long, ChartOfAccount> accountCache = new HashMap<>();

    for (int i = 0; i < lines.size(); i++) {
      VoucherLineDTO line = lines.get(i);
      int lineNumber = i + 1;

      // Validate accountId is not null
      if (line.getAccountId() == null) {
        result.addError(lineNumber, "accountId", "Account is required");
      }

      ChartOfAccount account = resolveAccount(line.getAccountId(), companyId, lineNumber, "accountId", result,
          accountCache);
      BigDecimal debit = line.getDebit() != null ? line.getDebit() : BigDecimal.ZERO;
      BigDecimal credit = line.getCredit() != null ? line.getCredit() : BigDecimal.ZERO;

      // Validate negative amounts (block and log as fraud)
      if (debit.compareTo(BigDecimal.ZERO) < 0) {
        result.addError(lineNumber, "debit", "Debit amount must be non-negative");
        logFraudDetection("NEGATIVE_DEBIT", account, lineNumber, debit);
      }

      if (credit.compareTo(BigDecimal.ZERO) < 0) {
        result.addError(lineNumber, "credit", "Credit amount must be non-negative");
        logFraudDetection("NEGATIVE_CREDIT", account, lineNumber, credit);
      }

      // Validate that both debit and credit cannot be non-zero at the same time
      if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0) {
        result.addError(lineNumber, "debit", "Debit and credit cannot both be greater than zero");
        result.addError(lineNumber, "credit", "Debit and credit cannot both be greater than zero");
      }

      if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
        result.addError(lineNumber, "debit", "Either debit or credit must be greater than zero");
        result.addError(lineNumber, "credit", "Either debit or credit must be greater than zero");
      }

      // Only add to totals if no errors for this line
      if (!result.hasErrorsForLine(lineNumber)) {
        // Use BigDecimal with proper scale and rounding
        totalDebit = totalDebit.add(debit.setScale(SCALE, ROUNDING_MODE));
        totalCredit = totalCredit.add(credit.setScale(SCALE, ROUNDING_MODE));
      }

      if (account != null) {
        validateAccountIsPostable(account, lineNumber, "accountId", result);
        validateDimensionRequirements(
            account,
            line.getCustomerId(),
            line.getVendorId(),
            line.getCostCenterId(),
            null, // itemId not available in VoucherLineDTO yet
            companyId,
            lineNumber,
            result);
      }
    }

    // Validate double-entry balance using BigDecimal with HALF_UP rounding
    // Round both totals to 2 decimal places using HALF_UP mode
    totalDebit = totalDebit.setScale(SCALE, ROUNDING_MODE);
    totalCredit = totalCredit.setScale(SCALE, ROUNDING_MODE);

    BigDecimal difference = totalDebit.subtract(totalCredit).abs();

    // Allow small rounding differences (tolerance: 0.01 VND)
    if (difference.compareTo(ROUNDING_TOLERANCE) > 0) {
      result.addError(
          0,
          "balance",
          String.format(
              "Double-entry balance error: Total Debit (%s) must equal Total Credit (%s). Difference: %s",
              totalDebit, totalCredit, difference));
    }
  }

  /**
   * Resolve account by ID with caching and validation.
   * 
   * <p>
   * This method implements account resolution with caching to avoid redundant
   * database queries when the same account is referenced multiple times in a
   * voucher.
   * The caching pattern improves performance for bulk validation scenarios.
   * 
   * <p>
   * <b>Caching Strategy:</b>
   * <ul>
   * <li>Cache is passed in by caller and shared across all line validations</li>
   * <li>Cache key: accountId, value: ChartOfAccount (or null if not
   * found/invalid)</li>
   * <li>Cache lookup happens before database query</li>
   * <li>Cache is populated after successful resolution or error</li>
   * </ul>
   * 
   * <p>
   * <b>Validation:</b>
   * <ul>
   * <li>Account must exist in database</li>
   * <li>Account must belong to the specified company (company isolation)</li>
   * <li>Validation errors are added to result if checks fail</li>
   * </ul>
   * 
   * <p>
   * <b>Note:</b> If this caching pattern is needed elsewhere, consider extracting
   * to a shared utility service (e.g., AccountResolutionService).
   *
   * @param accountId  account ID to resolve (may be null)
   * @param companyId  company ID for company scoping validation (must not be
   *                   null)
   * @param lineNumber line number for error reporting (1-based)
   * @param fieldName  field name for error reporting (e.g., "debitAccount",
   *                   "creditAccount")
   * @param result     validation result to add errors to (must not be null)
   * @param cache      account cache map (shared across validation, must not be
   *                   null)
   * @return resolved ChartOfAccount, or null if accountId is null, account not
   *         found, or company mismatch
   */
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

  /**
   * Validate that account is leaf-only (postable=true AND no child accounts).
   * Logs audit entry for blocked attempts.
   */
  private void validateAccountIsPostable(
      ChartOfAccount account, int lineNumber, String fieldName, VoucherValidationResult result) {

    // Check if account is postable
    if (Boolean.FALSE.equals(account.getPostable())) {
      String errorMessage = "Account " + account.getCode() + " is not postable. Please select a leaf level account.";
      result.addError(lineNumber, fieldName, errorMessage);

      // Log audit entry for blocked attempt
      logBlockedAttempt("NON_POSTABLE_ACCOUNT", account, lineNumber, fieldName, errorMessage);
      return;
    }

    // Check if account has children (not a leaf)
    boolean hasChildren = chartOfAccountsRepository.hasChildren(account.getId());
    if (hasChildren) {
      String errorMessage = "Account " + account.getCode()
          + " has child accounts and cannot be used for posting. Please select a leaf level account.";
      result.addError(lineNumber, fieldName, errorMessage);

      // Log audit entry for blocked attempt
      logBlockedAttempt("NON_LEAF_ACCOUNT", account, lineNumber, fieldName, errorMessage);
    }
  }

  /**
   * Validate required dimensions using company/config-driven account_controls
   * table.
   * 
   * <p>
   * This method implements company-specific dimension requirements per account.
   * The validation flow is as follows:
   * <ol>
   * <li>Lookup account control configuration for the given account and company
   * via {@link AccountControlService#getRequiredDimensions(Long, Long)}</li>
   * <li>If no account control is configured, no dimension requirements are
   * enforced
   * (graceful default - allows accounts without dimension requirements)</li>
   * <li>If account control exists, validate each required dimension:
   * <ul>
   * <li>requires_customer: customerId must be present</li>
   * <li>requires_supplier: supplierId must be present</li>
   * <li>requires_cost_center: costCenterId must be present</li>
   * <li>requires_item: itemId must be present</li>
   * </ul>
   * </li>
   * <li>Collect all dimension errors via
   * {@link AccountControlService#validateRequiredDimensions}</li>
   * <li>Map error messages to field names (customerId, supplierId, costCenterId,
   * itemId)</li>
   * <li>Add all errors to validation result with account code context</li>
   * </ol>
   * 
   * <p>
   * <b>Example:</b> If account 131 (AR) has requires_customer=true in
   * account_controls,
   * and a voucher line uses account 131 without customerId, this method will add
   * an error
   * to the validation result: "Customer is required for this account (Account:
   * 131)".
   * 
   * <p>
   * <b>Bulk Validation:</b> All dimension errors are collected before returning,
   * ensuring all validation failures are reported at once (not sequentially).
   * 
   * <p>
   * <b>Company Scoping:</b> Account controls are company-scoped, so the same
   * account
   * can have different dimension requirements for different companies.
   *
   * @param account      account to validate (must not be null)
   * @param customerId   customer ID (optional, validated if account control
   *                     requires it)
   * @param supplierId   supplier ID (optional, validated if account control
   *                     requires it)
   * @param costCenterId cost center ID (optional, validated if account control
   *                     requires it)
   * @param itemId       item ID (optional, validated if account control requires
   *                     it)
   * @param companyId    company ID for account control lookup (must not be null)
   * @param lineNumber   line number for error reporting (1-based)
   * @param result       validation result to add errors to (must not be null)
   * 
   * @see AccountControlService#getRequiredDimensions(Long, Long)
   * @see AccountControlService#validateRequiredDimensions(AccountControl, Long,
   *      Long, Long, Long)
   */
  private void validateDimensionRequirements(
      ChartOfAccount account,
      Long customerId,
      Long supplierId,
      Long costCenterId,
      Long itemId,
      Long companyId,
      int lineNumber,
      VoucherValidationResult result) {

    // Get account control configuration for this account and company
    Optional<AccountControl> accountControlOpt = accountControlService.getRequiredDimensions(account.getId(),
        companyId);

    // If no account control configured, no dimension requirements
    if (accountControlOpt.isEmpty()) {
      return;
    }

    AccountControl accountControl = accountControlOpt.get();

    // Validate required dimensions using AccountControlService
    List<String> dimensionErrors = accountControlService.validateRequiredDimensions(
        accountControl, customerId, supplierId, costCenterId, itemId);

    // Add all dimension errors to validation result
    for (String error : dimensionErrors) {
      // Determine field name based on error message
      String fieldName = "dimensions";
      if (error.contains("Customer")) {
        fieldName = "customerId";
      } else if (error.contains("Supplier")) {
        fieldName = "supplierId";
      } else if (error.contains("Cost Center")) {
        fieldName = "costCenterId";
      } else if (error.contains("Item")) {
        fieldName = "itemId";
      }

      result.addError(lineNumber, fieldName, error + " (Account: " + account.getCode() + ")");
    }
  }

  /**
   * Log fraud detection event for negative amount attempts.
   * Creates audit log entry with "possible fraud" event type.
   */
  private void logFraudDetection(String fraudType, ChartOfAccount account, int lineNumber, BigDecimal amount) {
    try {
      Long userId = null;

      try {
        userId = SecurityUtils.getCurrentUserId();
      } catch (Exception e) {
        logger.warn("Could not get current user ID for fraud detection logging", e);
      }

      HttpServletRequest request = getCurrentRequest();

      // Log via logger for immediate visibility
      logger.warn(
          "POSSIBLE FRAUD DETECTED: {} - User: {}, Account: {}, Line: {}, Amount: {}",
          fraudType, userId, account != null ? account.getCode() : "N/A", lineNumber, amount);

      // Create audit log entry via AuditService
      auditService.logFraudDetection(
          userId,
          account != null ? account.getId() : null,
          account != null ? account.getCode() : null,
          lineNumber,
          amount,
          fraudType,
          request);

    } catch (Exception e) {
      // Don't let audit logging failure break validation
      logger.error("Failed to log fraud detection event", e);
    }
  }

  /**
   * Log blocked attempt (non-postable account, non-leaf account).
   * Creates audit log entry for security tracking.
   */
  private void logBlockedAttempt(String attemptType, ChartOfAccount account, int lineNumber, String fieldName,
      String reason) {
    try {
      Long userId = null;

      try {
        userId = SecurityUtils.getCurrentUserId();
      } catch (Exception e) {
        logger.debug("Could not get current user ID for blocked attempt logging", e);
      }

      HttpServletRequest request = getCurrentRequest();

      // Log via logger for immediate visibility
      logger.info(
          "BLOCKED ATTEMPT: {} - User: {}, Account: {}, Line: {}, Field: {}, Reason: {}",
          attemptType, userId, account != null ? account.getCode() : "N/A", lineNumber, fieldName, reason);

      // Create audit log entry via AuditService
      auditService.logBlockedAttempt(
          userId,
          account != null ? account.getId() : null,
          account != null ? account.getCode() : null,
          lineNumber,
          fieldName,
          reason,
          attemptType,
          request);

    } catch (Exception e) {
      // Don't let audit logging failure break validation
      logger.error("Failed to log blocked attempt", e);
    }
  }
}

package com.accounting.service.impl.purchase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.PurchaseBillCreateRequest;
import com.accounting.dto.PurchaseBillLineDTO;
import com.accounting.dto.PurchaseBillValidationResult;
import com.accounting.entity.AccountControl;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountControlService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.PurchaseBillValidationService;

/**
 * Implementation of PurchaseBillValidationService.
 * Validates bill number uniqueness, dates, line items, VAT sum, duplicates, and required dimensions.
 */
@Service
public class PurchaseBillValidationServiceImpl implements PurchaseBillValidationService {

  private static final Logger logger = LoggerFactory.getLogger(PurchaseBillValidationServiceImpl.class);

  // VAT sum tolerance: 1,000₫
  private static final BigDecimal VAT_TOLERANCE = new BigDecimal("1000.00");

  // BigDecimal precision and scale constants
  private static final int SCALE = 2; // Number of decimal places
  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  private final PurchaseBillRepository purchaseBillRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final AccountControlService accountControlService;
  private final PeriodManagementService periodManagementService;

  public PurchaseBillValidationServiceImpl(
      PurchaseBillRepository purchaseBillRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      AccountControlService accountControlService,
      PeriodManagementService periodManagementService) {
    this.purchaseBillRepository = purchaseBillRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.accountControlService = accountControlService;
    this.periodManagementService = periodManagementService;
  }

  @Override
  public PurchaseBillValidationResult validate(PurchaseBillCreateRequest request, UUID billId) {
    PurchaseBillValidationResult result = new PurchaseBillValidationResult();
    Long companyId = CompanyContext.getCompanyId();

    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    if (request == null) {
      result.addHeaderError("general", "Request payload is required");
      return result;
    }

    // Validate bill number uniqueness
    if (!validateBillNumber(
        request.getSupplierId(),
        request.getBillNumber(),
        request.getBillDate(),
        companyId,
        billId)) {
      result.addHeaderError(
          "billNumber",
          "Bill number must be unique per supplier per year. A bill with this number already exists for this supplier in "
              + request.getBillDate().getYear());
      result.setValid(false);
    }

    // Validate dates
    PurchaseBillValidationResult dateResult = validateDates(request.getBillDate(), request.getDueDate());
    if (!dateResult.isValid()) {
      dateResult.getHeaderErrors().forEach((field, errors) -> {
        errors.forEach(error -> result.addHeaderError(field, error));
      });
      result.setValid(false);
    }

    // Validate duplicate supplier+bill/date combination
    if (!validateDuplicate(
        request.getSupplierId(),
        request.getBillNumber(),
        request.getBillDate(),
        companyId,
        billId)) {
      result.addHeaderError(
          "duplicate",
          "A bill with the same supplier, bill number, and date already exists");
      result.setValid(false);
    }

    // Validate line items
    if (request.getLines() != null && !request.getLines().isEmpty()) {
      PurchaseBillValidationResult lineResult = validateLineItems(request.getLines(), companyId);
      if (!lineResult.isValid()) {
        lineResult.getLineErrors().forEach((lineNum, lineErrors) -> {
          lineErrors.forEach((field, errors) -> {
            errors.forEach(error -> result.addLineError(lineNum, field, error));
          });
        });
        result.setValid(false);
      }

      // Validate required dimensions
      PurchaseBillValidationResult dimensionResult = validateRequiredDimensions(request.getLines(), companyId);
      if (!dimensionResult.isValid()) {
        dimensionResult.getLineErrors().forEach((lineNum, lineErrors) -> {
          lineErrors.forEach((field, errors) -> {
            errors.forEach(error -> result.addLineError(lineNum, field, error));
          });
        });
        result.setValid(false);
      }

      // Calculate line VAT sum and validate against header VAT
      BigDecimal lineVatSum = request.getLines().stream()
          .map(PurchaseBillLineDTO::getVatAmount)
          .reduce(BigDecimal.ZERO, BigDecimal::add)
          .setScale(SCALE, ROUNDING_MODE);

      // Get header VAT amount from request (if provided) or default to line VAT sum
      BigDecimal headerVatAmount = request.getVatAmount() != null
          ? request.getVatAmount().setScale(SCALE, ROUNDING_MODE)
          : lineVatSum; // If not provided, use line VAT sum as default
      PurchaseBillValidationResult vatResult = validateVATSum(headerVatAmount, lineVatSum);
      if (!vatResult.isValid()) {
        vatResult.getHeaderErrors().forEach((field, errors) -> {
          errors.forEach(error -> result.addHeaderError(field, error));
        });
        result.setValid(false);
      }
    } else {
      result.addHeaderError("lines", "At least one line item is required");
      result.setValid(false);
    }

    return result;
  }

  @Override
  public boolean validateBillNumber(
      Long supplierId, String billNumber, LocalDate billDate, Long companyId, UUID billId) {
    if (supplierId == null || billNumber == null || billDate == null || companyId == null) {
      return false;
    }

    LocalDate yearStart = billDate.withDayOfYear(1);
    LocalDate yearEnd = yearStart.plusYears(1);
    return !purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndYear(
        companyId, supplierId, billNumber, yearStart, yearEnd, billId);
  }

  @Override
  public PurchaseBillValidationResult validateDates(LocalDate billDate, LocalDate dueDate) {
    PurchaseBillValidationResult result = new PurchaseBillValidationResult();

    if (billDate == null) {
      result.addHeaderError("billDate", "Bill date is required");
      result.setValid(false);
      return result;
    }

    if (dueDate == null) {
      result.addHeaderError("dueDate", "Due date is required");
      result.setValid(false);
      return result;
    }

    // Validate future dates disabled
    LocalDate today = LocalDate.now();
    if (billDate.isAfter(today)) {
      result.addHeaderError("billDate", "Bill date cannot be in the future");
      result.setValid(false);
    }

    // Validate due date is after bill date
    if (dueDate.isBefore(billDate)) {
      result.addHeaderError("dueDate", "Due date must be on or after bill date");
      result.setValid(false);
    }

    // Validate period status (OPEN/CLOSED) - check if period is open for bill operations
    try {
      Optional<com.accounting.dto.AccountingPeriodDTO> periodOpt =
          periodManagementService.findPeriodByDate(billDate);
      if (periodOpt.isPresent()) {
        com.accounting.dto.AccountingPeriodDTO period = periodOpt.get();
        if (!periodManagementService.isPeriodOpen(period.getId())) {
          result.addHeaderError(
              "period", "Cannot create bill in closed period: " + period.getPeriodName());
          result.setValid(false);
        }
      } else {
        result.addHeaderError("period", "No period found for date: " + billDate);
        result.setValid(false);
      }
    } catch (Exception e) {
      logger.warn("Failed to validate period for bill date {}: {}", billDate, e.getMessage());
      result.addHeaderError("period", "Failed to validate period: " + e.getMessage());
      result.setValid(false);
    }

    return result;
  }

  @Override
  public PurchaseBillValidationResult validateLineItems(
      List<PurchaseBillLineDTO> lines, Long companyId) {
    PurchaseBillValidationResult result = new PurchaseBillValidationResult();
    Map<Long, ChartOfAccount> accountCache = new HashMap<>();

    for (int i = 0; i < lines.size(); i++) {
      PurchaseBillLineDTO line = lines.get(i);
      int lineNumber = i + 1;

      // Validate accountId is not null
      if (line.getAccountId() == null) {
        result.addLineError(lineNumber, "accountId", "Account is required");
        continue;
      }

      ChartOfAccount account = resolveAccount(line.getAccountId(), companyId, lineNumber, "accountId", result, accountCache);

      if (account != null) {
        // Validate account is leaf and postable
        validateAccountIsPostable(account, lineNumber, "accountId", result);

        // Validate positive amounts
        if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
          result.addLineError(lineNumber, "amount", "Amount must be greater than 0");
        }

        if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
          result.addLineError(lineNumber, "quantity", "Quantity must be non-negative");
        }

        if (line.getUnitPrice() == null || line.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
          result.addLineError(lineNumber, "unitPrice", "Unit price must be greater than 0");
        }

        // Validate description is required
        if (line.getDescription() == null || line.getDescription().trim().isEmpty()) {
          result.addLineError(lineNumber, "description", "Description is required");
        }
      }
    }

    return result;
  }

  @Override
  public PurchaseBillValidationResult validateVATSum(BigDecimal headerVatAmount, BigDecimal lineVatSum) {
    PurchaseBillValidationResult result = new PurchaseBillValidationResult();

    if (headerVatAmount == null) {
      headerVatAmount = BigDecimal.ZERO;
    }
    if (lineVatSum == null) {
      lineVatSum = BigDecimal.ZERO;
    }

    BigDecimal difference = headerVatAmount.subtract(lineVatSum).abs();

    if (difference.compareTo(VAT_TOLERANCE) > 0) {
      result.addHeaderError(
          "vatAmount",
          String.format(
              "VAT sum mismatch: Header VAT (%,.2f₫) does not match line VAT sum (%,.2f₫). Difference: %,.2f₫ (tolerance: %,.2f₫)",
              headerVatAmount, lineVatSum, difference, VAT_TOLERANCE));
      result.setValid(false);
    }

    return result;
  }

  @Override
  public boolean validateDuplicate(
      Long supplierId, String billNumber, LocalDate billDate, Long companyId, UUID billId) {
    if (supplierId == null || billNumber == null || billDate == null || companyId == null) {
      return false;
    }

    return !purchaseBillRepository.existsByCompanyIdAndSupplierIdAndBillNumberAndBillDate(
        companyId, supplierId, billNumber, billDate, billId);
  }

  @Override
  public PurchaseBillValidationResult validateRequiredDimensions(
      List<PurchaseBillLineDTO> lines, Long companyId) {
    PurchaseBillValidationResult result = new PurchaseBillValidationResult();
    Map<Long, ChartOfAccount> accountCache = new HashMap<>();

    for (int i = 0; i < lines.size(); i++) {
      PurchaseBillLineDTO line = lines.get(i);
      int lineNumber = i + 1;

      if (line.getAccountId() == null) {
        continue; // Skip if account is not set (will be caught by other validation)
      }

      ChartOfAccount account = resolveAccount(line.getAccountId(), companyId, lineNumber, "accountId", result, accountCache);

      if (account != null) {
        validateDimensionRequirements(
            account,
            null, // customerId not applicable for purchase bills
            null, // supplierId not applicable at line level (it's at header level)
            line.getCostCenterId(),
            line.getItemId(),
            companyId,
            lineNumber,
            result);
      }
    }

    return result;
  }

  /**
   * Resolve account by ID with company scoping and caching.
   */
  private ChartOfAccount resolveAccount(
      Long accountId,
      Long companyId,
      int lineNumber,
      String fieldName,
      PurchaseBillValidationResult result,
      Map<Long, ChartOfAccount> cache) {

    if (accountId == null) {
      return null;
    }

    if (cache.containsKey(accountId)) {
      return cache.get(accountId);
    }

    ChartOfAccount account = chartOfAccountsRepository.findById(accountId).orElse(null);
    if (account == null) {
      result.addLineError(lineNumber, fieldName, "Account not found");
      cache.put(accountId, null);
      return null;
    }

    if (!companyId.equals(account.getCompanyId())) {
      result.addLineError(lineNumber, fieldName, "Account does not belong to your company");
      cache.put(accountId, null);
      return null;
    }

    cache.put(accountId, account);
    return account;
  }

  /**
   * Validate that account is leaf-only (postable=true AND no child accounts).
   */
  private void validateAccountIsPostable(
      ChartOfAccount account, int lineNumber, String fieldName, PurchaseBillValidationResult result) {

    // Check if account is postable
    if (Boolean.FALSE.equals(account.getPostable())) {
      String errorMessage =
          "Account " + account.getCode() + " is not postable. Please select a leaf level account.";
      result.addLineError(lineNumber, fieldName, errorMessage);
      return;
    }

    // Check if account has children (not a leaf)
    boolean hasChildren = chartOfAccountsRepository.hasChildren(account.getId());
    if (hasChildren) {
      String errorMessage =
          "Account "
              + account.getCode()
              + " has child accounts and cannot be used for posting. Please select a leaf level account.";
      result.addLineError(lineNumber, fieldName, errorMessage);
    }
  }

  /**
   * Validate required dimensions using company/config-driven account_controls table.
   */
  private void validateDimensionRequirements(
      ChartOfAccount account,
      Long customerId,
      Long supplierId,
      Long costCenterId,
      Long itemId,
      Long companyId,
      int lineNumber,
      PurchaseBillValidationResult result) {

    // Get account control configuration for this account and company
    Optional<AccountControl> accountControlOpt =
        accountControlService.getRequiredDimensions(account.getId(), companyId);

    // If no account control configured, no dimension requirements
    if (accountControlOpt.isEmpty()) {
      return;
    }

    AccountControl accountControl = accountControlOpt.get();

    // Validate required dimensions using AccountControlService
    List<String> dimensionErrors =
        accountControlService.validateRequiredDimensions(
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

      result.addLineError(lineNumber, fieldName, error + " (Account: " + account.getCode() + ")");
    }
  }
}

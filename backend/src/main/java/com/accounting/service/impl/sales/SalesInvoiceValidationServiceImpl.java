package com.accounting.service.impl.sales;

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

import com.accounting.dto.SalesInvoiceCreateRequest;
import com.accounting.dto.SalesInvoiceLineDTO;
import com.accounting.dto.SalesInvoiceValidationResult;
import com.accounting.entity.AccountControl;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountControlService;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.SalesInvoiceValidationService;

/**
 * Implementation of SalesInvoiceValidationService.
 * Validates invoice number uniqueness, dates, line items, VAT sum, duplicates,
 * and required dimensions.
 */
@Service
public class SalesInvoiceValidationServiceImpl implements SalesInvoiceValidationService {

    private static final Logger logger = LoggerFactory.getLogger(SalesInvoiceValidationServiceImpl.class);

    // VAT sum tolerance: 1,000₫
    private static final BigDecimal VAT_TOLERANCE = new BigDecimal("1000.00");

    // BigDecimal precision and scale constants
    private static final int SCALE = 2; // Number of decimal places
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AccountControlService accountControlService;
    private final PeriodManagementService periodManagementService;

    public SalesInvoiceValidationServiceImpl(
            SalesInvoiceRepository salesInvoiceRepository,
            ChartOfAccountsRepository chartOfAccountsRepository,
            AccountControlService accountControlService,
            PeriodManagementService periodManagementService) {
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
        this.accountControlService = accountControlService;
        this.periodManagementService = periodManagementService;
    }

    @Override
    public SalesInvoiceValidationResult validate(SalesInvoiceCreateRequest request, UUID invoiceId) {
        SalesInvoiceValidationResult result = new SalesInvoiceValidationResult();
        Long companyId = CompanyContext.getCompanyId();

        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
        }

        if (request == null) {
            result.setHeaderErrors(new HashMap<>());
            result.getHeaderErrors().put("general", "Request payload is required");
            result.setValid(false);
            return result;
        }

        // Initialize maps if null
        if (result.getHeaderErrors() == null) {
            result.setHeaderErrors(new HashMap<>());
        }
        if (result.getLineErrors() == null) {
            result.setLineErrors(new HashMap<>());
        }

        // Validate invoice number uniqueness
        if (!validateInvoiceNumber(
                request.getCustomerId(),
                request.getInvoiceNumber(),
                request.getInvoiceDate(),
                companyId,
                invoiceId)) {
            result.getHeaderErrors().put(
                    "invoiceNumber",
                    "Invoice number must be unique per customer per year. An invoice with this number already exists for this customer in "
                            + request.getInvoiceDate().getYear());
            result.setValid(false);
        }

        // Validate dates
        SalesInvoiceValidationResult dateResult = validateDates(request.getInvoiceDate(), request.getDueDate());
        if (!dateResult.isValid()) {
            if (dateResult.getHeaderErrors() != null) {
                dateResult.getHeaderErrors().forEach((field, error) -> result.getHeaderErrors().put(field, error));
            }
            result.setValid(false);
        }

        // Validate duplicate customer+invoice/date combination
        if (!validateDuplicate(
                request.getCustomerId(),
                request.getInvoiceNumber(),
                request.getInvoiceDate(),
                companyId,
                invoiceId)) {
            result.getHeaderErrors().put(
                    "duplicate",
                    "An invoice with the same customer, invoice number, and date already exists");
            result.setValid(false);
        }

        // Validate line items
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            SalesInvoiceValidationResult lineResult = validateLineItems(request.getLines(), companyId);
            if (!lineResult.isValid()) {
                if (lineResult.getLineErrors() != null) {
                    lineResult.getLineErrors().forEach((lineNum, lineErrors) -> {
                        if (!result.getLineErrors().containsKey(lineNum)) {
                            result.getLineErrors().put(lineNum, new HashMap<>());
                        }
                        lineErrors.forEach((field, error) -> result.getLineErrors().get(lineNum).put(field, error));
                    });
                }
                result.setValid(false);
            }

            // Validate required dimensions
            SalesInvoiceValidationResult dimensionResult = validateRequiredDimensions(request.getLines(), companyId);
            if (!dimensionResult.isValid()) {
                if (dimensionResult.getLineErrors() != null) {
                    dimensionResult.getLineErrors().forEach((lineNum, lineErrors) -> {
                        if (!result.getLineErrors().containsKey(lineNum)) {
                            result.getLineErrors().put(lineNum, new HashMap<>());
                        }
                        lineErrors.forEach((field, error) -> result.getLineErrors().get(lineNum).put(field, error));
                    });
                }
                result.setValid(false);
            }

            // Calculate line VAT sum and validate against header VAT
            BigDecimal lineVatSum = request.getLines().stream()
                    .map(SalesInvoiceLineDTO::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(SCALE, ROUNDING_MODE);

            // Get header VAT amount from request (if provided) or default to line VAT sum
            BigDecimal headerVatAmount = request.getVatAmount() != null
                    ? request.getVatAmount().setScale(SCALE, ROUNDING_MODE)
                    : lineVatSum; // If not provided, use line VAT sum as default
            SalesInvoiceValidationResult vatResult = validateVATSum(headerVatAmount, lineVatSum);
            if (!vatResult.isValid()) {
                if (vatResult.getHeaderErrors() != null) {
                    vatResult.getHeaderErrors().forEach((field, error) -> result.getHeaderErrors().put(field, error));
                }
                result.setValid(false);
            }
        } else {
            result.getHeaderErrors().put("lines", "At least one line item is required");
            result.setValid(false);
        }

        // Set isValid to true if no errors found and wasn't already set to false
        if (result.getHeaderErrors().isEmpty() && result.getLineErrors().isEmpty()) {
            result.setValid(true);
        }

        return result;
    }

    @Override
    public boolean validateInvoiceNumber(
            Long customerId, String invoiceNumber, LocalDate invoiceDate, Long companyId, UUID invoiceId) {
        if (customerId == null || invoiceNumber == null || invoiceDate == null || companyId == null) {
            return false;
        }

        LocalDate yearStart = invoiceDate.withDayOfYear(1);
        LocalDate yearEnd = yearStart.plusYears(1);
        return !salesInvoiceRepository.existsByCompanyIdAndCustomerIdAndInvoiceNumberAndYear(
                companyId, customerId, invoiceNumber, yearStart, yearEnd, invoiceId);
    }

    @Override
    public SalesInvoiceValidationResult validateDates(LocalDate invoiceDate, LocalDate dueDate) {
        SalesInvoiceValidationResult result = new SalesInvoiceValidationResult();
        result.setHeaderErrors(new HashMap<>());
        result.setValid(true);

        if (invoiceDate == null) {
            result.getHeaderErrors().put("invoiceDate", "Invoice date is required");
            result.setValid(false);
            return result;
        }

        if (dueDate == null) {
            result.getHeaderErrors().put("dueDate", "Due date is required");
            result.setValid(false);
            return result;
        }

        // Validate future dates disabled
        LocalDate today = LocalDate.now();
        if (invoiceDate.isAfter(today)) {
            result.getHeaderErrors().put("invoiceDate", "Invoice date cannot be in the future");
            result.setValid(false);
        }

        // Validate due date is after invoice date
        if (dueDate.isBefore(invoiceDate)) {
            result.getHeaderErrors().put("dueDate", "Due date must be on or after invoice date");
            result.setValid(false);
        }

        // Validate period status (OPEN/CLOSED) - check if period is open for invoice
        // operations
        try {
            Optional<com.accounting.dto.AccountingPeriodDTO> periodOpt = periodManagementService
                    .findPeriodByDate(invoiceDate);
            if (periodOpt.isPresent()) {
                com.accounting.dto.AccountingPeriodDTO period = periodOpt.get();
                if (!periodManagementService.isPeriodOpen(period.getId())) {
                    result.getHeaderErrors().put(
                            "period", "Cannot create invoice in closed period: " + period.getPeriodName());
                    result.setValid(false);
                }
            } else {
                result.getHeaderErrors().put("period", "No period found for date: " + invoiceDate);
                result.setValid(false);
            }
        } catch (Exception e) {
            logger.warn("Failed to validate period for invoice date {}: {}", invoiceDate, e.getMessage());
            result.getHeaderErrors().put("period", "Failed to validate period: " + e.getMessage());
            result.setValid(false);
        }

        return result;
    }

    @Override
    public SalesInvoiceValidationResult validateLineItems(
            List<SalesInvoiceLineDTO> lines, Long companyId) {
        SalesInvoiceValidationResult result = new SalesInvoiceValidationResult();
        result.setLineErrors(new HashMap<>());
        result.setValid(true);

        Map<Long, ChartOfAccount> accountCache = new HashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            SalesInvoiceLineDTO line = lines.get(i);
            int lineNumber = i + 1;

            // Validate accountId is not null
            if (line.getAccountId() == null) {
                addLineError(result, lineNumber, "accountId", "Account is required");
                continue;
            }

            ChartOfAccount account = resolveAccount(line.getAccountId(), companyId, lineNumber, "accountId", result,
                    accountCache);

            if (account != null) {
                // Validate account is leaf and postable
                validateAccountIsPostable(account, lineNumber, "accountId", result);

                // Validate positive amounts
                if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    addLineError(result, lineNumber, "amount", "Amount must be greater than 0");
                }

                if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
                    addLineError(result, lineNumber, "quantity", "Quantity must be non-negative");
                }

                if (line.getUnitPrice() == null || line.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    addLineError(result, lineNumber, "unitPrice", "Unit price must be greater than 0");
                }

                // Validate description is required
                if (line.getDescription() == null || line.getDescription().trim().isEmpty()) {
                    addLineError(result, lineNumber, "description", "Description is required");
                }
            }
        }

        return result;
    }

    @Override
    public SalesInvoiceValidationResult validateVATSum(BigDecimal headerVatAmount, BigDecimal lineVatSum) {
        SalesInvoiceValidationResult result = new SalesInvoiceValidationResult();
        result.setHeaderErrors(new HashMap<>());
        result.setValid(true);

        if (headerVatAmount == null) {
            headerVatAmount = BigDecimal.ZERO;
        }
        if (lineVatSum == null) {
            lineVatSum = BigDecimal.ZERO;
        }

        BigDecimal difference = headerVatAmount.subtract(lineVatSum).abs();

        if (difference.compareTo(VAT_TOLERANCE) > 0) {
            result.getHeaderErrors().put(
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
            Long customerId, String invoiceNumber, LocalDate invoiceDate, Long companyId, UUID invoiceId) {
        if (customerId == null || invoiceNumber == null || invoiceDate == null || companyId == null) {
            return false;
        }

        return !salesInvoiceRepository.existsByCompanyIdAndCustomerIdAndInvoiceNumberAndInvoiceDate(
                companyId, customerId, invoiceNumber, invoiceDate, invoiceId);
    }

    @Override
    public SalesInvoiceValidationResult validateRequiredDimensions(
            List<SalesInvoiceLineDTO> lines, Long companyId) {
        SalesInvoiceValidationResult result = new SalesInvoiceValidationResult();
        result.setLineErrors(new HashMap<>());
        result.setValid(true);

        Map<Long, ChartOfAccount> accountCache = new HashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            SalesInvoiceLineDTO line = lines.get(i);
            int lineNumber = i + 1;

            if (line.getAccountId() == null) {
                continue; // Skip if account is not set (will be caught by other validation)
            }

            ChartOfAccount account = resolveAccount(line.getAccountId(), companyId, lineNumber, "accountId", result,
                    accountCache);

            if (account != null) {
                validateDimensionRequirements(
                        account,
                        null, // customerId checked at header
                        null, // supplierId not applicable
                        line.getItemId(),
                        companyId,
                        lineNumber,
                        result);
            }
        }

        return result;
    }

    private void addLineError(SalesInvoiceValidationResult result, int lineNumber, String field, String message) {
        if (!result.getLineErrors().containsKey(lineNumber)) {
            result.getLineErrors().put(lineNumber, new HashMap<>());
        }
        result.getLineErrors().get(lineNumber).put(field, message);
        result.setValid(false);
    }

    /**
     * Resolve account by ID with company scoping and caching.
     */
    private ChartOfAccount resolveAccount(
            Long accountId,
            Long companyId,
            int lineNumber,
            String fieldName,
            SalesInvoiceValidationResult result,
            Map<Long, ChartOfAccount> cache) {

        if (accountId == null) {
            return null;
        }

        if (cache.containsKey(accountId)) {
            return cache.get(accountId);
        }

        ChartOfAccount account = chartOfAccountsRepository.findById(accountId).orElse(null);
        if (account == null) {
            addLineError(result, lineNumber, fieldName, "Account not found");
            cache.put(accountId, null);
            return null;
        }

        if (!companyId.equals(account.getCompanyId())) {
            addLineError(result, lineNumber, fieldName, "Account does not belong to your company");
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
            ChartOfAccount account, int lineNumber, String fieldName, SalesInvoiceValidationResult result) {

        // Check if account is postable
        if (Boolean.FALSE.equals(account.getPostable())) {
            String errorMessage = "Account " + account.getCode()
                    + " is not postable. Please select a leaf level account.";
            addLineError(result, lineNumber, fieldName, errorMessage);
            return;
        }

        // Check if account has children (not a leaf)
        boolean hasChildren = chartOfAccountsRepository.hasChildren(account.getId());
        if (hasChildren) {
            String errorMessage = "Account "
                    + account.getCode()
                    + " has child accounts and cannot be used for posting. Please select a leaf level account.";
            addLineError(result, lineNumber, fieldName, errorMessage);
        }
    }

    /**
     * Validate required dimensions using company/config-driven account_controls
     * table.
     */
    private void validateDimensionRequirements(
            ChartOfAccount account,
            Long customerId,
            Long supplierId,
            Long itemId,
            Long companyId,
            int lineNumber,
            SalesInvoiceValidationResult result) {

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
                accountControl, customerId, supplierId, itemId);

        // Add all dimension errors to validation result
        for (String error : dimensionErrors) {
            // Determine field name based on error message
            String fieldName = "dimensions";
            if (error.contains("Customer")) {
                fieldName = "customerId";
            } else if (error.contains("Supplier")) {
                fieldName = "supplierId";
            } else if (error.contains("Item")) {
                fieldName = "itemId";
            }

            addLineError(result, lineNumber, fieldName, error + " (Account: " + account.getCode() + ")");
        }
    }
}

package com.accounting.service.impl.sales;

import com.accounting.dto.ARPaymentCreateRequest;
import com.accounting.dto.ImportResultDTO;
import com.accounting.dto.ImportRowErrorDTO;
import com.accounting.dto.ReceiptAllocationRequest;
import com.accounting.entity.BankAccount;
import com.accounting.entity.Customer;
import com.accounting.entity.PaymentMethod;
import com.accounting.entity.SalesInvoice;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.ImportErrorReportRepository;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.ReceiptImportService;
import com.accounting.service.ReceiptService;
import com.accounting.service.ReceiptValidationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of ReceiptImportService for Excel batch import.
 * Supports Excel (.xlsx, .xls) format with validated template.
 * Format: Each row represents one receipt.
 * Columns: Customer Code, Receipt Date, Account Code (Cash/Bank), Amount, Reference,
 * Payment Method, Payee, Receipt Proof URL (optional), Is Standalone (Y/N),
 * Invoice Numbers (comma-separated, optional - FIFO if empty)
 */
@Service
@Transactional
public class ReceiptImportServiceImpl implements ReceiptImportService {

  private static final Logger logger = LoggerFactory.getLogger(ReceiptImportServiceImpl.class);
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final ReceiptService receiptService;
  private final ReceiptValidationService receiptValidationService;
  private final CustomerRepository customerRepository;
  private final BankAccountRepository bankAccountRepository;
  private final SalesInvoiceRepository salesInvoiceRepository;
  private final ImportErrorReportRepository importErrorReportRepository;
  private final AuditService auditService;

  @PersistenceContext
  private EntityManager entityManager;

  public ReceiptImportServiceImpl(
      ReceiptService receiptService,
      ReceiptValidationService receiptValidationService,
      CustomerRepository customerRepository,
      BankAccountRepository bankAccountRepository,
      SalesInvoiceRepository salesInvoiceRepository,
      ImportErrorReportRepository importErrorReportRepository,
      AuditService auditService) {
    this.receiptService = receiptService;
    this.receiptValidationService = receiptValidationService;
    this.customerRepository = customerRepository;
    this.bankAccountRepository = bankAccountRepository;
    this.salesInvoiceRepository = salesInvoiceRepository;
    this.importErrorReportRepository = importErrorReportRepository;
    this.auditService = auditService;
  }

  @Override
  public ImportResultDTO importReceipts(MultipartFile file) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    List<ImportRowErrorDTO> errors = new ArrayList<>();
    List<ARPaymentCreateRequest> validRequests = new ArrayList<>();
    Map<String, Long> customerCache = new HashMap<>(); // customer code -> customer ID
    Map<String, Long> accountCache = new HashMap<>(); // account code -> account ID

    try (InputStream inputStream = file.getInputStream()) {
      Workbook workbook = WorkbookFactory.create(inputStream);
      Sheet sheet = workbook.getSheetAt(0);

      // Validate header row
      Row headerRow = sheet.getRow(0);
      if (headerRow == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Excel file is empty or missing header row");
      }

      // Validate headers
      validateHeaders(headerRow, errors);
      if (!errors.isEmpty()) {
        workbook.close();
        return createErrorResult(errors);
      }

      // Parse data rows (skip header row)
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null || isEmptyRow(row)) {
          continue;
        }

        int rowNumber = i + 1; // 1-based row number for error reporting
        ARPaymentCreateRequest request = new ARPaymentCreateRequest();
        List<ImportRowErrorDTO> rowErrors = new ArrayList<>();

        // Parse customer code (required, column 0)
        String customerCode = getCellValueAsString(row.getCell(0));
        if (customerCode == null || customerCode.isBlank()) {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "customerCode", "Customer code is required"));
        } else {
          Long customerId = findCustomerByCode(customerCode.trim(), companyId, rowNumber, rowErrors, customerCache);
          if (customerId != null) {
            request.setCustomerId(customerId);
          }
        }

        // Parse receipt date (required, column 1)
        LocalDate receiptDate = parseDate(row.getCell(1), rowNumber, "receiptDate", rowErrors);
        if (receiptDate != null) {
          request.setReceiptDate(receiptDate);
        } else {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "receiptDate", "Receipt date is required"));
        }

        // Parse account code (required, column 2 - Cash or Bank account)
        String accountCode = getCellValueAsString(row.getCell(2));
        if (accountCode == null || accountCode.isBlank()) {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "accountCode", "Account code is required"));
        } else {
          Long accountId = findAccountByCode(accountCode.trim(), companyId, rowNumber, rowErrors, accountCache);
          if (accountId != null) {
            // Determine if it's cash or bank account based on account type
            BankAccount account = bankAccountRepository.findByCompanyIdAndId(companyId, accountId).orElse(null);
            if (account != null) {
              // Check account type or account number prefix
              String accountNumber = account.getAccountNumber();
              if (accountNumber != null && accountNumber.startsWith("111")) {
                request.setCashAccountId(accountId);
              } else {
                request.setBankAccountId(accountId);
              }
            }
          }
        }

        // Parse amount (required, column 3)
        BigDecimal amount = parseBigDecimal(row.getCell(3), rowNumber, "amount", rowErrors);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "amount", "Amount is required and must be positive"));
        } else {
          request.setAmount(amount);
        }

        // Parse reference (optional, column 4)
        String reference = getCellValueAsString(row.getCell(4));
        if (reference != null && !reference.isBlank()) {
          if (reference.length() > 100) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "reference", "Reference must not exceed 100 characters"));
          } else {
            request.setReference(reference.trim());
          }
        }

        // Parse payment method (optional, column 5, default: BANK_TRANSFER)
        String paymentMethodStr = getCellValueAsString(row.getCell(5));
        PaymentMethod paymentMethod = parsePaymentMethod(paymentMethodStr, rowNumber, rowErrors);
        request.setPaymentMethod(paymentMethod != null ? paymentMethod : PaymentMethod.BANK_TRANSFER);

        // Parse payee (optional, column 6)
        String payee = getCellValueAsString(row.getCell(6));
        if (payee != null && !payee.isBlank()) {
          if (payee.length() > 200) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "payee", "Payee must not exceed 200 characters"));
          } else {
            request.setPayee(payee.trim());
          }
        }

        // Parse receipt proof URL (optional, column 7)
        String receiptProofUrl = getCellValueAsString(row.getCell(7));
        if (receiptProofUrl != null && !receiptProofUrl.isBlank()) {
          if (receiptProofUrl.length() > 500) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "receiptProofUrl", "Receipt proof URL must not exceed 500 characters"));
          } else {
            request.setReceiptProofUrl(receiptProofUrl.trim());
          }
        }

        // Parse is standalone (optional, column 8, default: false)
        String isStandaloneStr = getCellValueAsString(row.getCell(8));
        boolean isStandalone = "Y".equalsIgnoreCase(isStandaloneStr) || "YES".equalsIgnoreCase(isStandaloneStr) || "TRUE".equalsIgnoreCase(isStandaloneStr);
        request.setIsStandalone(isStandalone);

        // Parse invoice numbers for allocations (optional, column 9, comma-separated)
        // If not provided, FIFO allocation will be performed automatically
        String invoiceNumbersStr = getCellValueAsString(row.getCell(9));
        if (invoiceNumbersStr != null && !invoiceNumbersStr.isBlank() && !isStandalone) {
          List<ReceiptAllocationRequest> allocations = parseInvoiceAllocations(
              invoiceNumbersStr.trim(), request.getCustomerId(), amount, companyId, rowNumber, rowErrors);
          if (!allocations.isEmpty()) {
            request.setAllocations(allocations);
          }
        }

        // Validate the request (basic validations already done, skip comprehensive validation for import)
        // Comprehensive validation will be done in ReceiptService.create()

        if (rowErrors.isEmpty()) {
          validRequests.add(request);
        } else {
          errors.addAll(rowErrors);
        }
      }

      workbook.close();

      // Save all valid rows (atomic transaction: all valid rows or none)
      int successCount = 0;
      for (int i = 0; i < validRequests.size(); i++) {
        ARPaymentCreateRequest request = validRequests.get(i);
        try {
          receiptService.create(request);
          successCount++;

          // Log audit entry for imported row
          try {
            logger.debug("Imported receipt: {} (row {})", request.getAmount(), i + 2);
          } catch (Exception e) {
            logger.warn("Failed to log audit entry for imported receipt: {}", e.getMessage());
          }
        } catch (Exception e) {
          // If any save fails, add error and mark transaction for rollback
          int errorRowNumber = i + 2; // Header row + 1-based index
          String errorMessage = e.getMessage();
          if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = e.getClass().getSimpleName();
          }
          if (e instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) e;
            errorMessage = rse.getReason() != null ? rse.getReason() : errorMessage;
          }
          errors.add(new ImportRowErrorDTO(errorRowNumber, "general", errorMessage));
          // Mark transaction for rollback
          TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
          // Return early with errors - transaction will rollback all saves
          return createErrorResult(errors);
        }
      }

      // Create error report if there are errors
      UUID errorReportId = null;
      if (!errors.isEmpty()) {
        // TODO: Create error report entity and save
        // errorReportId = importErrorReportRepository.save(...).getId();
      }

      // Log import audit event
      try {
        Long importedByUserId = SecurityUtils.getCurrentUserId();
        // TODO: Add logReceiptImport method to AuditService
        logger.info("Receipt import completed: {} successful, {} errors", successCount, errors.size());
      } catch (Exception e) {
        // Non-blocking: log error but don't break main flow
        logger.error("Failed to log receipt import audit event: {}", e.getMessage(), e);
      }

      return new ImportResultDTO(successCount, 0, errors.size(), errors, errorReportId);

    } catch (Exception e) {
      logger.error("Failed to import receipts: {}", e.getMessage(), e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to import receipts: " + e.getMessage());
    }
  }

  @Override
  public byte[] generateTemplate() {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Receipts");

      // Create header row
      Row headerRow = sheet.createRow(0);
      String[] headers = {
        "Customer Code",
        "Receipt Date (YYYY-MM-DD)",
        "Account Code (Cash/Bank)",
        "Amount",
        "Reference",
        "Payment Method (CASH/BANK_TRANSFER/CHECK/OTHER)",
        "Payee",
        "Receipt Proof URL",
        "Is Standalone (Y/N)",
        "Invoice Numbers (comma-separated, optional - FIFO if empty)"
      };

      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
      }

      // Create example row
      Row exampleRow = sheet.createRow(1);
      exampleRow.createCell(0).setCellValue("CUST001");
      exampleRow.createCell(1).setCellValue("2025-01-15");
      exampleRow.createCell(2).setCellValue("112001");
      exampleRow.createCell(3).setCellValue("5000000.00");
      exampleRow.createCell(4).setCellValue("Payment for invoice #INV-001");
      exampleRow.createCell(5).setCellValue("BANK_TRANSFER");
      exampleRow.createCell(6).setCellValue("ABC Customer Company");
      exampleRow.createCell(7).setCellValue("");
      exampleRow.createCell(8).setCellValue("N");
      exampleRow.createCell(9).setCellValue("INV-001,INV-002");

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      workbook.close();
      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate template: " + e.getMessage());
    }
  }

  private void validateHeaders(Row headerRow, List<ImportRowErrorDTO> errors) {
    String[] expectedHeaders = {
      "Customer Code",
      "Receipt Date (YYYY-MM-DD)",
      "Account Code (Cash/Bank)",
      "Amount",
      "Reference",
      "Payment Method (CASH/BANK_TRANSFER/CHECK/OTHER)",
      "Payee",
      "Receipt Proof URL",
      "Is Standalone (Y/N)",
      "Invoice Numbers (comma-separated, optional - FIFO if empty)"
    };

    for (int i = 0; i < expectedHeaders.length; i++) {
      String cellValue = getCellValueAsString(headerRow.getCell(i));
      if (cellValue == null || !cellValue.trim().equalsIgnoreCase(expectedHeaders[i])) {
        errors.add(new ImportRowErrorDTO(1, "header", "Invalid header at column " + (i + 1) + ": expected '" + expectedHeaders[i] + "'"));
      }
    }
  }

  private Long findCustomerByCode(
      String customerCode,
      Long companyId,
      int rowNumber,
      List<ImportRowErrorDTO> rowErrors,
      Map<String, Long> cache) {
    if (cache.containsKey(customerCode)) {
      return cache.get(customerCode);
    }

    // Try to find existing customer by code using search method
    List<Customer> customers = customerRepository.searchByCodeOrNameNative(companyId, customerCode);
    Optional<Customer> customerOpt = customers.stream()
        .filter(c -> c.getCode() != null && c.getCode().equalsIgnoreCase(customerCode))
        .findFirst();

    if (customerOpt.isPresent()) {
      Long customerId = customerOpt.get().getId();
      cache.put(customerCode, customerId);
      return customerId;
    }

    rowErrors.add(new ImportRowErrorDTO(rowNumber, "customerCode", "Customer not found: " + customerCode));
    return null;
  }

  private Long findAccountByCode(
      String accountCode,
      Long companyId,
      int rowNumber,
      List<ImportRowErrorDTO> rowErrors,
      Map<String, Long> cache) {
    if (cache.containsKey(accountCode)) {
      return cache.get(accountCode);
    }

    // Find account by account number (assuming accountCode is account number)
    Optional<BankAccount> accountOpt = bankAccountRepository.findByCompanyIdAndAccountNumber(companyId, accountCode);
    if (accountOpt.isPresent()) {
      Long accountId = accountOpt.get().getId();
      cache.put(accountCode, accountId);
      return accountId;
    }

    rowErrors.add(new ImportRowErrorDTO(rowNumber, "accountCode", "Account not found: " + accountCode));
    return null;
  }

  private List<ReceiptAllocationRequest> parseInvoiceAllocations(
      String invoiceNumbersStr,
      Long customerId,
      BigDecimal receiptAmount,
      Long companyId,
      int rowNumber,
      List<ImportRowErrorDTO> rowErrors) {
    List<ReceiptAllocationRequest> allocations = new ArrayList<>();
    String[] invoiceNumbers = invoiceNumbersStr.split(",");

    BigDecimal totalAllocated = BigDecimal.ZERO;
    for (String invoiceNumber : invoiceNumbers) {
      invoiceNumber = invoiceNumber.trim();
      if (invoiceNumber.isEmpty()) {
        continue;
      }

      // Find invoice by number (search in company-scoped invoices)
      final String invoiceNumberFinal = invoiceNumber; // Make effectively final for lambda
      List<SalesInvoice> invoices = salesInvoiceRepository.findByCompanyId(companyId);
      Optional<SalesInvoice> invoiceOpt = invoices.stream()
          .filter(inv -> inv.getInvoiceNumber() != null && inv.getInvoiceNumber().equalsIgnoreCase(invoiceNumberFinal))
          .filter(inv -> !inv.getIsDeleted())
          .findFirst();
      
      if (invoiceOpt.isEmpty()) {
        rowErrors.add(new ImportRowErrorDTO(rowNumber, "invoiceNumbers", "Invoice not found: " + invoiceNumber));
        continue;
      }

      SalesInvoice invoice = invoiceOpt.get();
      if (!invoice.getCustomerId().equals(customerId)) {
        rowErrors.add(new ImportRowErrorDTO(rowNumber, "invoiceNumbers", "Invoice " + invoiceNumber + " does not belong to customer"));
        continue;
      }

      // Calculate remaining balance (use total amount as fallback - actual balance would be calculated by service)
      BigDecimal remainingBalance = invoice.getTotalAmount();
      BigDecimal allocatedAmount = remainingBalance.min(receiptAmount.subtract(totalAllocated));

      ReceiptAllocationRequest allocation = new ReceiptAllocationRequest();
      allocation.setSalesInvoiceId(invoice.getId());
      allocation.setAllocatedAmount(allocatedAmount);
      allocations.add(allocation);

      totalAllocated = totalAllocated.add(allocatedAmount);
      if (totalAllocated.compareTo(receiptAmount) >= 0) {
        break; // Fully allocated
      }
    }

    // Validate total allocated amount matches receipt amount
    if (totalAllocated.compareTo(receiptAmount) != 0) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, "invoiceNumbers", 
          "Total allocated amount (" + totalAllocated + ") does not match receipt amount (" + receiptAmount + ")"));
    }

    return allocations;
  }

  private PaymentMethod parsePaymentMethod(String value, int rowNumber, List<ImportRowErrorDTO> rowErrors) {
    if (value == null || value.isBlank()) {
      return null; // Will use default
    }

    try {
      return PaymentMethod.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, "paymentMethod", 
          "Invalid payment method: " + value + ". Valid values: CASH, BANK_TRANSFER, CHECK, OTHER"));
      return null;
    }
  }

  private LocalDate parseDate(Cell cell, int rowNumber, String fieldName, List<ImportRowErrorDTO> rowErrors) {
    if (cell == null) {
      return null;
    }

    try {
      if (cell.getCellType() == CellType.NUMERIC) {
        return org.apache.poi.ss.usermodel.DateUtil.getJavaDate(cell.getNumericCellValue()).toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate();
      } else if (cell.getCellType() == CellType.STRING) {
        String dateStr = cell.getStringCellValue().trim();
        if (dateStr.isEmpty()) {
          return null;
        }
        return LocalDate.parse(dateStr, DATE_FORMATTER);
      }
    } catch (DateTimeParseException e) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, fieldName, "Invalid date format. Expected YYYY-MM-DD"));
    } catch (Exception e) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, fieldName, "Failed to parse date: " + e.getMessage()));
    }

    return null;
  }

  private BigDecimal parseBigDecimal(Cell cell, int rowNumber, String fieldName, List<ImportRowErrorDTO> rowErrors) {
    if (cell == null) {
      return null;
    }

    try {
      if (cell.getCellType() == CellType.NUMERIC) {
        return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, java.math.RoundingMode.HALF_UP);
      } else if (cell.getCellType() == CellType.STRING) {
        String value = cell.getStringCellValue().trim();
        if (value.isEmpty()) {
          return null;
        }
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
      }
    } catch (NumberFormatException e) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, fieldName, "Invalid number format"));
    } catch (Exception e) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, fieldName, "Failed to parse number: " + e.getMessage()));
    }

    return null;
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) {
      return null;
    }

    try {
      if (cell.getCellType() == CellType.STRING) {
        return cell.getStringCellValue();
      } else if (cell.getCellType() == CellType.NUMERIC) {
        if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
          return cell.getDateCellValue().toInstant()
              .atZone(java.time.ZoneId.systemDefault())
              .toLocalDate()
              .format(DATE_FORMATTER);
        } else {
          return String.valueOf(cell.getNumericCellValue());
        }
      } else if (cell.getCellType() == CellType.BOOLEAN) {
        return String.valueOf(cell.getBooleanCellValue());
      } else if (cell.getCellType() == CellType.FORMULA) {
        return cell.getStringCellValue();
      }
    } catch (Exception e) {
      logger.warn("Failed to get cell value as string: {}", e.getMessage());
    }

    return null;
  }

  private boolean isEmptyRow(Row row) {
    if (row == null) {
      return true;
    }

    for (int i = 0; i < row.getLastCellNum(); i++) {
      Cell cell = row.getCell(i);
      if (cell != null && cell.getCellType() != CellType.BLANK) {
        String value = getCellValueAsString(cell);
        if (value != null && !value.trim().isEmpty()) {
          return false;
        }
      }
    }

    return true;
  }

  private ImportResultDTO createErrorResult(List<ImportRowErrorDTO> errors) {
    return new ImportResultDTO(0, 0, errors.size(), errors, null);
  }
}

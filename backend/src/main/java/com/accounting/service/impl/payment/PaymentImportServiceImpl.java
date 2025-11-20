package com.accounting.service.impl.payment;

import com.accounting.dto.APPaymentCreateRequest;
import com.accounting.dto.ImportResultDTO;
import com.accounting.dto.ImportRowErrorDTO;
import com.accounting.dto.PaymentAllocationRequest;
import com.accounting.entity.BankAccount;
import com.accounting.entity.PaymentMethod;
import com.accounting.entity.PaymentStatus;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.Supplier;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ImportErrorReportRepository;
import com.accounting.repository.PurchaseBillRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.PaymentImportService;
import com.accounting.service.PaymentService;
import com.accounting.service.PaymentValidationService;
import com.accounting.service.SupplierService;
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
 * Implementation of PaymentImportService for Excel batch import.
 * Supports Excel (.xlsx, .xls) format with validated template.
 * Format: Each row represents one payment.
 * Columns: Supplier Code, Payment Date, Due Date (optional), Account Code (Cash/Bank),
 * Amount, Reference, Payment Method, Payee, Payment Proof URL (optional),
 * Is Standalone (Y/N), Bill Numbers (comma-separated, optional - FIFO if empty)
 */
@Service
@Transactional
public class PaymentImportServiceImpl implements PaymentImportService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentImportServiceImpl.class);

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final PaymentService paymentService;
  private final PaymentValidationService paymentValidationService;
  private final SupplierService supplierService;
  private final SupplierRepository supplierRepository;
  private final BankAccountRepository bankAccountRepository;
  private final PurchaseBillRepository purchaseBillRepository;
  private final ImportErrorReportRepository importErrorReportRepository;
  private final AuditService auditService;

  @PersistenceContext
  private EntityManager entityManager;

  public PaymentImportServiceImpl(
      PaymentService paymentService,
      PaymentValidationService paymentValidationService,
      SupplierService supplierService,
      SupplierRepository supplierRepository,
      BankAccountRepository bankAccountRepository,
      PurchaseBillRepository purchaseBillRepository,
      ImportErrorReportRepository importErrorReportRepository,
      AuditService auditService) {
    this.paymentService = paymentService;
    this.paymentValidationService = paymentValidationService;
    this.supplierService = supplierService;
    this.supplierRepository = supplierRepository;
    this.bankAccountRepository = bankAccountRepository;
    this.purchaseBillRepository = purchaseBillRepository;
    this.importErrorReportRepository = importErrorReportRepository;
    this.auditService = auditService;
  }

  @Override
  public ImportResultDTO importPayments(MultipartFile file) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    List<ImportRowErrorDTO> errors = new ArrayList<>();
    List<APPaymentCreateRequest> validRequests = new ArrayList<>();
    Map<String, Long> supplierCache = new HashMap<>(); // supplier code -> supplier ID
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
        APPaymentCreateRequest request = new APPaymentCreateRequest();
        List<ImportRowErrorDTO> rowErrors = new ArrayList<>();

        // Parse supplier code (required, column 0)
        String supplierCode = getCellValueAsString(row.getCell(0));
        if (supplierCode == null || supplierCode.isBlank()) {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "supplierCode", "Supplier code is required"));
        } else {
          Long supplierId = findOrCreateSupplier(supplierCode.trim(), companyId, rowNumber, rowErrors, supplierCache);
          if (supplierId != null) {
            request.setSupplierId(supplierId);
          }
        }

        // Parse payment date (required, column 1)
        LocalDate paymentDate = parseDate(row.getCell(1), rowNumber, "paymentDate", rowErrors);
        if (paymentDate != null) {
          request.setPaymentDate(paymentDate);
        }

        // Parse due date (optional, column 2)
        LocalDate dueDate = parseDate(row.getCell(2), rowNumber, "dueDate", rowErrors);
        if (dueDate != null) {
          request.setDueDate(dueDate);
        }

        // Parse account code (required, column 3 - Cash or Bank account)
        String accountCode = getCellValueAsString(row.getCell(3));
        if (accountCode == null || accountCode.isBlank()) {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "accountCode", "Account code is required"));
        } else {
          Long accountId = findAccountByCode(accountCode.trim(), companyId, rowNumber, rowErrors, accountCache);
          if (accountId != null) {
            // Determine if it's cash or bank account (simplified: check account number prefix)
            BankAccount account = bankAccountRepository.findByCompanyIdAndId(companyId, accountId).orElse(null);
            if (account != null) {
              // Assume cash accounts have prefix "111" and bank accounts have prefix "112"
              // This is a simplification - in real system, check account type
              String accountNumber = account.getAccountNumber();
              if (accountNumber != null && accountNumber.startsWith("111")) {
                request.setCashAccountId(accountId);
              } else {
                request.setBankAccountId(accountId);
              }
            }
          }
        }

        // Parse amount (required, column 4)
        BigDecimal amount = parseBigDecimal(row.getCell(4), rowNumber, "amount", rowErrors);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "amount", "Amount is required and must be positive"));
        } else {
          request.setAmount(amount);
        }

        // Parse reference (optional, column 5)
        String reference = getCellValueAsString(row.getCell(5));
        if (reference != null && !reference.isBlank()) {
          if (reference.length() > 100) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "reference", "Reference must not exceed 100 characters"));
          } else {
            request.setReference(reference.trim());
          }
        }

        // Parse payment method (optional, column 6, default: BANK_TRANSFER)
        String paymentMethodStr = getCellValueAsString(row.getCell(6));
        PaymentMethod paymentMethod = parsePaymentMethod(paymentMethodStr, rowNumber, rowErrors);
        request.setPaymentMethod(paymentMethod != null ? paymentMethod : PaymentMethod.BANK_TRANSFER);

        // Parse payee (optional, column 7)
        String payee = getCellValueAsString(row.getCell(7));
        if (payee != null && !payee.isBlank()) {
          if (payee.length() > 200) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "payee", "Payee must not exceed 200 characters"));
          } else {
            request.setPayee(payee.trim());
          }
        }

        // Parse payment proof URL (optional, column 8)
        String paymentProofUrl = getCellValueAsString(row.getCell(8));
        if (paymentProofUrl != null && !paymentProofUrl.isBlank()) {
          if (paymentProofUrl.length() > 500) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "paymentProofUrl", "Payment proof URL must not exceed 500 characters"));
          } else {
            request.setPaymentProofUrl(paymentProofUrl.trim());
          }
        }

        // Parse is standalone (optional, column 9, default: false)
        String isStandaloneStr = getCellValueAsString(row.getCell(9));
        boolean isStandalone = "Y".equalsIgnoreCase(isStandaloneStr) || "YES".equalsIgnoreCase(isStandaloneStr) || "TRUE".equalsIgnoreCase(isStandaloneStr);
        request.setIsStandalone(isStandalone);

        // Parse bill numbers for allocations (optional, column 10, comma-separated)
        // If not provided, FIFO allocation will be performed automatically
        String billNumbersStr = getCellValueAsString(row.getCell(10));
        if (billNumbersStr != null && !billNumbersStr.isBlank() && !isStandalone) {
          List<PaymentAllocationRequest> allocations = parseBillAllocations(
              billNumbersStr.trim(), request.getSupplierId(), amount, companyId, rowNumber, rowErrors);
          if (!allocations.isEmpty()) {
            request.setAllocations(allocations);
          }
        }

        // Validate the request (basic validations already done, skip comprehensive validation for import)
        // Comprehensive validation will be done in PaymentService.create()

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
        APPaymentCreateRequest request = validRequests.get(i);
        try {
          paymentService.create(request);
          successCount++;

          // Log audit entry for imported row
          try {
            logger.debug("Imported payment: {} (row {})", request.getAmount(), i + 2);
          } catch (Exception e) {
            logger.warn("Failed to log audit entry for imported payment: {}", e.getMessage());
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
        // TODO: Add logPaymentImport method to AuditService
        logger.info("Payment import completed: {} successful, {} errors", successCount, errors.size());
      } catch (Exception e) {
        // Non-blocking: log error but don't break main flow
        logger.error("Failed to log payment import audit event: {}", e.getMessage(), e);
      }

      return new ImportResultDTO(successCount, 0, errors.size(), errors, errorReportId);

    } catch (Exception e) {
      logger.error("Failed to import payments: {}", e.getMessage(), e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to import payments: " + e.getMessage());
    }
  }

  @Override
  public byte[] generateTemplate() {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Payments");

      // Create header row
      Row headerRow = sheet.createRow(0);
      String[] headers = {
        "Supplier Code",
        "Payment Date (YYYY-MM-DD)",
        "Due Date (YYYY-MM-DD)",
        "Account Code (Cash/Bank)",
        "Amount",
        "Reference",
        "Payment Method (CASH/BANK_TRANSFER/CHECK/OTHER)",
        "Payee",
        "Payment Proof URL",
        "Is Standalone (Y/N)",
        "Bill Numbers (comma-separated, optional - FIFO if empty)"
      };

      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
      }

      // Create example row
      Row exampleRow = sheet.createRow(1);
      exampleRow.createCell(0).setCellValue("SUP001");
      exampleRow.createCell(1).setCellValue("2025-01-15");
      exampleRow.createCell(2).setCellValue("2025-01-15");
      exampleRow.createCell(3).setCellValue("112001");
      exampleRow.createCell(4).setCellValue("1000000.00");
      exampleRow.createCell(5).setCellValue("Payment for invoice #12345");
      exampleRow.createCell(6).setCellValue("BANK_TRANSFER");
      exampleRow.createCell(7).setCellValue("ABC Company");
      exampleRow.createCell(8).setCellValue("");
      exampleRow.createCell(9).setCellValue("N");
      exampleRow.createCell(10).setCellValue("BILL-001,BILL-002");

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
      "Supplier Code",
      "Payment Date (YYYY-MM-DD)",
      "Due Date (YYYY-MM-DD)",
      "Account Code (Cash/Bank)",
      "Amount",
      "Reference",
      "Payment Method (CASH/BANK_TRANSFER/CHECK/OTHER)",
      "Payee",
      "Payment Proof URL",
      "Is Standalone (Y/N)",
      "Bill Numbers (comma-separated, optional - FIFO if empty)"
    };

    for (int i = 0; i < expectedHeaders.length; i++) {
      String cellValue = getCellValueAsString(headerRow.getCell(i));
      if (cellValue == null || !cellValue.trim().equalsIgnoreCase(expectedHeaders[i])) {
        errors.add(new ImportRowErrorDTO(1, "header", "Invalid header at column " + (i + 1) + ": expected '" + expectedHeaders[i] + "'"));
      }
    }
  }

  private Long findOrCreateSupplier(
      String supplierCode,
      Long companyId,
      int rowNumber,
      List<ImportRowErrorDTO> rowErrors,
      Map<String, Long> cache) {
    if (cache.containsKey(supplierCode)) {
      return cache.get(supplierCode);
    }

    // Try to find existing supplier by code
    List<Supplier> suppliers = supplierRepository.searchByCodeOrNameNative(companyId, supplierCode);
    Optional<Supplier> supplierOpt = suppliers.stream()
        .filter(s -> s.getCode() != null && s.getCode().equalsIgnoreCase(supplierCode))
        .findFirst();

    if (supplierOpt.isPresent()) {
      Long supplierId = supplierOpt.get().getId();
      cache.put(supplierCode, supplierId);
      return supplierId;
    }

    // Auto-create draft supplier (similar to purchase bill import)
    try {
      var supplierRequest = new com.accounting.dto.SupplierCreateRequest();
      supplierRequest.setCode(supplierCode);
      supplierRequest.setName(supplierCode); // Use code as name initially
      supplierRequest.setActive(false); // Draft supplier, inactive until confirmed
      var createdSupplier = supplierService.create(supplierRequest);
      Long supplierId = createdSupplier.getId();
      cache.put(supplierCode, supplierId);
      logger.info("Auto-created draft supplier: {} (code: {})", supplierId, supplierCode);
      return supplierId;
    } catch (Exception e) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, "supplierCode", "Failed to find or create supplier: " + e.getMessage()));
      return null;
    }
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

  private List<PaymentAllocationRequest> parseBillAllocations(
      String billNumbersStr,
      Long supplierId,
      BigDecimal paymentAmount,
      Long companyId,
      int rowNumber,
      List<ImportRowErrorDTO> rowErrors) {
    List<PaymentAllocationRequest> allocations = new ArrayList<>();
    String[] billNumbers = billNumbersStr.split(",");

    BigDecimal totalAllocated = BigDecimal.ZERO;
    for (String billNumber : billNumbers) {
      billNumber = billNumber.trim();
      if (billNumber.isEmpty()) {
        continue;
      }

      // Find bill by number (search in company-scoped bills)
      final String billNumberFinal = billNumber; // Make effectively final for lambda
      List<PurchaseBill> bills = purchaseBillRepository.findByCompanyId(companyId);
      Optional<PurchaseBill> billOpt = bills.stream()
          .filter(b -> b.getBillNumber() != null && b.getBillNumber().equalsIgnoreCase(billNumberFinal))
          .findFirst();
      if (billOpt.isEmpty()) {
        rowErrors.add(new ImportRowErrorDTO(rowNumber, "billNumbers", "Bill not found: " + billNumber));
        continue;
      }

      PurchaseBill bill = billOpt.get();
      if (!bill.getSupplierId().equals(supplierId)) {
        rowErrors.add(new ImportRowErrorDTO(rowNumber, "billNumbers", "Bill " + billNumber + " does not belong to supplier"));
        continue;
      }

      // Calculate remaining balance (simplified - in real system, use service)
      BigDecimal remainingBalance = bill.getTotalAmount(); // TODO: Calculate actual remaining balance
      BigDecimal allocatedAmount = remainingBalance.min(paymentAmount.subtract(totalAllocated));

      PaymentAllocationRequest allocation = new PaymentAllocationRequest();
      allocation.setPurchaseBillId(bill.getId());
      allocation.setAllocatedAmount(allocatedAmount);
      allocations.add(allocation);

      totalAllocated = totalAllocated.add(allocatedAmount);
      if (totalAllocated.compareTo(paymentAmount) >= 0) {
        break; // Fully allocated
      }
    }

    // Validate total allocated amount matches payment amount
    if (totalAllocated.compareTo(paymentAmount) != 0) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, "billNumbers", 
          "Total allocated amount (" + totalAllocated + ") does not match payment amount (" + paymentAmount + ")"));
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


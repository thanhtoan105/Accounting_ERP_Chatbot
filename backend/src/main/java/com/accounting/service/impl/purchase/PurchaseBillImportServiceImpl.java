package com.accounting.service.impl.purchase;

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

import com.accounting.dto.ImportResultDTO;
import com.accounting.dto.ImportRowErrorDTO;
import com.accounting.dto.PurchaseBillCreateRequest;
import com.accounting.dto.PurchaseBillLineDTO;
import com.accounting.dto.PurchaseBillValidationResult;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.Supplier;
import com.accounting.entity.VatRate;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.ImportErrorReportRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.PurchaseBillImportService;
import com.accounting.service.PurchaseBillService;
import com.accounting.service.PurchaseBillValidationService;
import com.accounting.service.SupplierService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Implementation of PurchaseBillImportService for Excel batch import.
 * Supports Excel (.xlsx, .xls) format with validated template.
 * Format: Each row represents one purchase bill with up to 5 line items.
 * Columns: Supplier Code, Bill Number, Bill Date, Due Date, Reference, Description,
 * Account Code 1, Description 1, Quantity 1, Unit Price 1, VAT Rate 1,
 * Account Code 2, Description 2, Quantity 2, Unit Price 2, VAT Rate 2, ... (up to 5 line items)
 */
@Service
@Transactional
public class PurchaseBillImportServiceImpl implements PurchaseBillImportService {

  private static final Logger logger = LoggerFactory.getLogger(PurchaseBillImportServiceImpl.class);

  private static final int MAX_LINE_ITEMS = 5;
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final PurchaseBillService purchaseBillService;
  private final PurchaseBillValidationService purchaseBillValidationService;
  private final SupplierService supplierService;
  private final SupplierRepository supplierRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final ImportErrorReportRepository importErrorReportRepository;
  private final AuditService auditService;

  @PersistenceContext
  private EntityManager entityManager;

  public PurchaseBillImportServiceImpl(
      PurchaseBillService purchaseBillService,
      PurchaseBillValidationService purchaseBillValidationService,
      SupplierService supplierService,
      SupplierRepository supplierRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      ImportErrorReportRepository importErrorReportRepository,
      AuditService auditService) {
    this.purchaseBillService = purchaseBillService;
    this.purchaseBillValidationService = purchaseBillValidationService;
    this.supplierService = supplierService;
    this.supplierRepository = supplierRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.importErrorReportRepository = importErrorReportRepository;
    this.auditService = auditService;
  }

  @Override
  public ImportResultDTO importBills(MultipartFile file) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    List<ImportRowErrorDTO> errors = new ArrayList<>();
    List<PurchaseBillCreateRequest> validRequests = new ArrayList<>();
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
        PurchaseBillCreateRequest request = new PurchaseBillCreateRequest();
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

        // Parse bill number (required, column 1)
        String billNumber = getCellValueAsString(row.getCell(1));
        if (billNumber == null || billNumber.isBlank()) {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "billNumber", "Bill number is required"));
        } else {
          request.setBillNumber(billNumber.trim());
        }

        // Parse bill date (required, column 2)
        LocalDate billDate = parseDate(row.getCell(2), rowNumber, "billDate", rowErrors);
        if (billDate != null) {
          request.setBillDate(billDate);
        }

        // Parse due date (required, column 3)
        LocalDate dueDate = parseDate(row.getCell(3), rowNumber, "dueDate", rowErrors);
        if (dueDate != null) {
          request.setDueDate(dueDate);
        }

        // Parse reference (required, column 4)
        String reference = getCellValueAsString(row.getCell(4));
        if (reference == null || reference.isBlank()) {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "reference", "Reference is required"));
        } else {
          if (reference.length() > 100) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "reference", "Reference must not exceed 100 characters"));
          } else {
            request.setReference(reference.trim());
          }
        }

        // Parse description (optional, column 5)
        String description = getCellValueAsString(row.getCell(5));
        if (description != null && !description.isBlank()) {
          if (description.length() > 500) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "description", "Description must not exceed 500 characters"));
          } else {
            request.setDescription(description.trim());
          }
        }

        // Parse line items (columns 6-30: 5 line items × 5 columns each)
        List<PurchaseBillLineDTO> lines = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < MAX_LINE_ITEMS; lineIndex++) {
          int baseColumn = 6 + (lineIndex * 5); // Account Code, Description, Quantity, Unit Price, VAT Rate

          String accountCode = getCellValueAsString(row.getCell(baseColumn));
          if (accountCode == null || accountCode.isBlank()) {
            continue; // Skip empty line items
          }

          PurchaseBillLineDTO line = new PurchaseBillLineDTO();
          line.setLineNumber(lineIndex + 1);

          // Parse account code (required)
          Long accountId = findAccountByCode(accountCode.trim(), companyId, rowNumber, baseColumn, rowErrors, accountCache);
          if (accountId != null) {
            line.setAccountId(accountId);
          }

          // Parse description (required)
          String lineDescription = getCellValueAsString(row.getCell(baseColumn + 1));
          if (lineDescription == null || lineDescription.isBlank()) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "line" + (lineIndex + 1) + ".description", "Line item description is required"));
          } else {
            if (lineDescription.length() > 500) {
              rowErrors.add(new ImportRowErrorDTO(rowNumber, "line" + (lineIndex + 1) + ".description", "Description must not exceed 500 characters"));
            } else {
              line.setDescription(lineDescription.trim());
            }
          }

          // Parse quantity (required, default 1)
          BigDecimal quantity = parseBigDecimal(row.getCell(baseColumn + 2), rowNumber, "line" + (lineIndex + 1) + ".quantity", rowErrors);
          if (quantity == null) {
            quantity = BigDecimal.ONE;
          } else if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "line" + (lineIndex + 1) + ".quantity", "Quantity must be non-negative"));
            quantity = BigDecimal.ONE;
          }
          line.setQuantity(quantity);

          // Parse unit price (required)
          BigDecimal unitPrice = parseBigDecimal(row.getCell(baseColumn + 3), rowNumber, "line" + (lineIndex + 1) + ".unitPrice", rowErrors);
          if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            rowErrors.add(new ImportRowErrorDTO(rowNumber, "line" + (lineIndex + 1) + ".unitPrice", "Unit price is required and must be positive"));
          } else {
            line.setUnitPrice(unitPrice);
          }

          // Parse VAT rate (required, default ZERO)
          String vatRateStr = getCellValueAsString(row.getCell(baseColumn + 4));
          VatRate vatRate = parseVatRate(vatRateStr, rowNumber, "line" + (lineIndex + 1) + ".vatRate", rowErrors);
          line.setVatRate(vatRate != null ? vatRate : VatRate.ZERO);

          // Calculate amount and VAT amount
          if (unitPrice != null && quantity != null && vatRate != null) {
            BigDecimal amount = quantity.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
            line.setAmount(amount);
            BigDecimal vatAmount = amount.multiply(vatRate.getRate()).setScale(2, java.math.RoundingMode.HALF_UP);
            line.setVatAmount(vatAmount);
          }

          lines.add(line);
        }

        if (lines.isEmpty()) {
          rowErrors.add(new ImportRowErrorDTO(rowNumber, "lines", "At least one line item is required"));
        } else {
          request.setLines(lines);
        }

        // Validate the request
        if (rowErrors.isEmpty() && request.getSupplierId() != null && request.getBillDate() != null && request.getDueDate() != null) {
          PurchaseBillValidationResult validationResult = purchaseBillValidationService.validate(request, null);
          if (!validationResult.isValid()) {
            // Add validation errors to row errors
            validationResult.getHeaderErrors().forEach((field, fieldErrors) -> {
              fieldErrors.forEach(error -> rowErrors.add(new ImportRowErrorDTO(rowNumber, field, error)));
            });
            validationResult.getLineErrors().forEach((lineNum, lineErrors) -> {
              lineErrors.forEach((field, fieldErrors) -> {
                fieldErrors.forEach(error -> rowErrors.add(new ImportRowErrorDTO(rowNumber, "line" + lineNum + "." + field, error)));
              });
            });
          }
        }

        if (rowErrors.isEmpty()) {
          request.setStatus(PurchaseBillStatus.DRAFT);
          validRequests.add(request);
        } else {
          errors.addAll(rowErrors);
        }
      }

      workbook.close();

      // Save all valid rows (atomic transaction: all valid rows or none)
      int successCount = 0;
      for (int i = 0; i < validRequests.size(); i++) {
        PurchaseBillCreateRequest request = validRequests.get(i);
        try {
          purchaseBillService.create(request);
          successCount++;

          // Log audit entry for imported row (individual bill creation is logged by PurchaseBillService.create)
          try {
            logger.debug("Imported purchase bill: {} (row {})", request.getBillNumber(), i + 2);
          } catch (Exception e) {
            logger.warn("Failed to log audit entry for imported purchase bill: {}", e.getMessage());
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
        auditService.logPurchaseBillImport(successCount, errors.size(), importedByUserId, null);
      } catch (Exception e) {
        // Non-blocking: log error but don't break main flow
        logger.error("Failed to log purchase bill import audit event: {}", e.getMessage(), e);
      }

      return new ImportResultDTO(successCount, 0, errors.size(), errors, errorReportId);

    } catch (Exception e) {
      logger.error("Failed to import purchase bills: {}", e.getMessage(), e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to import purchase bills: " + e.getMessage());
    }
  }

  @Override
  public byte[] generateTemplate() {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Purchase Bills");

      // Create header row
      Row headerRow = sheet.createRow(0);
      String[] headers = {
        "Supplier Code",
        "Bill Number",
        "Bill Date (YYYY-MM-DD)",
        "Due Date (YYYY-MM-DD)",
        "Reference",
        "Description",
        // Line Item 1
        "Account Code 1",
        "Description 1",
        "Quantity 1",
        "Unit Price 1",
        "VAT Rate 1 (ZERO/FIVE/TEN/EXEMPT)",
        // Line Item 2
        "Account Code 2",
        "Description 2",
        "Quantity 2",
        "Unit Price 2",
        "VAT Rate 2",
        // Line Item 3
        "Account Code 3",
        "Description 3",
        "Quantity 3",
        "Unit Price 3",
        "VAT Rate 3",
        // Line Item 4
        "Account Code 4",
        "Description 4",
        "Quantity 4",
        "Unit Price 4",
        "VAT Rate 4",
        // Line Item 5
        "Account Code 5",
        "Description 5",
        "Quantity 5",
        "Unit Price 5",
        "VAT Rate 5"
      };

      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
      }

      // Create example row
      Row exampleRow = sheet.createRow(1);
      exampleRow.createCell(0).setCellValue("SUP001");
      exampleRow.createCell(1).setCellValue("BILL-001");
      exampleRow.createCell(2).setCellValue("2025-01-15");
      exampleRow.createCell(3).setCellValue("2025-02-14");
      exampleRow.createCell(4).setCellValue("Invoice #12345");
      exampleRow.createCell(5).setCellValue("Office supplies");
      exampleRow.createCell(6).setCellValue("642");
      exampleRow.createCell(7).setCellValue("Office supplies");
      exampleRow.createCell(8).setCellValue("10");
      exampleRow.createCell(9).setCellValue("100000");
      exampleRow.createCell(10).setCellValue("TEN");

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
      "Bill Number",
      "Bill Date (YYYY-MM-DD)",
      "Due Date (YYYY-MM-DD)",
      "Reference",
      "Description"
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

    // Auto-create draft supplier pending confirmation
    try {
      com.accounting.dto.SupplierCreateRequest supplierRequest = new com.accounting.dto.SupplierCreateRequest();
      supplierRequest.setCode(supplierCode);
      supplierRequest.setName(supplierCode); // Use code as name temporarily
      supplierRequest.setActive(false); // Draft supplier, inactive until confirmed
      com.accounting.dto.SupplierDTO supplier = supplierService.create(supplierRequest);
      Long supplierId = supplier.getId();
      cache.put(supplierCode, supplierId);
      logger.info("Auto-created draft supplier: {} (pending confirmation)", supplierCode);
      return supplierId;
    } catch (Exception e) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, "supplierCode", "Supplier not found and failed to create: " + e.getMessage()));
      return null;
    }
  }

  private Long findAccountByCode(
      String accountCode,
      Long companyId,
      int rowNumber,
      int column,
      List<ImportRowErrorDTO> rowErrors,
      Map<String, Long> cache) {
    if (cache.containsKey(accountCode)) {
      return cache.get(accountCode);
    }

    Optional<ChartOfAccount> accountOpt = chartOfAccountsRepository.findByCompanyIdAndCode(companyId, accountCode);
    if (accountOpt.isPresent()) {
      Long accountId = accountOpt.get().getId();
      cache.put(accountCode, accountId);
      return accountId;
    }

    rowErrors.add(new ImportRowErrorDTO(rowNumber, "accountCode", "Account not found: " + accountCode));
    return null;
  }

  private LocalDate parseDate(Cell cell, int rowNumber, String fieldName, List<ImportRowErrorDTO> rowErrors) {
    if (cell == null) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, fieldName, fieldName + " is required"));
      return null;
    }

    String dateStr = getCellValueAsString(cell);
    if (dateStr == null || dateStr.isBlank()) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, fieldName, fieldName + " is required"));
      return null;
    }

    try {
      return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
    } catch (DateTimeParseException e) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, fieldName, "Invalid date format: expected YYYY-MM-DD"));
      return null;
    }
  }

  private BigDecimal parseBigDecimal(Cell cell, int rowNumber, String fieldName, List<ImportRowErrorDTO> rowErrors) {
    if (cell == null) {
      return null;
    }

    try {
      if (cell.getCellType() == CellType.NUMERIC) {
        return BigDecimal.valueOf(cell.getNumericCellValue());
      } else if (cell.getCellType() == CellType.STRING) {
        String value = cell.getStringCellValue().trim();
        if (value.isEmpty()) {
          return null;
        }
        return new BigDecimal(value);
      }
    } catch (NumberFormatException e) {
      rowErrors.add(new ImportRowErrorDTO(rowNumber, fieldName, "Invalid number format"));
      return null;
    }

    return null;
  }

  private VatRate parseVatRate(String vatRateStr, int rowNumber, String fieldName, List<ImportRowErrorDTO> rowErrors) {
    if (vatRateStr == null || vatRateStr.isBlank()) {
      return VatRate.ZERO;
    }

    String upper = vatRateStr.trim().toUpperCase();
    try {
      return VatRate.valueOf(upper);
    } catch (IllegalArgumentException e) {
      // Try to parse as percentage
      if (upper.equals("0") || upper.equals("0%")) {
        return VatRate.ZERO;
      } else if (upper.equals("5") || upper.equals("5%")) {
        return VatRate.FIVE;
      } else if (upper.equals("10") || upper.equals("10%")) {
        return VatRate.TEN;
      } else if (upper.equals("EXEMPT")) {
        return VatRate.EXEMPT;
      }
      rowErrors.add(new ImportRowErrorDTO(rowNumber, fieldName, "Invalid VAT rate: must be ZERO, FIVE, TEN, or EXEMPT"));
      return VatRate.ZERO;
    }
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) {
      return null;
    }
    if (cell.getCellType() == CellType.STRING) {
      return cell.getStringCellValue();
    } else if (cell.getCellType() == CellType.NUMERIC) {
      // Check if it's a date
      if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
        return cell.getDateCellValue().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER);
      }
      // Otherwise, return as integer if whole number, else as decimal
      double numValue = cell.getNumericCellValue();
      if (numValue == (long) numValue) {
        return String.valueOf((long) numValue);
      }
      return String.valueOf(numValue);
    } else if (cell.getCellType() == CellType.BOOLEAN) {
      return String.valueOf(cell.getBooleanCellValue());
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
    UUID errorReportId = null;
    // TODO: Create error report entity and save

    // Log import audit event (all failed)
    try {
      Long importedByUserId = SecurityUtils.getCurrentUserId();
      auditService.logPurchaseBillImport(0, errors.size(), importedByUserId, null);
    } catch (Exception e) {
      // Non-blocking: log error but don't break main flow
      logger.error("Failed to log purchase bill import audit event: {}", e.getMessage(), e);
    }

    return new ImportResultDTO(0, 0, errors.size(), errors, errorReportId);
  }
}

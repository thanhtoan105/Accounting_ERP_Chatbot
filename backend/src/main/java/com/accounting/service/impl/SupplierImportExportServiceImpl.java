package com.accounting.service.impl;

import com.accounting.dto.SupplierCreateRequest;
import com.accounting.dto.SupplierDTO;
import com.accounting.service.SupplierImportExportService;
import com.accounting.service.SupplierService;
import com.accounting.service.util.SupplierCodeGenerator;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Implementation of SupplierImportExportService for Excel/CSV import/export.
 */
@Service
@Transactional
public class SupplierImportExportServiceImpl implements SupplierImportExportService {

  private final SupplierService supplierService;
  private final SupplierCodeGenerator codeGenerator;

  public SupplierImportExportServiceImpl(
      SupplierService supplierService, SupplierCodeGenerator codeGenerator) {
    this.supplierService = supplierService;
    this.codeGenerator = codeGenerator;
  }

  @Override
  public void exportSuppliers(List<SupplierDTO> suppliers, String format, OutputStream outputStream) {
    try {
      if ("csv".equalsIgnoreCase(format)) {
        exportToCSV(suppliers, outputStream);
      } else {
        exportToExcel(suppliers, outputStream);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to export suppliers: " + e.getMessage(), e);
    }
  }

  @Override
  public ImportResult importSuppliers(InputStream inputStream, String filename) {
    List<ImportError> errors = new ArrayList<>();
    List<SupplierCreateRequest> validRequests = new ArrayList<>();
    int rowNumber = 0;

    try {
      String format = detectFormat(filename);

      if ("csv".equalsIgnoreCase(format)) {
        parseCSV(inputStream, validRequests, errors);
      } else {
        parseExcel(inputStream, validRequests, errors);
      }

      // Save all valid rows (atomic transaction: all valid rows or none)
      // If any row fails during save (e.g., duplicate), rollback the entire
      // transaction
      int successCount = 0;
      for (int i = 0; i < validRequests.size(); i++) {
        SupplierCreateRequest request = validRequests.get(i);
        try {
          supplierService.create(request);
          successCount++;
        } catch (Exception e) {
          // If any save fails, add error and mark transaction for rollback
          // Calculate row number: index + 2 (header row + 1-based)
          int errorRowNumber = i + 2;
          String errorMessage = e.getMessage();
          if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = e.getClass().getSimpleName();
          }
          // Extract reason from ResponseStatusException if present
          if (e instanceof org.springframework.web.server.ResponseStatusException) {
            org.springframework.web.server.ResponseStatusException rse = (org.springframework.web.server.ResponseStatusException) e;
            errorMessage = rse.getReason() != null ? rse.getReason() : errorMessage;
          }
          errors.add(new ImportError(errorRowNumber, "general", errorMessage));
          // Mark transaction for rollback without throwing exception
          // This allows controller to return 200 with error details
          TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
          // Return early with errors - transaction will rollback all saves
          return new ImportResult(0, errors.size(), errors);
        }
      }

      return new ImportResult(successCount, errors.size(), errors);
    } catch (Exception e) {
      throw new RuntimeException("Failed to import suppliers: " + e.getMessage(), e);
    }
  }

  private void exportToExcel(List<SupplierDTO> suppliers, OutputStream outputStream) throws Exception {
    Workbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("Suppliers");

    // Create header row
    Row headerRow = sheet.createRow(0);
    String[] headers = { "Code", "Name", "Tax Code", "Email", "Phone", "Address", "Active" };
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
    }

    // Create data rows
    int rowNum = 1;
    for (SupplierDTO supplier : suppliers) {
      Row row = sheet.createRow(rowNum++);
      row.createCell(0).setCellValue(supplier.getCode() != null ? supplier.getCode() : "");
      row.createCell(1).setCellValue(supplier.getName() != null ? supplier.getName() : "");
      row.createCell(2).setCellValue(supplier.getTaxCode() != null ? supplier.getTaxCode() : "");
      row.createCell(3).setCellValue(supplier.getEmail() != null ? supplier.getEmail() : "");
      row.createCell(4).setCellValue(supplier.getPhone() != null ? supplier.getPhone() : "");
      row.createCell(5).setCellValue(supplier.getAddress() != null ? supplier.getAddress() : "");
      row.createCell(6).setCellValue(supplier.getActive() != null ? supplier.getActive() : true);
    }

    workbook.write(outputStream);
    workbook.close();
  }

  private void exportToCSV(List<SupplierDTO> suppliers, OutputStream outputStream) throws Exception {
    PrintWriter writer = new PrintWriter(new java.io.OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

    // Write header
    writer.println("Code,Name,Tax Code,Email,Phone,Address,Active");

    // Write data rows
    for (SupplierDTO supplier : suppliers) {
      writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
          escapeCSV(supplier.getCode()),
          escapeCSV(supplier.getName()),
          escapeCSV(supplier.getTaxCode()),
          escapeCSV(supplier.getEmail()),
          escapeCSV(supplier.getPhone()),
          escapeCSV(supplier.getAddress()),
          supplier.getActive() != null ? supplier.getActive() : true);
    }

    writer.flush();
  }

  private String escapeCSV(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private void parseExcel(InputStream inputStream, List<SupplierCreateRequest> validRequests, List<ImportError> errors)
      throws Exception {
    Workbook workbook = WorkbookFactory.create(inputStream);
    Sheet sheet = workbook.getSheetAt(0);

    // Skip header row (row 0)
    for (int i = 1; i <= sheet.getLastRowNum(); i++) {
      Row row = sheet.getRow(i);
      if (row == null) {
        continue;
      }

      SupplierCreateRequest request = new SupplierCreateRequest();
      List<ImportError> rowErrors = new ArrayList<>();

      // Parse Code (optional, column 0)
      String code = getCellValueAsString(row.getCell(0));
      if (code != null && !code.isBlank()) {
        request.setCode(code.trim());
      }

      // Parse Name (required, column 1)
      String name = getCellValueAsString(row.getCell(1));
      if (name == null || name.isBlank()) {
        rowErrors.add(new ImportError(i + 1, "name", "Name is required"));
      } else {
        request.setName(name.trim());
      }

      // Parse Tax Code (optional)
      String taxCode = getCellValueAsString(row.getCell(2));
      if (taxCode != null && !taxCode.isBlank()) {
        if (!taxCode.matches("^\\d{10}$")) {
          rowErrors.add(new ImportError(i + 1, "taxCode", "Tax code must be exactly 10 digits"));
        } else {
          request.setTaxCode(taxCode.trim());
        }
      }

      // Parse Email (optional)
      String email = getCellValueAsString(row.getCell(3));
      if (email != null && !email.isBlank()) {
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
          rowErrors.add(new ImportError(i + 1, "email", "Invalid email format"));
        } else {
          request.setEmail(email.trim());
        }
      }

      // Parse Phone (optional)
      String phone = getCellValueAsString(row.getCell(4));
      if (phone != null && !phone.isBlank()) {
        if (!phone.matches("^[0-9+\\-() ]+$")) {
          rowErrors.add(new ImportError(i + 1, "phone", "Invalid phone format"));
        } else {
          request.setPhone(phone.trim());
        }
      }

      // Parse Address (optional)
      String address = getCellValueAsString(row.getCell(5));
      if (address != null && !address.isBlank()) {
        request.setAddress(address.trim());
      }

      // Parse Active (optional, default true)
      String activeStr = getCellValueAsString(row.getCell(6));
      if (activeStr != null && !activeStr.isBlank()) {
        request.setActive(Boolean.parseBoolean(activeStr.trim()));
      } else {
        request.setActive(true);
      }

      if (rowErrors.isEmpty()) {
        validRequests.add(request);
      } else {
        errors.addAll(rowErrors);
      }
    }

    workbook.close();
  }

  private void parseCSV(InputStream inputStream, List<SupplierCreateRequest> validRequests, List<ImportError> errors)
      throws Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    String line;
    int rowNumber = 0;

    // Skip header row
    reader.readLine();
    rowNumber++;

    while ((line = reader.readLine()) != null) {
      rowNumber++;
      String[] values = parseCSVLine(line);

      if (values.length < 2) {
        errors.add(new ImportError(rowNumber, "general", "Insufficient columns"));
        continue;
      }

      SupplierCreateRequest request = new SupplierCreateRequest();
      List<ImportError> rowErrors = new ArrayList<>();

      // Parse Code (optional, column 0)
      if (values.length > 0 && !values[0].trim().isBlank()) {
        request.setCode(values[0].trim());
      }

      // Parse Name (required, column 1)
      String name = values.length > 1 ? values[1].trim() : "";
      if (name.isBlank()) {
        rowErrors.add(new ImportError(rowNumber, "name", "Name is required"));
      } else {
        request.setName(name);
      }

      // Parse Tax Code (optional, column 2)
      if (values.length > 2 && !values[2].trim().isBlank()) {
        String taxCode = values[2].trim();
        if (!taxCode.matches("^\\d{10}$")) {
          rowErrors.add(new ImportError(rowNumber, "taxCode", "Tax code must be exactly 10 digits"));
        } else {
          request.setTaxCode(taxCode);
        }
      }

      // Parse Email (optional, column 3)
      if (values.length > 3 && !values[3].trim().isBlank()) {
        String email = values[3].trim();
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
          rowErrors.add(new ImportError(rowNumber, "email", "Invalid email format"));
        } else {
          request.setEmail(email);
        }
      }

      // Parse Phone (optional, column 4)
      if (values.length > 4 && !values[4].trim().isBlank()) {
        String phone = values[4].trim();
        if (!phone.matches("^[0-9+\\-() ]+$")) {
          rowErrors.add(new ImportError(rowNumber, "phone", "Invalid phone format"));
        } else {
          request.setPhone(phone);
        }
      }

      // Parse Address (optional, column 5)
      if (values.length > 5 && !values[5].trim().isBlank()) {
        request.setAddress(values[5].trim());
      }

      // Parse Active (optional, column 6, default true)
      if (values.length > 6 && !values[6].trim().isBlank()) {
        request.setActive(Boolean.parseBoolean(values[6].trim()));
      } else {
        request.setActive(true);
      }

      if (rowErrors.isEmpty()) {
        validRequests.add(request);
      } else {
        errors.addAll(rowErrors);
      }
    }
  }

  private String[] parseCSVLine(String line) {
    List<String> values = new ArrayList<>();
    boolean inQuotes = false;
    StringBuilder current = new StringBuilder();

    for (char c : line.toCharArray()) {
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        values.add(current.toString());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    values.add(current.toString());
    return values.toArray(new String[0]);
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) {
      return null;
    }
    if (cell.getCellType() == CellType.STRING) {
      return cell.getStringCellValue();
    } else if (cell.getCellType() == CellType.NUMERIC) {
      return String.valueOf((long) cell.getNumericCellValue());
    } else if (cell.getCellType() == CellType.BOOLEAN) {
      return String.valueOf(cell.getBooleanCellValue());
    }
    return null;
  }

  private String detectFormat(String filename) {
    if (filename == null) {
      return "xlsx";
    }
    String lower = filename.toLowerCase();
    if (lower.endsWith(".csv")) {
      return "csv";
    } else if (lower.endsWith(".xls")) {
      return "xls";
    }
    return "xlsx";
  }
}

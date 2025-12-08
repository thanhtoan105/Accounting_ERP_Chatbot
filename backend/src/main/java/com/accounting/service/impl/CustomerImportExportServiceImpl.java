package com.accounting.service.impl;

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

import com.accounting.dto.CustomerCreateRequest;
import com.accounting.dto.CustomerDTO;
import com.accounting.service.CustomerImportExportService;
import com.accounting.service.CustomerService;
import com.accounting.service.util.CustomerCodeGenerator;

/**
 * Implementation of CustomerImportExportService for Excel/CSV import/export.
 */
@Service
@Transactional
public class CustomerImportExportServiceImpl implements CustomerImportExportService {

  private final CustomerService customerService;
  private final CustomerCodeGenerator codeGenerator;

  public CustomerImportExportServiceImpl(
      CustomerService customerService, CustomerCodeGenerator codeGenerator) {
    this.customerService = customerService;
    this.codeGenerator = codeGenerator;
  }

  @Override
  public void exportCustomers(List<CustomerDTO> customers, String format, OutputStream outputStream) {
    try {
      if ("csv".equalsIgnoreCase(format)) {
        exportToCSV(customers, outputStream);
      } else {
        exportToExcel(customers, outputStream);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to export customers: " + e.getMessage(), e);
    }
  }

  @Override
  public ImportResult importCustomers(InputStream inputStream, String filename) {
    List<ImportError> errors = new ArrayList<>();
    List<CustomerCreateRequest> validRequests = new ArrayList<>();
    int rowNumber = 0;

    try {
      String format = detectFormat(filename);
      
      if ("csv".equalsIgnoreCase(format)) {
        parseCSV(inputStream, validRequests, errors);
      } else {
        parseExcel(inputStream, validRequests, errors);
      }

      // Validate all rows before saving (atomic transaction)
      if (!errors.isEmpty()) {
        return new ImportResult(0, errors.size(), errors);
      }

      // Save all valid rows
      int successCount = 0;
      for (CustomerCreateRequest request : validRequests) {
        try {
          customerService.create(request);
          successCount++;
        } catch (Exception e) {
          errors.add(new ImportError(validRequests.indexOf(request) + 2, "general", e.getMessage()));
        }
      }

      return new ImportResult(successCount, errors.size(), errors);
    } catch (Exception e) {
      throw new RuntimeException("Failed to import customers: " + e.getMessage(), e);
    }
  }

  private void exportToExcel(List<CustomerDTO> customers, OutputStream outputStream) throws Exception {
    Workbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("Customers");

    // Create header row
    Row headerRow = sheet.createRow(0);
    String[] headers = {"Code", "Name", "Tax Code", "Email", "Phone", "Address", "Active"};
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
    }

    // Create data rows
    int rowNum = 1;
    for (CustomerDTO customer : customers) {
      Row row = sheet.createRow(rowNum++);
      row.createCell(0).setCellValue(customer.getCode() != null ? customer.getCode() : "");
      row.createCell(1).setCellValue(customer.getName() != null ? customer.getName() : "");
      row.createCell(2).setCellValue(customer.getTaxCode() != null ? customer.getTaxCode() : "");
      row.createCell(3).setCellValue(customer.getEmail() != null ? customer.getEmail() : "");
      row.createCell(4).setCellValue(customer.getPhone() != null ? customer.getPhone() : "");
      row.createCell(5).setCellValue(customer.getAddress() != null ? customer.getAddress() : "");
      row.createCell(6).setCellValue(customer.getActive() != null ? customer.getActive() : true);
    }

    workbook.write(outputStream);
    workbook.close();
  }

  private void exportToCSV(List<CustomerDTO> customers, OutputStream outputStream) throws Exception {
    PrintWriter writer = new PrintWriter(new java.io.OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    
    // Write header
    writer.println("Code,Name,Tax Code,Email,Phone,Address,Active");
    
    // Write data rows
    for (CustomerDTO customer : customers) {
      writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
          escapeCSV(customer.getCode()),
          escapeCSV(customer.getName()),
          escapeCSV(customer.getTaxCode()),
          escapeCSV(customer.getEmail()),
          escapeCSV(customer.getPhone()),
          escapeCSV(customer.getAddress()),
          customer.getActive() != null ? customer.getActive() : true);
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

  private void parseExcel(InputStream inputStream, List<CustomerCreateRequest> validRequests, List<ImportError> errors) throws Exception {
    Workbook workbook = WorkbookFactory.create(inputStream);
    Sheet sheet = workbook.getSheetAt(0);

    // Skip header row (row 0)
    for (int i = 1; i <= sheet.getLastRowNum(); i++) {
      Row row = sheet.getRow(i);
      if (row == null) {
        continue;
      }

      CustomerCreateRequest request = new CustomerCreateRequest();
      List<ImportError> rowErrors = new ArrayList<>();

      // Parse Name (required)
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

  private void parseCSV(InputStream inputStream, List<CustomerCreateRequest> validRequests, List<ImportError> errors) throws Exception {
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

      CustomerCreateRequest request = new CustomerCreateRequest();
      List<ImportError> rowErrors = new ArrayList<>();

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

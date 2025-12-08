package com.accounting.service.impl.ar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.accounting.dto.ARStatementDetailedDTO;
import com.accounting.dto.ARStatementSummaryDTO;
import com.accounting.entity.ARStatementHistory;
import com.accounting.entity.Company;
import com.accounting.repository.CompanyRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ARStatementExportService;

/**
 * Implementation of ARStatementExportService for PDF/Excel export.
 */
@Service
public class ARStatementExportServiceImpl implements ARStatementExportService {

  private static final Logger logger = LoggerFactory.getLogger(ARStatementExportServiceImpl.class);
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final CompanyRepository companyRepository;

  public ARStatementExportServiceImpl(CompanyRepository companyRepository) {
    this.companyRepository = companyRepository;
  }

  @Override
  public byte[] exportStatement(
      Object statement, String format, ARStatementHistory.StatementFormat statementFormat) {
    logger.info("Exporting AR statement to {} format", format);

    if ("EXCEL".equalsIgnoreCase(format)) {
      return exportToExcel(statement, statementFormat);
    } else if ("PDF".equalsIgnoreCase(format)) {
      return exportToPDF(statement, statementFormat);
    } else {
      throw new IllegalArgumentException("Unsupported export format: " + format);
    }
  }

  @Override
  public byte[] batchExportStatements(
      List<Long> customerIds,
      String format,
      ARStatementHistory.StatementFormat statementFormat,
      LocalDate asOfDate) {
    logger.info("Batch exporting {} statements to {} format", customerIds.size(), format);

    try (ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(zipOut)) {

      // Note: This is a stub - in production, you would:
      // 1. Generate individual statements for each customer
      // 2. Add each as a separate file in the ZIP
      // 3. Name files: "Statement_{CustomerCode}_{Date}.{ext}"

      ZipEntry entry = new ZipEntry("statements_batch.zip");
      zos.putNextEntry(entry);
      zos.write("Batch export placeholder".getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      return zipOut.toByteArray();
    } catch (IOException e) {
      logger.error("Failed to create batch export ZIP", e);
      throw new RuntimeException("Failed to create batch export", e);
    }
  }

  private byte[] exportToExcel(Object statement, ARStatementHistory.StatementFormat format) {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Customer Statement");

      // Create styles
      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle currencyStyle = createCurrencyStyle(workbook);
      CellStyle dateStyle = createDateStyle(workbook);

      int rowNum = 0;

      // Get company info for header
      Company company = getCompany();
      if (company != null) {
        Row companyRow = sheet.createRow(rowNum++);
        companyRow.createCell(0).setCellValue(company.getName());
        companyRow.getCell(0).setCellStyle(headerStyle);
        rowNum++;
      }

      if (format == ARStatementHistory.StatementFormat.SUMMARY) {
        exportSummaryToExcel((ARStatementSummaryDTO) statement, sheet, rowNum, headerStyle, currencyStyle, dateStyle);
      } else {
        exportDetailedToExcel((ARStatementDetailedDTO) statement, sheet, rowNum, headerStyle, currencyStyle, dateStyle);
      }

      // Auto-size columns
      for (int i = 0; i < 7; i++) {
        sheet.autoSizeColumn(i);
      }

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      logger.error("Failed to export statement to Excel", e);
      throw new RuntimeException("Failed to export to Excel", e);
    }
  }

  private void exportSummaryToExcel(
      ARStatementSummaryDTO statement,
      Sheet sheet,
      int startRow,
      CellStyle headerStyle,
      CellStyle currencyStyle,
      CellStyle dateStyle) {
    int rowNum = startRow;

    // Title
    Row titleRow = sheet.createRow(rowNum++);
    titleRow.createCell(0).setCellValue("CUSTOMER STATEMENT - SUMMARY");
    titleRow.getCell(0).setCellStyle(headerStyle);

    // Customer info
    rowNum++;
    createRow(sheet, rowNum++, "Customer:", statement.getCustomerName());
    createRow(sheet, rowNum++, "Code:", statement.getCustomerCode());
    if (statement.getCustomerAddress() != null) {
      createRow(sheet, rowNum++, "Address:", statement.getCustomerAddress());
    }
    if (statement.getCustomerTaxCode() != null) {
      createRow(sheet, rowNum++, "Tax Code:", statement.getCustomerTaxCode());
    }
    createRow(sheet, rowNum++, "As of Date:", DATE_FORMATTER.format(statement.getAsOfDate()));

    // Table header
    rowNum++;
    Row headerRow = sheet.createRow(rowNum++);
    String[] headers = {"Invoice #", "Date", "Invoice Amount", "Amount Paid", "Balance", "Running Balance"};
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // Data rows
    for (ARStatementSummaryDTO.StatementInvoiceDTO invoice : statement.getInvoices()) {
      Row dataRow = sheet.createRow(rowNum++);
      dataRow.createCell(0).setCellValue(invoice.getInvoiceNumber());
      Cell dateCell = dataRow.createCell(1);
      dateCell.setCellValue(invoice.getInvoiceDate());
      dateCell.setCellStyle(dateStyle);
      Cell amountCell = dataRow.createCell(2);
      amountCell.setCellValue(invoice.getInvoiceAmount().doubleValue());
      amountCell.setCellStyle(currencyStyle);
      Cell paidCell = dataRow.createCell(3);
      paidCell.setCellValue(invoice.getAmountPaid().doubleValue());
      paidCell.setCellStyle(currencyStyle);
      Cell balanceCell = dataRow.createCell(4);
      balanceCell.setCellValue(invoice.getBalance().doubleValue());
      balanceCell.setCellStyle(currencyStyle);
      Cell runningBalanceCell = dataRow.createCell(5);
      runningBalanceCell.setCellValue(invoice.getRunningBalance().doubleValue());
      runningBalanceCell.setCellStyle(currencyStyle);
    }

    // Totals
    rowNum++;
    createRow(sheet, rowNum++, "Total Invoices:", formatCurrency(statement.getTotalInvoices()));
    createRow(sheet, rowNum++, "Total Paid:", formatCurrency(statement.getTotalPaid()));
    createRow(sheet, rowNum++, "Total Outstanding:", formatCurrency(statement.getTotalOutstanding()));

    // Footer with hash
    rowNum++;
    Company company = getCompany();
    String footer = String.format(
        "Generated: %s | Hash: %s",
        DATE_FORMATTER.format(LocalDate.now()),
        generateHash(statement));
    if (company != null) {
      footer += String.format(" | %s - Tax Code: %s", company.getName(), company.getTaxCode());
    }
    createRow(sheet, rowNum++, footer, "");
  }

  private void exportDetailedToExcel(
      ARStatementDetailedDTO statement,
      Sheet sheet,
      int startRow,
      CellStyle headerStyle,
      CellStyle currencyStyle,
      CellStyle dateStyle) {
    int rowNum = startRow;

    // Title
    Row titleRow = sheet.createRow(rowNum++);
    titleRow.createCell(0).setCellValue("CUSTOMER STATEMENT - DETAILED");
    titleRow.getCell(0).setCellStyle(headerStyle);

    // Customer info
    rowNum++;
    createRow(sheet, rowNum++, "Customer:", statement.getCustomerName());
    createRow(sheet, rowNum++, "Code:", statement.getCustomerCode());
    if (statement.getCustomerAddress() != null) {
      createRow(sheet, rowNum++, "Address:", statement.getCustomerAddress());
    }
    if (statement.getCustomerTaxCode() != null) {
      createRow(sheet, rowNum++, "Tax Code:", statement.getCustomerTaxCode());
    }
    createRow(sheet, rowNum++, "As of Date:", DATE_FORMATTER.format(statement.getAsOfDate()));

    // Table header
    rowNum++;
    Row headerRow = sheet.createRow(rowNum++);
    String[] headers = {"Type", "Date", "Reference", "Description", "Debit", "Credit", "Running Balance"};
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // Data rows
    for (ARStatementDetailedDTO.StatementTransactionDTO transaction : statement.getTransactions()) {
      Row dataRow = sheet.createRow(rowNum++);
      dataRow.createCell(0).setCellValue(transaction.getType());
      Cell dateCell = dataRow.createCell(1);
      dateCell.setCellValue(transaction.getTransactionDate());
      dateCell.setCellStyle(dateStyle);
      dataRow.createCell(2).setCellValue(transaction.getReference() != null ? transaction.getReference() : "");
      dataRow.createCell(3).setCellValue(transaction.getDescription() != null ? transaction.getDescription() : "");
      Cell debitCell = dataRow.createCell(4);
      debitCell.setCellValue(transaction.getDebit() != null ? transaction.getDebit().doubleValue() : 0);
      debitCell.setCellStyle(currencyStyle);
      Cell creditCell = dataRow.createCell(5);
      creditCell.setCellValue(transaction.getCredit() != null ? transaction.getCredit().doubleValue() : 0);
      creditCell.setCellStyle(currencyStyle);
      Cell runningBalanceCell = dataRow.createCell(6);
      runningBalanceCell.setCellValue(transaction.getRunningBalance().doubleValue());
      runningBalanceCell.setCellStyle(currencyStyle);
    }

    // Totals
    rowNum++;
    createRow(sheet, rowNum++, "Total Invoices:", formatCurrency(statement.getTotalInvoices()));
    createRow(sheet, rowNum++, "Total Paid:", formatCurrency(statement.getTotalPaid()));
    createRow(sheet, rowNum++, "Total Outstanding:", formatCurrency(statement.getTotalOutstanding()));

    // Footer with hash
    rowNum++;
    Company company = getCompany();
    String footer = String.format(
        "Generated: %s | Hash: %s",
        DATE_FORMATTER.format(LocalDate.now()),
        generateHash(statement));
    if (company != null) {
      footer += String.format(" | %s - Tax Code: %s", company.getName(), company.getTaxCode());
    }
    createRow(sheet, rowNum++, footer, "");
  }

  private byte[] exportToPDF(Object statement, ARStatementHistory.StatementFormat format) {
    // Simple text-based PDF for MVP (can be enhanced with iText or PDFBox later)
    StringBuilder pdf = new StringBuilder();
    
    Company company = getCompany();
    if (company != null) {
      pdf.append(company.getName()).append("\n");
      pdf.append("Tax Code: ").append(company.getTaxCode()).append("\n");
      pdf.append("Address: ").append(company.getAddress()).append("\n\n");
    }

    if (format == ARStatementHistory.StatementFormat.SUMMARY) {
      exportSummaryToPDF((ARStatementSummaryDTO) statement, pdf);
    } else {
      exportDetailedToPDF((ARStatementDetailedDTO) statement, pdf);
    }

    // Footer
    pdf.append("\n");
    pdf.append("Generated: ").append(DATE_FORMATTER.format(LocalDate.now())).append("\n");
    pdf.append("Hash: ").append(generateHash(statement)).append("\n");
    if (company != null) {
      pdf.append(company.getName()).append(" - Tax Code: ").append(company.getTaxCode()).append("\n");
    }

    return pdf.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void exportSummaryToPDF(ARStatementSummaryDTO statement, StringBuilder pdf) {
    pdf.append("CUSTOMER STATEMENT - SUMMARY\n\n");
    pdf.append("Customer: ").append(statement.getCustomerName()).append("\n");
    pdf.append("Code: ").append(statement.getCustomerCode()).append("\n");
    if (statement.getCustomerAddress() != null) {
      pdf.append("Address: ").append(statement.getCustomerAddress()).append("\n");
    }
    if (statement.getCustomerTaxCode() != null) {
      pdf.append("Tax Code: ").append(statement.getCustomerTaxCode()).append("\n");
    }
    pdf.append("As of Date: ").append(DATE_FORMATTER.format(statement.getAsOfDate())).append("\n\n");

    pdf.append(String.format(
        "%-15s %-12s %15s %15s %15s %15s\n",
        "Invoice #", "Date", "Invoice Amount", "Amount Paid", "Balance", "Running Balance"));
    pdf.append("-".repeat(100)).append("\n");

    for (ARStatementSummaryDTO.StatementInvoiceDTO invoice : statement.getInvoices()) {
      pdf.append(String.format(
          "%-15s %-12s %15s %15s %15s %15s\n",
          invoice.getInvoiceNumber(),
          DATE_FORMATTER.format(invoice.getInvoiceDate()),
          formatCurrency(invoice.getInvoiceAmount()),
          formatCurrency(invoice.getAmountPaid()),
          formatCurrency(invoice.getBalance()),
          formatCurrency(invoice.getRunningBalance())));
    }

    pdf.append("\n");
    pdf.append("Total Invoices: ").append(formatCurrency(statement.getTotalInvoices())).append("\n");
    pdf.append("Total Paid: ").append(formatCurrency(statement.getTotalPaid())).append("\n");
    pdf.append("Total Outstanding: ").append(formatCurrency(statement.getTotalOutstanding())).append("\n");
  }

  private void exportDetailedToPDF(ARStatementDetailedDTO statement, StringBuilder pdf) {
    pdf.append("CUSTOMER STATEMENT - DETAILED\n\n");
    pdf.append("Customer: ").append(statement.getCustomerName()).append("\n");
    pdf.append("Code: ").append(statement.getCustomerCode()).append("\n");
    if (statement.getCustomerAddress() != null) {
      pdf.append("Address: ").append(statement.getCustomerAddress()).append("\n");
    }
    if (statement.getCustomerTaxCode() != null) {
      pdf.append("Tax Code: ").append(statement.getCustomerTaxCode()).append("\n");
    }
    pdf.append("As of Date: ").append(DATE_FORMATTER.format(statement.getAsOfDate())).append("\n\n");

    pdf.append(String.format(
        "%-10s %-12s %-20s %-30s %15s %15s %15s\n",
        "Type", "Date", "Reference", "Description", "Debit", "Credit", "Running Balance"));
    pdf.append("-".repeat(120)).append("\n");

    for (ARStatementDetailedDTO.StatementTransactionDTO transaction : statement.getTransactions()) {
      pdf.append(String.format(
          "%-10s %-12s %-20s %-30s %15s %15s %15s\n",
          transaction.getType(),
          DATE_FORMATTER.format(transaction.getTransactionDate()),
          truncate(transaction.getReference(), 20),
          truncate(transaction.getDescription(), 30),
          formatCurrency(transaction.getDebit()),
          formatCurrency(transaction.getCredit()),
          formatCurrency(transaction.getRunningBalance())));
    }

    pdf.append("\n");
    pdf.append("Total Invoices: ").append(formatCurrency(statement.getTotalInvoices())).append("\n");
    pdf.append("Total Paid: ").append(formatCurrency(statement.getTotalPaid())).append("\n");
    pdf.append("Total Outstanding: ").append(formatCurrency(statement.getTotalOutstanding())).append("\n");
  }

  private String generateHash(Object statement) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String content = statement.toString();
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hash).substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
      logger.error("Failed to generate statement hash", e);
      return "HASH_ERROR";
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte b : bytes) {
      result.append(String.format("%02x", b));
    }
    return result.toString();
  }

  private void createRow(Sheet sheet, int rowNum, String label, String value) {
    Row row = sheet.createRow(rowNum);
    row.createCell(0).setCellValue(label);
    row.createCell(1).setCellValue(value);
  }

  private String formatCurrency(BigDecimal amount) {
    if (amount == null) {
      return "0₫";
    }
    return String.format("%,.0f₫", amount);
  }

  private String truncate(String str, int maxLength) {
    if (str == null) {
      return "";
    }
    return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
  }

  private CellStyle createHeaderStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    style.setFont(font);
    return style;
  }

  private CellStyle createCurrencyStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    DataFormat format = workbook.createDataFormat();
    style.setDataFormat(format.getFormat("#,##0"));
    return style;
  }

  private CellStyle createDateStyle(Workbook workbook) {
    CellStyle style = workbook.createCellStyle();
    DataFormat format = workbook.createDataFormat();
    style.setDataFormat(format.getFormat("dd/mm/yyyy"));
    return style;
  }

  private Company getCompany() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      return null;
    }
    return companyRepository.findById(companyId).orElse(null);
  }
}

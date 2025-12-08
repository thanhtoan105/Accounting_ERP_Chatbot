package com.accounting.imports.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.accounting.imports.ImportType;
import com.accounting.imports.service.ImportTemplateService;

@Service
public class ImportTemplateServiceImpl implements ImportTemplateService {

  private static final Map<ImportType, TemplateDefinition> DEFINITIONS = new HashMap<>();

  static {
    DEFINITIONS.put(
        ImportType.CUSTOMERS,
        new TemplateDefinition(
            List.of("customer_code", "name", "tax_code", "email", "phone", "address", "active"),
            List.of(
                List.of(
                    "CUST-001",
                    "Sunrise Trading Co.",
                    "0101234567",
                    "hoa.nguyen@sunrise.vn",
                    "+84-24-3939-2222",
                    "45 Ly Thuong Kiet, Hoan Kiem, Hanoi, VN",
                    "TRUE"),
                List.of(
                    "CUST-002",
                    "Emerald Retail Ltd.",
                    "0309876543",
                    "duc.tran@emerald-retail.com",
                    "+84-28-3822-8899",
                    "12 Nguyen Hue, District 1, Ho Chi Minh City, VN",
                    "TRUE"))));
    DEFINITIONS.put(
        ImportType.SUPPLIERS,
        new TemplateDefinition(
            List.of("supplier_code", "name", "tax_code", "email", "phone", "address", "active"),
            List.of(
                List.of(
                    "SUP-001",
                    "Lotus Manufacturing JSC",
                    "0101122233",
                    "mai.le@lotus-mfg.vn",
                    "+84-24-3773-4455",
                    "168 Tran Duy Hung, Cau Giay, Hanoi, VN",
                    "TRUE"),
                List.of(
                    "SUP-002",
                    "Pacific Raw Materials",
                    "0309988776",
                    "huy.pham@pacificraw.com",
                    "+84-28-3620-7788",
                    "55 Le Lai, District 1, Ho Chi Minh City, VN",
                    "TRUE"))));
    DEFINITIONS.put(
        ImportType.BANK_ACCOUNTS,
        new TemplateDefinition(
            List.of(
                "account_code",
                "account_number",
                "bank_name",
                "branch",
                "account_type",
                "opening_balance",
                "active"),
            List.of(
                List.of(
                    "BANK-001",
                    "0451000123456",
                    "Vietcombank",
                    "Hanoi Main",
                    "BANK",
                    "280000000",
                    "TRUE"),
                List.of(
                    "BANK-002",
                    "205890123456",
                    "ACB",
                    "Saigon Prestige",
                    "BANK",
                    "150000000",
                    "TRUE"))));
    DEFINITIONS.put(
        ImportType.OPENING_BALANCES,
        new TemplateDefinition(
            List.of(
                "journal_code",
                "account_code",
                "account_name",
                "currency",
                "debit",
                "credit",
                "period_start",
                "period_end",
                "note"),
            List.of(
                List.of(
                    "OB-2025-01",
                    "1311",
                    "Trade Receivables",
                    "VND",
                    "350000000",
                    "0",
                    "2025-01-01",
                    "2025-01-31",
                    "Cut-over balance for customers"),
                List.of(
                    "OB-2025-01",
                    "3311",
                    "Trade Payables",
                    "VND",
                    "0",
                    "280000000",
                    "2025-01-01",
                    "2025-01-31",
                    "Cut-over balance for suppliers"),
                List.of(
                    "OB-2025-01",
                    "1121",
                    "Cash at Bank",
                    "VND",
                    "280000000",
                    "0",
                    "2025-01-01",
                    "2025-01-31",
                    "Balancing entry"),
                List.of(
                    "OB-2025-01",
                    "4111",
                    "Owner Equity - Opening",
                    "VND",
                    "0",
                    "350000000",
                    "2025-01-01",
                    "2025-01-31",
                    "Opening retained earnings"))));
  }

  @Override
  public byte[] generateTemplate(ImportType type, String format) {
    TemplateDefinition definition =
        DEFINITIONS.getOrDefault(type, null);
    if (definition == null) {
      throw new IllegalArgumentException("No template definition for type " + type);
    }
    String normalized = format.toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "csv" -> generateCsv(definition);
      case "xls" -> generateWorkbook(definition, new HSSFWorkbook());
      case "xlsx" -> generateWorkbook(definition, new XSSFWorkbook());
      default -> throw new IllegalArgumentException("Unsupported template format: " + format);
    };
  }

  private byte[] generateCsv(TemplateDefinition definition) {
    StringJoiner joiner = new StringJoiner(System.lineSeparator());
    joiner.add(String.join(",", definition.headers()));
    for (List<String> sample : definition.samples()) {
      joiner.add(sample.stream().map(this::escape).reduce((a, b) -> a + "," + b).orElse(""));
    }
    return joiner.toString().getBytes(StandardCharsets.UTF_8);
  }

  private byte[] generateWorkbook(TemplateDefinition definition, Workbook workbook) {
    Sheet sheet = workbook.createSheet("Template");

    Row headerRow = sheet.createRow(0);
    for (int i = 0; i < definition.headers().size(); i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(definition.headers().get(i));
      sheet.autoSizeColumn(i);
    }

    for (int rowIndex = 0; rowIndex < definition.samples().size(); rowIndex++) {
      Row row = sheet.createRow(rowIndex + 1);
      List<String> sample = definition.samples().get(rowIndex);
      for (int col = 0; col < sample.size(); col++) {
        Cell cell = row.createCell(col);
        cell.setCellValue(sample.get(col));
      }
    }

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      workbook.write(outputStream);
      workbook.close();
      return outputStream.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to generate template workbook", ex);
    }
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    String sanitized = value.replace("\"", "\"\"");
    if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n")) {
      return "\"" + sanitized + "\"";
    }
    return sanitized;
  }

  private record TemplateDefinition(List<String> headers, List<List<String>> samples) {}
}

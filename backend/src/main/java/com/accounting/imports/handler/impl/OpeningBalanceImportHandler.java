package com.accounting.imports.handler.impl;

import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherLineDTO;
import com.accounting.entity.ChartOfAccount;
import com.accounting.imports.ImportType;
import com.accounting.imports.exception.ImportProcessingException;
import com.accounting.imports.exception.ImportValidationException;
import com.accounting.imports.handler.ImportHandler;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportRowAudit;
import com.accounting.imports.model.ImportRowError;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.ledger.LedgerPeriodService;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.VoucherService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component("opening-balances")
public class OpeningBalanceImportHandler implements ImportHandler {

  private static final List<String> EXPECTED_HEADERS = List.of(
      "journal_code",
      "account_code",
      "account_name",
      "currency",
      "debit",
      "credit",
      "period_start",
      "period_end",
      "note");

  private static final Set<String> SUPPORTED_FORMATS = Set.of("csv", "xlsx", "xls");
  private static final DataFormatter DATA_FORMATTER = new DataFormatter();

  private final ImportErrorReportService errorReportService;
  private final LedgerPeriodService ledgerPeriodService;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final VoucherService voucherService;
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_ERROR = "ERROR";
  private static final String STATUS_ROLLED_BACK = "ROLLED_BACK";
  private static final String STATUS_VALIDATION_ERROR = "VALIDATION_ERROR";

  public OpeningBalanceImportHandler(
      ImportErrorReportService errorReportService,
      LedgerPeriodService ledgerPeriodService,
      ChartOfAccountsRepository chartOfAccountsRepository,
      VoucherService voucherService) {
    this.errorReportService = errorReportService;
    this.ledgerPeriodService = ledgerPeriodService;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.voucherService = voucherService;
  }

  @Override
  @Transactional
  public ImportSummary handle(MultipartFile file, ImportContext context) {
    // Guard: Block when first period is already closed
    if (ledgerPeriodService.isFirstPeriodClosed(context.companyId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Opening balance import is blocked: first period is closed");
    }
    if (file == null || file.isEmpty()) {
      throw new ImportValidationException(
          "File is empty", List.of(new ImportRowError(0, "file", "Uploaded file contains no data")));
    }
    String format = detectFormat(file.getOriginalFilename());
    if (!SUPPORTED_FORMATS.contains(format)) {
      throw new ImportValidationException(
          "Unsupported file format",
          List.of(new ImportRowError(0, "file", "Supported formats: CSV, XLSX")));
    }

    ParsedImport parsed = parseFile(file, format);
    if (!parsed.errors.isEmpty()) {
      java.util.List<ImportRowAudit> auditEntries = parsed.errors.stream()
          .map(error -> new ImportRowAudit(
              error.rowNumber(),
              java.util.Map.of("field", error.field()),
              null,
              STATUS_VALIDATION_ERROR,
              error.message()))
          .toList();
      java.util.UUID reportId = errorReportService.saveReport(
          ImportType.OPENING_BALANCES,
          context.companyId(),
          context.userId(),
          context.filename(),
          parsed.errors);
      throw new ImportValidationException("Validation failed", parsed.errors, reportId, auditEntries);
    }

    // Business rule: enforce Dr = Cr across all rows in the same journal_code
    List<ImportRowError> processingErrors = new ArrayList<>();
    List<ResolvedRow> resolvedRows = new ArrayList<>();
    List<RowAuditBuilder> auditBuilders = new ArrayList<>();
    for (RowModel row : parsed.rows) {
      RowAuditBuilder auditBuilder = RowAuditBuilder.success(row.rowNumber(), row);
      auditBuilders.add(auditBuilder);
      ResolvedRow resolved = resolveRow(row, context, processingErrors, auditBuilder);
      if (resolved != null) {
        resolvedRows.add(resolved);
      }
    }

    if (!processingErrors.isEmpty()) {
      markRollback();
      auditBuilders.forEach(RowAuditBuilder::markRolledBackIfSuccessful);
      UUID reportId = errorReportService.saveReport(
          ImportType.OPENING_BALANCES,
          context.companyId(),
          context.userId(),
          context.filename(),
          processingErrors);
      throw new ImportValidationException(
          "Validation failed", processingErrors, reportId, toAuditEntries(auditBuilders));
    }

    BigDecimal totalDebit = resolvedRows.stream()
        .map(ResolvedRow::debit)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalCredit = resolvedRows.stream()
        .map(ResolvedRow::credit)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalDebit.compareTo(totalCredit) != 0) {
      int rowNumber = resolvedRows.isEmpty() ? 1 : resolvedRows.get(0).rowNumber();
      String message = "Sum of debit must equal sum of credit for opening balances";
      processingErrors.add(
          new ImportRowError(
              rowNumber,
              "debit/credit",
              message));
      auditBuilders.stream()
          .filter(builder -> builder.rowNumber() == rowNumber)
          .findFirst()
          .ifPresent(builder -> builder.markError(message));
    }

    Map<String, List<ResolvedRow>> rowsByJournal = resolvedRows.stream()
        .collect(Collectors.groupingBy(
            ResolvedRow::journalCode,
            LinkedHashMap::new,
            Collectors.toList()));

    for (Map.Entry<String, List<ResolvedRow>> entry : rowsByJournal.entrySet()) {
      BigDecimal journalDebit = entry.getValue().stream()
          .map(ResolvedRow::debit)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal journalCredit = entry.getValue().stream()
          .map(ResolvedRow::credit)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (journalDebit.compareTo(journalCredit) != 0) {
        int rowNumber = entry.getValue().get(0).rowNumber();
        String message = "Journal " + entry.getKey() + " must balance (debit = credit)";
        processingErrors.add(
            new ImportRowError(
                rowNumber,
                "debit/credit",
                message));
        auditBuilders.stream()
            .filter(builder -> builder.rowNumber() == rowNumber)
            .findFirst()
            .ifPresent(builder -> builder.markError(message));
      }
    }

    if (!processingErrors.isEmpty()) {
      markRollback();
      auditBuilders.forEach(RowAuditBuilder::markRolledBackIfSuccessful);
      UUID reportId = errorReportService.saveReport(
          ImportType.OPENING_BALANCES,
          context.companyId(),
          context.userId(),
          context.filename(),
          processingErrors);
      throw new ImportValidationException(
          "Opening balance rule violation", processingErrors, reportId, toAuditEntries(auditBuilders));
    }

    Long originalCompanyId = CompanyContext.getCompanyId();
    boolean overrideContext = originalCompanyId == null;
    if (overrideContext) {
      CompanyContext.setCompanyId(context.companyId());
    }

    int successCount = 0;
    try {
      for (Map.Entry<String, List<ResolvedRow>> entry : rowsByJournal.entrySet()) {
        List<VoucherLineDTO> lines = new ArrayList<>();
        int lineNumber = 1;
        for (ResolvedRow row : entry.getValue()) {
          VoucherLineDTO line = new VoucherLineDTO();
          line.setLineNumber(lineNumber++);
          line.setAccountId(row.accountId());
          line.setDebit(row.debit());
          line.setCredit(row.credit());
          line.setDescription(row.note());
          lines.add(line);
        }

        VoucherCreateRequest request = new VoucherCreateRequest();
        request.setDate(resolveVoucherDate(entry.getValue(), context));
        request.setDescription("Opening balance import - " + entry.getKey());
        request.setLines(lines);

        try {
          voucherService.create(request);
          successCount += entry.getValue().size();
        } catch (ResponseStatusException ex) {
          String message = messageOrDefault(ex.getReason());
          processingErrors.add(
              new ImportRowError(
                  entry.getValue().get(0).rowNumber(),
                  "voucher",
                  message));
          markRowsAsError(auditBuilders, entry.getValue(), message);
        } catch (Exception ex) {
          String message = messageOrDefault(ex.getMessage());
          processingErrors.add(
              new ImportRowError(
                  entry.getValue().get(0).rowNumber(),
                  "voucher",
                  message));
          markRowsAsError(auditBuilders, entry.getValue(), message);
        }
      }
    } finally {
      if (overrideContext) {
        CompanyContext.clear();
      } else if (!Objects.equals(originalCompanyId, context.companyId())) {
        CompanyContext.setCompanyId(originalCompanyId);
      }
    }

    if (!processingErrors.isEmpty()) {
      markRollback();
      auditBuilders.forEach(RowAuditBuilder::markRolledBackIfSuccessful);
      UUID reportId = errorReportService.saveReport(
          ImportType.OPENING_BALANCES,
          context.companyId(),
          context.userId(),
          context.filename(),
          processingErrors);
      throw new ImportValidationException(
          "Opening balance import failed", processingErrors, reportId, toAuditEntries(auditBuilders));
    }

    return ImportSummary.success(successCount, toAuditEntries(auditBuilders));
  }

  private ParsedImport parseFile(MultipartFile file, String format) {
    try {
      if ("csv".equals(format)) {
        return parseCsv(file);
      }
      return parseExcel(file);
    } catch (IOException ex) {
      throw new ImportProcessingException("Unable to parse import file", ex);
    }
  }

  private ParsedImport parseCsv(MultipartFile file) throws IOException {
    List<RowModel> rows = new ArrayList<>();
    List<ImportRowError> errors = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        errors.add(new ImportRowError(0, "header", "Missing header row"));
        return new ParsedImport(rows, errors);
      }
      String[] headers = headerLine.split(",");
      validateHeaders(headers, errors);
      if (!errors.isEmpty()) {
        return new ParsedImport(rows, errors);
      }
      String line;
      int rowNumber = 1;
      while ((line = reader.readLine()) != null) {
        rowNumber++;
        if (line == null || line.isBlank()) {
          continue;
        }
        String[] tokens = splitCsvRow(line);
        RowModel model = mapRow(tokens, rowNumber, errors);
        if (model != null) {
          rows.add(model);
        }
      }
    }
    return new ParsedImport(rows, errors);
  }

  private ParsedImport parseExcel(MultipartFile file) throws IOException {
    List<RowModel> rows = new ArrayList<>();
    List<ImportRowError> errors = new ArrayList<>();
    try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
      Sheet sheet = workbook.getSheetAt(0);
      if (sheet == null) {
        errors.add(new ImportRowError(0, "sheet", "First sheet is missing"));
        return new ParsedImport(rows, errors);
      }
      Row headerRow = sheet.getRow(0);
      if (headerRow == null) {
        errors.add(new ImportRowError(0, "header", "Missing header row"));
        return new ParsedImport(rows, errors);
      }
      String[] headers = new String[EXPECTED_HEADERS.size()];
      for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
        headers[i] = getCellValueAsString(headerRow.getCell(i));
      }
      validateHeaders(headers, errors);
      if (!errors.isEmpty()) {
        return new ParsedImport(rows, errors);
      }
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) {
          continue;
        }
        boolean allBlank = true;
        String[] tokens = new String[EXPECTED_HEADERS.size()];
        for (int col = 0; col < EXPECTED_HEADERS.size(); col++) {
          String value = getCellValueAsString(row.getCell(col));
          tokens[col] = value;
          if (value != null && !value.isBlank()) {
            allBlank = false;
          }
        }
        if (allBlank) {
          continue;
        }
        int rowNumber = i + 1;
        RowModel model = mapRow(tokens, rowNumber, errors);
        if (model != null) {
          rows.add(model);
        }
      }
    }
    return new ParsedImport(rows, errors);
  }

  private void validateHeaders(String[] actualHeaders, List<ImportRowError> errors) {
    if (actualHeaders.length < EXPECTED_HEADERS.size()) {
      errors.add(
          new ImportRowError(
              1, "header", "Header count mismatch. Expected " + EXPECTED_HEADERS.size() + " columns"));
      return;
    }
    for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
      String expected = EXPECTED_HEADERS.get(i);
      String actual = actualHeaders[i] != null ? actualHeaders[i].trim().toLowerCase(Locale.ROOT) : "";
      if (!Objects.equals(expected, actual)) {
        errors.add(
            new ImportRowError(
                1,
                expected,
                "Header mismatch at column "
                    + (i + 1)
                    + ". Expected '"
                    + expected
                    + "' but found '"
                    + actual
                    + "'"));
      }
    }
  }

  private RowModel mapRow(String[] tokens, int rowNumber, List<ImportRowError> errors) {
    String journalCode = value(tokens, 0);
    String accountCode = value(tokens, 1);
    String accountName = value(tokens, 2);
    String currency = value(tokens, 3);
    String debitRaw = value(tokens, 4);
    String creditRaw = value(tokens, 5);
    BigDecimal debit = parseAmount(debitRaw, "debit", rowNumber, errors);
    BigDecimal credit = parseAmount(creditRaw, "credit", rowNumber, errors);
    LocalDate periodStart = parseDate(value(tokens, 6), "period_start", rowNumber, errors);
    LocalDate periodEnd = parseDate(value(tokens, 7), "period_end", rowNumber, errors);
    String note = value(tokens, 8);

    int initialErrors = errors.size();

    if (isBlank(journalCode)) {
      errors.add(new ImportRowError(rowNumber, "journal_code", "Journal code is required"));
    }
    if (isBlank(accountCode)) {
      errors.add(new ImportRowError(rowNumber, "account_code", "Account code is required"));
    }

    boolean debitProvided = debitRaw != null && !debitRaw.isBlank();
    boolean creditProvided = creditRaw != null && !creditRaw.isBlank();
    if (!debitProvided && !creditProvided) {
      errors.add(new ImportRowError(rowNumber, "debit/credit", "Either debit or credit must be provided"));
    }

    if (currency == null || currency.isBlank()) {
      errors.add(new ImportRowError(rowNumber, "currency", "Currency is required"));
    }

    if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) {
      errors.add(new ImportRowError(rowNumber, "period_end", "Period end must be on or after period start"));
    }

    if (errors.size() > initialErrors) {
      return null;
    }

    BigDecimal safeDebit = debit != null ? debit : BigDecimal.ZERO;
    BigDecimal safeCredit = credit != null ? credit : BigDecimal.ZERO;

    return new RowModel(
        rowNumber,
        journalCode,
        accountCode,
        accountName,
        currency,
        safeDebit,
        safeCredit,
        periodStart,
        periodEnd,
        note);
  }

  private String detectFormat(String filename) {
    if (filename == null) {
      return "xlsx";
    }
    String lower = filename.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".csv")) {
      return "csv";
    }
    if (lower.endsWith(".xls")) {
      return "xls";
    }
    return "xlsx";
  }

  private String[] splitCsvRow(String line) {
    List<String> values = new ArrayList<>();
    boolean inQuotes = false;
    StringBuilder current = new StringBuilder();
    for (char c : line.toCharArray()) {
      if (c == '\"') {
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
    String value = DATA_FORMATTER.formatCellValue(cell);
    return value != null && !value.isBlank() ? value.trim() : null;
  }

  private String value(String[] tokens, int index) {
    if (tokens == null || index >= tokens.length) {
      return null;
    }
    String value = tokens[index];
    return value == null || value.isBlank() ? null : value.trim();
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private BigDecimal parseAmount(String raw, String field, int rowNumber, List<ImportRowError> errors) {
    if (raw == null || raw.isBlank()) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    try {
      String sanitized = raw.replace("_", "").replace(",", "").replace(" ", "");
      BigDecimal value = new BigDecimal(sanitized);
      return value.setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException ex) {
      errors.add(new ImportRowError(rowNumber, field, "Invalid number"));
      return null;
    }
  }

  private LocalDate parseDate(String raw, String field, int rowNumber, List<ImportRowError> errors) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw.trim());
    } catch (DateTimeParseException ex) {
      errors.add(new ImportRowError(rowNumber, field, "Invalid date (expected yyyy-MM-dd)"));
      return null;
    }
  }

  private ResolvedRow resolveRow(
      RowModel row,
      ImportContext context,
      List<ImportRowError> errors,
      RowAuditBuilder auditBuilder) {
    Long companyId = context.companyId();
    ChartOfAccount account = chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, row.accountCode())
        .orElse(null);
    if (account == null) {
      String message = "Account code " + row.accountCode() + " does not exist";
      errors.add(new ImportRowError(row.rowNumber(), "account_code", message));
      auditBuilder.markError(message);
      return null;
    }
    if (Boolean.FALSE.equals(account.getActive())) {
      String message = "Account " + row.accountCode() + " is inactive";
      errors.add(new ImportRowError(row.rowNumber(), "account_code", message));
      auditBuilder.markError(message);
      return null;
    }
    if (Boolean.FALSE.equals(account.getPostable())) {
      String message = "Account " + row.accountCode() + " is not postable";
      errors.add(new ImportRowError(row.rowNumber(), "account_code", message));
      auditBuilder.markError(message);
      return null;
    }

    String currency = row.currency() != null ? row.currency().toUpperCase(Locale.ROOT) : null;
    if (!"VND".equals(currency)) {
      String message = "Only VND currency is supported for opening balance imports at this time";
      errors.add(new ImportRowError(row.rowNumber(), "currency", message));
      auditBuilder.markError(message);
      return null;
    }

    ResolvedRow resolved = new ResolvedRow(
        row.rowNumber(),
        row.journalCode(),
        account.getId(),
        row.debit(),
        row.credit(),
        row.periodStart(),
        row.periodEnd(),
        row.note(),
        currency);
    auditBuilder.setAfterPayload(resolved);
    return resolved;
  }

  private LocalDate resolveVoucherDate(List<ResolvedRow> rows, ImportContext context) {
    return rows.stream()
        .map(ResolvedRow::periodEnd)
        .filter(Objects::nonNull)
        .findFirst()
        .orElseGet(() -> rows.stream()
            .map(ResolvedRow::periodStart)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(context.requestedAt().atZone(ZoneId.systemDefault()).toLocalDate()));
  }

  private void markRollback() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
  }

  private String messageOrDefault(String message) {
    return (message == null || message.isBlank()) ? "Unexpected error during import" : message;
  }

  private void markRowsAsError(
      List<RowAuditBuilder> builders, List<ResolvedRow> rows, String message) {
    for (ResolvedRow row : rows) {
      builders.stream()
          .filter(builder -> builder.rowNumber() == row.rowNumber())
          .findFirst()
          .ifPresent(builder -> builder.markError(message));
    }
  }

  private List<ImportRowAudit> toAuditEntries(List<RowAuditBuilder> builders) {
    return builders.stream().map(RowAuditBuilder::toAudit).toList();
  }

  private static final class RowAuditBuilder {
    private final int rowNumber;
    private final Object beforePayload;
    private Object afterPayload;
    private String status;
    private String message;

    private RowAuditBuilder(int rowNumber, Object beforePayload, String status) {
      this.rowNumber = rowNumber;
      this.beforePayload = beforePayload;
      this.status = status;
    }

    static RowAuditBuilder success(int rowNumber, Object beforePayload) {
      return new RowAuditBuilder(rowNumber, beforePayload, STATUS_SUCCESS);
    }

    int rowNumber() {
      return rowNumber;
    }

    void setAfterPayload(Object afterPayload) {
      this.afterPayload = afterPayload;
    }

    void markError(String message) {
      this.status = STATUS_ERROR;
      this.message = message;
    }

    void markRolledBackIfSuccessful() {
      if (STATUS_SUCCESS.equals(this.status)) {
        this.status = STATUS_ROLLED_BACK;
      }
    }

    ImportRowAudit toAudit() {
      return new ImportRowAudit(rowNumber, beforePayload, afterPayload, status, message);
    }
  }

  private record RowModel(
      int rowNumber,
      String journalCode,
      String accountCode,
      String accountName,
      String currency,
      BigDecimal debit,
      BigDecimal credit,
      LocalDate periodStart,
      LocalDate periodEnd,
      String note) {
  }

  private record ParsedImport(List<RowModel> rows, List<ImportRowError> errors) {
  }

  private record ResolvedRow(
      int rowNumber,
      String journalCode,
      Long accountId,
      BigDecimal debit,
      BigDecimal credit,
      LocalDate periodStart,
      LocalDate periodEnd,
      String note,
      String currency) {
  }
}

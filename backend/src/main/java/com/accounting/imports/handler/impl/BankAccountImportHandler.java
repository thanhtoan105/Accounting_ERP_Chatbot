package com.accounting.imports.handler.impl;

import com.accounting.dto.BankAccountCreateRequest;
import com.accounting.entity.BankAccount;
import com.accounting.imports.ImportType;
import com.accounting.imports.exception.ImportProcessingException;
import com.accounting.imports.exception.ImportValidationException;
import com.accounting.imports.handler.ImportHandler;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportRowAudit;
import com.accounting.imports.model.ImportRowError;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.service.BankAccountService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Component("bank-accounts")
public class BankAccountImportHandler implements ImportHandler {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "account_number",
            "bank_name",
            "branch",
            "account_type",
            "opening_balance",
            "gl_account_code",
            "active");
    private static final Set<String> SUPPORTED_FORMATS = Set.of("csv", "xlsx", "xls");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";
    private static final String STATUS_ROLLED_BACK = "ROLLED_BACK";
    private static final String STATUS_VALIDATION_ERROR = "VALIDATION_ERROR";

    private final BankAccountService bankAccountService;
    private final Validator validator;
    private final ImportErrorReportService errorReportService;

    public BankAccountImportHandler(
            BankAccountService bankAccountService,
            Validator validator,
            ImportErrorReportService errorReportService) {
        this.bankAccountService = bankAccountService;
        this.validator = validator;
        this.errorReportService = errorReportService;
    }

    @Override
    @Transactional
    public ImportSummary handle(MultipartFile file, ImportContext context) {
        if (file.isEmpty()) {
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
            List<ImportRowAudit> auditEntries = parsed.errors.stream()
                    .map(error -> new ImportRowAudit(
                            error.rowNumber(),
                            java.util.Map.of("field", error.field()),
                            null,
                            STATUS_VALIDATION_ERROR,
                            error.message()))
                    .toList();
            java.util.UUID reportId = errorReportService.saveReport(
                    ImportType.BANK_ACCOUNTS,
                    context.companyId(),
                    context.userId(),
                    context.filename(),
                    parsed.errors);
            throw new ImportValidationException("Validation failed", parsed.errors, reportId, auditEntries);
        }

        List<ImportRowError> processingErrors = new ArrayList<>();
        int successCount = 0;
        List<RowAuditBuilder> auditBuilders = new ArrayList<>();

        for (int i = 0; i < parsed.requests.size(); i++) {
            BankAccountCreateRequest request = parsed.requests.get(i);
            int rowNumber = parsed.rowNumbers.get(i);
            try {
                var createdAccount = bankAccountService.create(request);
                auditBuilders.add(new RowAuditBuilder(rowNumber, request, createdAccount, STATUS_SUCCESS, null));
                successCount++;
            } catch (org.springframework.web.server.ResponseStatusException ex) {
                String message = messageOrDefault(ex.getReason());
                processingErrors.add(new ImportRowError(rowNumber, resolveField(ex), message));
                auditBuilders.add(new RowAuditBuilder(rowNumber, request, null, STATUS_ERROR, message));
            } catch (Exception ex) {
                String message = messageOrDefault(ex.getMessage());
                processingErrors.add(new ImportRowError(rowNumber, "general", message));
                auditBuilders.add(new RowAuditBuilder(rowNumber, request, null, STATUS_ERROR, message));
            }
        }

        if (!processingErrors.isEmpty()) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            auditBuilders.forEach(RowAuditBuilder::markRolledBackIfSuccessful);
            java.util.UUID reportId = errorReportService.saveReport(
                    ImportType.BANK_ACCOUNTS,
                    context.companyId(),
                    context.userId(),
                    context.filename(),
                    processingErrors);
            throw new ImportValidationException(
                    "Import failed", processingErrors, reportId, toAuditEntries(auditBuilders));
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
        List<BankAccountCreateRequest> requests = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                errors.add(new ImportRowError(0, "header", "Missing header row"));
                return new ParsedImport(requests, rowNumbers, errors);
            }
            validateHeaders(splitCsvRow(headerLine), errors);
            if (!errors.isEmpty()) {
                return new ParsedImport(requests, rowNumbers, errors);
            }

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] tokens = splitCsvRow(line);
                BankAccountCreateRequest request = mapRow(tokens, rowNumber, errors);
                if (request != null) {
                    requests.add(request);
                    rowNumbers.add(rowNumber);
                }
            }
        }

        return new ParsedImport(requests, rowNumbers, errors);
    }

    private ParsedImport parseExcel(MultipartFile file) throws IOException {
        List<BankAccountCreateRequest> requests = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                errors.add(new ImportRowError(0, "sheet", "First sheet is missing"));
                return new ParsedImport(requests, rowNumbers, errors);
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                errors.add(new ImportRowError(0, "header", "Missing header row"));
                return new ParsedImport(requests, rowNumbers, errors);
            }

            String[] headers = new String[EXPECTED_HEADERS.size()];
            for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
                headers[i] = getCellValueAsString(headerRow.getCell(i));
            }
            validateHeaders(headers, errors);
            if (!errors.isEmpty()) {
                return new ParsedImport(requests, rowNumbers, errors);
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                int rowNumber = i + 1;
                String[] tokens = new String[EXPECTED_HEADERS.size()];
                for (int col = 0; col < EXPECTED_HEADERS.size(); col++) {
                    tokens[col] = getCellValueAsString(row.getCell(col));
                }
                BankAccountCreateRequest request = mapRow(tokens, rowNumber, errors);
                if (request != null) {
                    requests.add(request);
                    rowNumbers.add(rowNumber);
                }
            }
        }

        return new ParsedImport(requests, rowNumbers, errors);
    }

    private void validateHeaders(String[] actualHeaders, List<ImportRowError> errors) {
        if (actualHeaders.length < EXPECTED_HEADERS.size()) {
            errors.add(
                    new ImportRowError(
                            1,
                            "header",
                            "Header count mismatch. Expected "
                                    + EXPECTED_HEADERS.size()
                                    + " columns"));
            return;
        }
        for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
            String expected = EXPECTED_HEADERS.get(i);
            String actual = actualHeaders[i] != null ? actualHeaders[i].trim().toLowerCase(Locale.ROOT) : "";
            if (!expected.equals(actual)) {
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

    private BankAccountCreateRequest mapRow(String[] tokens, int rowNumber, List<ImportRowError> errors) {
        BankAccountCreateRequest request = new BankAccountCreateRequest();
        int initialErrorSize = errors.size();

        // Column 0: account_number (required)
        String accountNumber = value(tokens, 0);
        if (StringUtils.isBlank(accountNumber)) {
            errors.add(new ImportRowError(rowNumber, "account_number", "Account number is required"));
        }
        request.setAccountNumber(accountNumber);

        // Column 1: bank_name (required)
        request.setBankName(value(tokens, 1));

        // Column 2: branch (optional)
        request.setBranch(value(tokens, 2));

        // Column 3: account_type (required)
        BankAccount.AccountType type = parseAccountType(value(tokens, 3), rowNumber, errors);
        request.setType(type);

        // Column 4: opening_balance (required)
        BigDecimal openingBalance = parseDecimal(value(tokens, 4), rowNumber, errors);
        request.setOpeningBalance(openingBalance);

        // Column 5: gl_account_code (optional - for linking to Chart of Accounts)
        request.setGlAccountCode(value(tokens, 5));

        // Column 6: active (optional, defaults to TRUE)
        Boolean active = parseBoolean(value(tokens, 6), rowNumber, errors);
        request.setActive(active != null ? active : Boolean.TRUE);

        Set<ConstraintViolation<BankAccountCreateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            for (ConstraintViolation<BankAccountCreateRequest> violation : violations) {
                errors.add(
                        new ImportRowError(
                                rowNumber, violation.getPropertyPath().toString(), violation.getMessage()));
            }
            return null;
        }
        if (errors.size() > initialErrorSize) {
            return null;
        }

        return request;
    }

    private BankAccount.AccountType parseAccountType(String value, int rowNumber, List<ImportRowError> errors) {
        if (StringUtils.isBlank(value)) {
            errors.add(new ImportRowError(rowNumber, "account_type", "Account type is required"));
            return null;
        }
        try {
            return BankAccount.AccountType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add(
                    new ImportRowError(
                            rowNumber,
                            "account_type",
                            "Account type must be one of: " + String.join("/", allowedAccountTypes())));
            return null;
        }
    }

    private String allowedAccountTypes() {
        return java.util.Arrays.stream(BankAccount.AccountType.values())
                .map(Enum::name)
                .reduce((a, b) -> a + "/" + b)
                .orElse("");
    }

    private BigDecimal parseDecimal(String value, int rowNumber, List<ImportRowError> errors) {
        if (StringUtils.isBlank(value)) {
            errors.add(new ImportRowError(rowNumber, "opening_balance", "Opening balance is required"));
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(value.trim());
            if (decimal.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(
                        new ImportRowError(
                                rowNumber, "opening_balance", "Opening balance must be non-negative"));
                return null;
            }
            return decimal;
        } catch (NumberFormatException ex) {
            errors.add(
                    new ImportRowError(rowNumber, "opening_balance", "Opening balance must be numeric"));
            return null;
        }
    }

    private Boolean parseBoolean(String value, int rowNumber, List<ImportRowError> errors) {
        if (StringUtils.isBlank(value)) {
            return Boolean.TRUE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y" -> Boolean.TRUE;
            case "false", "0", "no", "n" -> Boolean.FALSE;
            default -> {
                errors.add(
                        new ImportRowError(rowNumber, "active", "Active must be TRUE/FALSE or 1/0"));
                yield null;
            }
        };
    }

    private String value(String[] tokens, int index) {
        if (tokens == null || index >= tokens.length) {
            return null;
        }
        String value = tokens[index];
        return value == null || value.isBlank() ? null : value.trim();
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
        String value = DATA_FORMATTER.formatCellValue(cell);
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private String resolveField(org.springframework.web.server.ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.is4xxClientError()) {
            return "validation";
        }
        return "general";
    }

    private String messageOrDefault(String message) {
        return message != null && !message.isBlank() ? message : "Unexpected error";
    }

    private List<ImportRowAudit> toAuditEntries(List<RowAuditBuilder> builders) {
        return builders.stream().map(RowAuditBuilder::toAudit).toList();
    }

    private static final class RowAuditBuilder {
        private final int rowNumber;
        private final Object beforePayload;
        private final Object afterPayload;
        private String status;
        private final String message;

        private RowAuditBuilder(int rowNumber, Object beforePayload, Object afterPayload, String status,
                String message) {
            this.rowNumber = rowNumber;
            this.beforePayload = beforePayload;
            this.afterPayload = afterPayload;
            this.status = status;
            this.message = message;
        }

        private void markRolledBackIfSuccessful() {
            if (STATUS_SUCCESS.equals(status)) {
                this.status = STATUS_ROLLED_BACK;
            }
        }

        private ImportRowAudit toAudit() {
            return new ImportRowAudit(rowNumber, beforePayload, afterPayload, status, message);
        }
    }

    private record ParsedImport(
            List<BankAccountCreateRequest> requests, List<Integer> rowNumbers, List<ImportRowError> errors) {
    }
}

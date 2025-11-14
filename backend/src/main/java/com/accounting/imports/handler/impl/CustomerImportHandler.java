package com.accounting.imports.handler.impl;

import com.accounting.dto.CustomerCreateRequest;
import com.accounting.imports.ImportType;
import com.accounting.imports.exception.ImportProcessingException;
import com.accounting.imports.exception.ImportValidationException;
import com.accounting.imports.handler.ImportHandler;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportRowAudit;
import com.accounting.imports.model.ImportRowError;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.service.CustomerService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

@Component("customers")
public class CustomerImportHandler implements ImportHandler {

    private static final List<String> EXPECTED_HEADERS = List.of("customer_code", "name", "tax_code", "email", "phone",
            "address", "active");
    private static final Map<String, List<String>> HEADER_ALIASES = Map.of(
            "customer_code", List.of("customer_code", "code"),
            "name", List.of("name"),
            "tax_code", List.of("tax_code", "tax code"),
            "email", List.of("email"),
            "phone", List.of("phone"),
            "address", List.of("address"),
            "active", List.of("active"));
    private static final Set<String> SUPPORTED_FORMATS = Set.of("csv", "xlsx", "xls");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_ERROR = "ERROR";
    private static final String STATUS_ROLLED_BACK = "ROLLED_BACK";
    private static final String STATUS_VALIDATION_ERROR = "VALIDATION_ERROR";

    private final CustomerService customerService;
    private final Validator validator;
    private final ImportErrorReportService errorReportService;

    public CustomerImportHandler(
            CustomerService customerService, Validator validator, ImportErrorReportService errorReportService) {
        this.customerService = customerService;
        this.validator = validator;
        this.errorReportService = errorReportService;
    }

    @Override
    @Transactional
    public ImportSummary handle(MultipartFile file, ImportContext context) {
        String format = detectFormat(file.getOriginalFilename());
        if (!SUPPORTED_FORMATS.contains(format)) {
            throw new ImportValidationException(
                    "Unsupported file format",
                    List.of(
                            new ImportRowError(
                                    0,
                                    "file",
                                    "Supported formats: CSV, XLSX")));
        }

        ParsedImport parsed;
        parsed = parseFile(file, format);

        if (!parsed.errors.isEmpty()) {
            List<ImportRowAudit> auditEntries = parsed.errors.stream()
                    .map(error -> new ImportRowAudit(
                            error.rowNumber(),
                            Map.of("field", error.field()),
                            null,
                            STATUS_VALIDATION_ERROR,
                            error.message()))
                    .toList();
            java.util.UUID reportId = errorReportService.saveReport(
                    ImportType.CUSTOMERS,
                    context.companyId(),
                    context.userId(),
                    context.filename(),
                    parsed.errors);
            throw new ImportValidationException("Validation failed", parsed.errors, reportId, auditEntries);
        }

        // If no requests and no errors, treat as empty data set (no-op success)

        List<ImportRowError> processingErrors = new ArrayList<>();
        int successCount = 0;
        List<RowAuditBuilder> auditBuilders = new ArrayList<>();

        for (int i = 0; i < parsed.requests.size(); i++) {
            CustomerCreateRequest request = parsed.requests.get(i);
            int rowNumber = parsed.rowNumbers.get(i);
            try {
                var createdCustomer = customerService.create(request);
                auditBuilders.add(new RowAuditBuilder(rowNumber, request, createdCustomer, STATUS_SUCCESS, null));
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
                    ImportType.CUSTOMERS,
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
        List<CustomerCreateRequest> requests = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();

        // CRITICAL: Use getInputStream() directly - do NOT call getBytes() first as it
        // consumes the stream!
        // NOTE: MockMultipartFile.getInputStream() can only be read once!
        // If we already read it in handle() to check if empty, we need to get a fresh
        // stream
        java.io.InputStream inputStream = file.getInputStream();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                errors.add(new ImportRowError(0, "header", "Missing header row"));
                return new ParsedImport(requests, rowNumbers, errors);
            }
            // Trim whitespace including \r from Windows line endings
            headerLine = headerLine.trim();
            String[] headerTokens = splitCsvRow(headerLine);
            validateHeaders(headerTokens, errors);
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
                CustomerCreateRequest request = mapRow(tokens, rowNumber, errors);
                if (request != null) {
                    requests.add(request);
                    rowNumbers.add(rowNumber);
                } else {
                }
            }
        }
        return new ParsedImport(requests, rowNumbers, errors);
    }

    private ParsedImport parseExcel(MultipartFile file) throws IOException {
        List<CustomerCreateRequest> requests = new ArrayList<>();
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
                CustomerCreateRequest request = mapRow(tokens, rowNumber, errors);
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
            List<String> aliases = HEADER_ALIASES.getOrDefault(expected, List.of(expected));
            if (!aliases.contains(actual)) {
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

    private CustomerCreateRequest mapRow(String[] tokens, int rowNumber, List<ImportRowError> errors) {
        CustomerCreateRequest request = new CustomerCreateRequest();
        int initialErrorSize = errors.size();

        request.setCode(value(tokens, 0));
        request.setName(value(tokens, 1));
        request.setTaxCode(value(tokens, 2));
        request.setEmail(value(tokens, 3));
        request.setPhone(value(tokens, 4));
        request.setAddress(value(tokens, 5));
        Boolean active = parseBoolean(value(tokens, 6), rowNumber, errors);
        request.setActive(active != null ? active : Boolean.TRUE);

        Set<ConstraintViolation<CustomerCreateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            for (ConstraintViolation<CustomerCreateRequest> violation : violations) {
                errors.add(
                        new ImportRowError(
                                rowNumber, violation.getPropertyPath().toString(), violation.getMessage()));
            }
            return null;
        }
        if (StringUtils.isBlank(request.getName())) {
            errors.add(new ImportRowError(rowNumber, "name", "Name is required"));
            return null;
        }
        if (errors.size() > initialErrorSize) {
            return null;
        }

        return request;
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
            List<CustomerCreateRequest> requests, List<Integer> rowNumbers, List<ImportRowError> errors) {
    }
}

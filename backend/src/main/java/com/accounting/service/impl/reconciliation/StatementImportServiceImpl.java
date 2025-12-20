package com.accounting.service.impl.reconciliation;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.reconciliation.BankStatementFormatDTO;
import com.accounting.dto.reconciliation.ColumnMappingSuggestionDTO;
import com.accounting.dto.reconciliation.ImportErrorDTO;
import com.accounting.dto.reconciliation.StatementImportRequestDTO;
import com.accounting.dto.reconciliation.StatementImportResultDTO;
import com.accounting.entity.reconciliation.BankReconciliation;
import com.accounting.entity.reconciliation.BankStatementFormat;
import com.accounting.entity.reconciliation.BankStatementLine;
import com.accounting.entity.reconciliation.MatchStatus;
import com.accounting.entity.reconciliation.ReconciliationStatus;
import com.accounting.exception.BusinessException;
import com.accounting.repository.reconciliation.BankReconciliationRepository;
import com.accounting.repository.reconciliation.BankStatementFormatRepository;
import com.accounting.repository.reconciliation.BankStatementLineRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.StatementImportService;

/**
 * Implementation of StatementImportService for bank statement file parsing.
 */
@Service
public class StatementImportServiceImpl implements StatementImportService {

    private static final Logger log = LoggerFactory.getLogger(StatementImportServiceImpl.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // Common header patterns for auto-detection
    private static final List<String> DATE_PATTERNS = Arrays.asList(
            "date", "transaction date", "value date", "posting date", "ngày", "ngày giao dịch");
    private static final List<String> DESCRIPTION_PATTERNS = Arrays.asList(
            "description", "particulars", "narration", "details", "diễn giải", "nội dung");
    private static final List<String> REFERENCE_PATTERNS = Arrays.asList(
            "reference", "ref", "cheque", "check", "document", "số ct", "số chứng từ");
    private static final List<String> DEBIT_PATTERNS = Arrays.asList(
            "debit", "withdrawal", "dr", "chi", "rút tiền");
    private static final List<String> CREDIT_PATTERNS = Arrays.asList(
            "credit", "deposit", "cr", "có", "nạp tiền");
    private static final List<String> BALANCE_PATTERNS = Arrays.asList(
            "balance", "running balance", "closing balance", "số dư");

    /**
     * FIXME: Replace in-memory error report storage with Redis or database for production.
     *
     * Current limitations:
     * 1. Data loss on server restart - all error reports are lost
     * 2. No TTL - reports never expire, potential memory leak over time
     * 3. No multi-instance support - user may get "report not found" if routed to different instance
     * 4. No persistence - cannot audit what errors occurred in past imports
     *
     * Recommended production implementation:
     * - Use Redis with 1-hour TTL for error reports
     * - Or persist to database with scheduled cleanup job
     * - Consider storing in S3/MinIO for large error files
     *
     * @see accounting-p1l for future enhancement tracking
     */
    private final Map<String, List<ImportErrorDTO>> errorReportStore = new ConcurrentHashMap<>();

    private final BankReconciliationRepository reconciliationRepository;
    private final BankStatementLineRepository statementLineRepository;
    private final BankStatementFormatRepository formatRepository;

    public StatementImportServiceImpl(
            BankReconciliationRepository reconciliationRepository,
            BankStatementLineRepository statementLineRepository,
            BankStatementFormatRepository formatRepository) {
        this.reconciliationRepository = reconciliationRepository;
        this.statementLineRepository = statementLineRepository;
        this.formatRepository = formatRepository;
    }

    @Override
    public ColumnMappingSuggestionDTO analyzeFileHeaders(UUID reconciliationId, InputStream fileInputStream,
            String fileName) {
        Long companyId = CompanyContext.getCompanyId();
        BankReconciliation reconciliation = reconciliationRepository.findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new BusinessException("Reconciliation not found"));

        try {
            byte[] fileBytes = readFileBytes(fileInputStream);
            List<String> headers = extractHeaders(new ByteArrayInputStream(fileBytes), fileName);

            ColumnMappingSuggestionDTO suggestion = new ColumnMappingSuggestionDTO();
            suggestion.setHeaders(headers);

            // Auto-detect columns based on header patterns
            suggestion.setSuggestedDateColumn(findMatchingColumn(headers, DATE_PATTERNS));
            suggestion.setSuggestedDescriptionColumn(findMatchingColumn(headers, DESCRIPTION_PATTERNS));
            suggestion.setSuggestedReferenceColumn(findMatchingColumn(headers, REFERENCE_PATTERNS));
            suggestion.setSuggestedDebitColumn(findMatchingColumn(headers, DEBIT_PATTERNS));
            suggestion.setSuggestedCreditColumn(findMatchingColumn(headers, CREDIT_PATTERNS));
            suggestion.setSuggestedBalanceColumn(findMatchingColumn(headers, BALANCE_PATTERNS));
            suggestion.setSuggestedDateFormat(detectDateFormat(new ByteArrayInputStream(fileBytes), fileName,
                    suggestion.getSuggestedDateColumn()));

            // Check for saved profile
            Optional<BankStatementFormat> savedFormat = formatRepository
                    .findByCompanyIdAndBankAccountId(companyId, reconciliation.getBankAccountId());
            if (savedFormat.isPresent()) {
                suggestion.setHasSavedProfile(true);
                suggestion.setSavedProfile(mapToFormatDTO(savedFormat.get()));
            }

            return suggestion;
        } catch (IOException e) {
            log.error("Failed to analyze file headers", e);
            throw new BusinessException("Failed to analyze file: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public StatementImportResultDTO importStatement(UUID reconciliationId, InputStream fileInputStream, String fileName,
            StatementImportRequestDTO importRequest) {
        Long companyId = CompanyContext.getCompanyId();
        BankReconciliation reconciliation = reconciliationRepository.findByCompanyIdAndId(companyId, reconciliationId)
                .orElseThrow(() -> new BusinessException("Reconciliation not found"));

        if (reconciliation.getStatus() == ReconciliationStatus.COMPLETED) {
            throw new BusinessException("Cannot import to completed reconciliation");
        }

        StatementImportResultDTO result = new StatementImportResultDTO();
        List<ImportErrorDTO> errors = new ArrayList<>();

        try {
            byte[] fileBytes = readFileBytes(fileInputStream);

            // Calculate file hash for duplicate detection
            String fileHash = calculateFileHash(new ByteArrayInputStream(fileBytes));
            result.setFileHash(fileHash);

            // Check for duplicate
            if (isDuplicateFile(companyId, fileHash)) {
                Optional<BankReconciliation> existing = reconciliationRepository
                        .findByCompanyIdAndStatementFileHash(companyId, fileHash);
                result.setDuplicateDetected(true);
                result.setDuplicateReconciliationId(existing.map(r -> r.getId().toString()).orElse(null));
                result.setSuccess(false);
                return result;
            }

            // Clear existing lines if re-importing
            statementLineRepository.deleteByReconciliationId(reconciliationId);

            // Parse file based on extension
            List<Map<String, String>> rows = parseFile(new ByteArrayInputStream(fileBytes), fileName,
                    importRequest.getSkipHeaderRows());

            result.setTotalRows(rows.size());

            // Validate and create statement lines
            List<BankStatementLine> validLines = new ArrayList<>();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(importRequest.getDateFormat());
            int lineNumber = 0;

            for (Map<String, String> row : rows) {
                lineNumber++;
                try {
                    BankStatementLine line = parseStatementLine(row, importRequest, dateFormatter, lineNumber,
                            reconciliation);
                    validLines.add(line);
                } catch (Exception e) {
                    errors.add(new ImportErrorDTO(lineNumber, "row", row.toString(), e.getMessage()));
                }
            }

            // Atomic import: only save if no errors
            if (!errors.isEmpty()) {
                result.setSuccess(false);
                result.setErrorRows(errors.size());
                result.setErrors(errors);
                result.setImportedRows(0);

                // Store error report for download
                String errorReportId = UUID.randomUUID().toString();
                errorReportStore.put(errorReportId, errors);
                result.setErrorReportId(errorReportId);
            } else {
                statementLineRepository.saveAll(validLines);

                // Update reconciliation
                reconciliation.setStatementFileHash(fileHash);
                reconciliation.setStatus(ReconciliationStatus.IN_PROGRESS);
                reconciliationRepository.save(reconciliation);

                result.setSuccess(true);
                result.setImportedRows(validLines.size());

                // Save format profile if requested
                if (Boolean.TRUE.equals(importRequest.getSaveFormatProfile())) {
                    saveFormatProfile(reconciliation.getBankAccountId(), importRequest);
                }

                log.info("Statement imported successfully: reconciliationId={}, rows={}, fileHash={}",
                        reconciliationId, validLines.size(), fileHash);
            }

            return result;
        } catch (IOException e) {
            log.error("Failed to import statement", e);
            throw new BusinessException("Failed to import file: " + e.getMessage());
        }
    }

    @Override
    public String calculateFileHash(InputStream fileInputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new BusinessException("Failed to calculate file hash: " + e.getMessage());
        }
    }

    @Override
    public boolean isDuplicateFile(Long companyId, String fileHash) {
        return reconciliationRepository.existsByCompanyIdAndStatementFileHash(companyId, fileHash);
    }

    @Override
    public BankStatementFormatDTO getFormatProfile(Long bankAccountId) {
        Long companyId = CompanyContext.getCompanyId();
        return formatRepository.findByCompanyIdAndBankAccountId(companyId, bankAccountId)
                .map(this::mapToFormatDTO)
                .orElse(null);
    }

    @Override
    @Transactional
    public BankStatementFormatDTO saveFormatProfile(Long bankAccountId, StatementImportRequestDTO format) {
        Long companyId = CompanyContext.getCompanyId();

        BankStatementFormat entity = formatRepository.findByCompanyIdAndBankAccountId(companyId, bankAccountId)
                .orElse(new BankStatementFormat());

        entity.setCompanyId(companyId);
        entity.setBankAccountId(bankAccountId);
        entity.setFormatName(format.getFormatProfileName());
        entity.setDateColumn(format.getDateColumn());
        entity.setDescriptionColumn(format.getDescriptionColumn());
        entity.setReferenceColumn(format.getReferenceColumn());
        entity.setDebitColumn(format.getDebitColumn());
        entity.setCreditColumn(format.getCreditColumn());
        entity.setBalanceColumn(format.getBalanceColumn());
        entity.setDateFormat(format.getDateFormat());
        entity.setSkipHeaderRows(format.getSkipHeaderRows());

        entity = formatRepository.save(entity);
        return mapToFormatDTO(entity);
    }

    @Override
    @Transactional
    public void deleteFormatProfile(Long bankAccountId) {
        Long companyId = CompanyContext.getCompanyId();
        formatRepository.deleteByCompanyIdAndBankAccountId(companyId, bankAccountId);
    }

    @Override
    public byte[] downloadErrorReport(String errorReportId) {
        List<ImportErrorDTO> errors = errorReportStore.get(errorReportId);
        if (errors == null) {
            throw new BusinessException("Error report not found or expired");
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Row,Field,Value,Error\n");
        for (ImportErrorDTO error : errors) {
            csv.append(error.getRowNumber()).append(",")
                    .append(escapeCsv(error.getField())).append(",")
                    .append(escapeCsv(error.getValue())).append(",")
                    .append(escapeCsv(error.getErrorMessage())).append("\n");
        }

        // Clean up after download to free memory (single-use report)
        errorReportStore.remove(errorReportId);
        log.debug("Error report {} downloaded and removed from memory", errorReportId);

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ========== Private Helper Methods ==========

    private byte[] readFileBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int totalRead = 0;
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            totalRead += bytesRead;
            if (totalRead > MAX_FILE_SIZE) {
                throw new BusinessException("File exceeds maximum size of 10MB");
            }
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private List<String> extractHeaders(InputStream inputStream, String fileName) throws IOException {
        if (isExcelFile(fileName)) {
            return extractExcelHeaders(inputStream);
        } else {
            return extractCsvHeaders(inputStream);
        }
    }

    private List<String> extractCsvHeaders(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(false).build()
                        .parse(reader)) {
            return new ArrayList<>(parser.getHeaderNames());
        }
    }

    private List<String> extractExcelHeaders(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return new ArrayList<>();
            }

            List<String> headers = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell));
            }
            return headers;
        }
    }

    private List<Map<String, String>> parseFile(InputStream inputStream, String fileName, int skipRows)
            throws IOException {
        if (isExcelFile(fileName)) {
            return parseExcelFile(inputStream, skipRows);
        } else {
            return parseCsvFile(inputStream, skipRows);
        }
    }

    private List<Map<String, String>> parseCsvFile(InputStream inputStream, int skipRows) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
                        .parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            for (CSVRecord record : parser) {
                Map<String, String> row = new HashMap<>();
                for (String header : headers) {
                    row.put(header, record.get(header));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parseExcelFile(InputStream inputStream, int skipRows) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(skipRows - 1 >= 0 ? skipRows - 1 : 0);
            if (headerRow == null) {
                return rows;
            }

            List<String> headers = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell));
            }

            for (int i = skipRows; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Map<String, String> rowData = new HashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    String value = cell != null ? formatter.formatCellValue(cell) : "";
                    rowData.put(headers.get(j), value);
                }
                rows.add(rowData);
            }
        }
        return rows;
    }

    private BankStatementLine parseStatementLine(Map<String, String> row, StatementImportRequestDTO config,
            DateTimeFormatter dateFormatter, int lineNumber, BankReconciliation reconciliation) {
        BankStatementLine line = new BankStatementLine();
        line.setReconciliation(reconciliation);
        line.setLineNumber(lineNumber);

        // Parse date (required)
        String dateValue = row.get(config.getDateColumn());
        if (dateValue == null || dateValue.isBlank()) {
            throw new IllegalArgumentException("Date is required");
        }
        try {
            line.setTransactionDate(LocalDate.parse(dateValue.trim(), dateFormatter));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + dateValue);
        }

        // Parse optional fields
        if (config.getDescriptionColumn() != null) {
            line.setDescription(row.get(config.getDescriptionColumn()));
        }
        if (config.getReferenceColumn() != null) {
            line.setReference(row.get(config.getReferenceColumn()));
        }

        // Parse amounts
        BigDecimal debit = parseAmount(row.get(config.getDebitColumn()));
        BigDecimal credit = parseAmount(row.get(config.getCreditColumn()));
        BigDecimal balance = parseAmount(row.get(config.getBalanceColumn()));

        line.setDebitAmount(debit != null ? debit : BigDecimal.ZERO);
        line.setCreditAmount(credit != null ? credit : BigDecimal.ZERO);
        line.setBalance(balance);
        line.setMatchStatus(MatchStatus.UNMATCHED);

        return line;
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // Remove currency symbols, commas, spaces
            String cleaned = value.replaceAll("[^\\d.-]", "");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String findMatchingColumn(List<String> headers, List<String> patterns) {
        for (String header : headers) {
            String lowerHeader = header.toLowerCase().trim();
            for (String pattern : patterns) {
                if (lowerHeader.contains(pattern)) {
                    return header;
                }
            }
        }
        return null;
    }

    private String detectDateFormat(InputStream inputStream, String fileName, String dateColumn) {
        if (dateColumn == null) {
            return "yyyy-MM-dd";
        }

        // Common date formats in order of likelihood for Vietnamese banks
        List<String> candidateFormats = Arrays.asList(
                "dd/MM/yyyy",   // Vietcombank, BIDV, most Vietnamese banks
                "dd-MM-yyyy",   // Techcombank
                "yyyy-MM-dd",   // ISO format (some international banks)
                "MM/dd/yyyy",   // US format (some international banks)
                "dd.MM.yyyy",   // European format
                "yyyy/MM/dd"    // Alternative ISO format
        );

        try {
            // Extract sample date values from the file
            List<String> sampleDates = extractSampleDateValues(inputStream, fileName, dateColumn, 10);

            if (sampleDates.isEmpty()) {
                log.debug("No date samples found for column '{}', using default format", dateColumn);
                return "yyyy-MM-dd";
            }

            // Try each format and return the first one that parses all samples
            for (String format : candidateFormats) {
                if (tryParseAllDates(sampleDates, format)) {
                    log.debug("Detected date format '{}' for column '{}'", format, dateColumn);
                    return format;
                }
            }

            log.debug("No matching date format found for column '{}', using default", dateColumn);
            return "yyyy-MM-dd";
        } catch (IOException e) {
            log.warn("Error detecting date format, using default: {}", e.getMessage());
            return "yyyy-MM-dd";
        }
    }

    private List<String> extractSampleDateValues(InputStream inputStream, String fileName, String dateColumn,
            int maxSamples) throws IOException {
        List<String> samples = new ArrayList<>();

        if (isExcelFile(fileName)) {
            samples = extractExcelDateSamples(inputStream, dateColumn, maxSamples);
        } else {
            samples = extractCsvDateSamples(inputStream, dateColumn, maxSamples);
        }

        return samples;
    }

    private List<String> extractCsvDateSamples(InputStream inputStream, String dateColumn, int maxSamples)
            throws IOException {
        List<String> samples = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
                        .parse(reader)) {

            for (CSVRecord record : parser) {
                if (samples.size() >= maxSamples) {
                    break;
                }
                try {
                    String value = record.get(dateColumn);
                    if (value != null && !value.isBlank()) {
                        samples.add(value.trim());
                    }
                } catch (IllegalArgumentException e) {
                    // Column not found, skip
                    break;
                }
            }
        }
        return samples;
    }

    private List<String> extractExcelDateSamples(InputStream inputStream, String dateColumn, int maxSamples)
            throws IOException {
        List<String> samples = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return samples;
            }

            // Find the column index for the date column
            int dateColumnIndex = -1;
            DataFormatter formatter = new DataFormatter();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null && dateColumn.equals(formatter.formatCellValue(cell))) {
                    dateColumnIndex = i;
                    break;
                }
            }

            if (dateColumnIndex == -1) {
                return samples;
            }

            // Extract sample values from data rows
            for (int i = 1; i <= sheet.getLastRowNum() && samples.size() < maxSamples; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell cell = row.getCell(dateColumnIndex);
                if (cell != null) {
                    String value;
                    // Handle Excel date cells specially
                    if (cell.getCellType() == CellType.NUMERIC
                            && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                        // Excel stores dates as numbers, format as ISO date
                        java.util.Date date = cell.getDateCellValue();
                        value = new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
                    } else {
                        value = formatter.formatCellValue(cell);
                    }
                    if (value != null && !value.isBlank()) {
                        samples.add(value.trim());
                    }
                }
            }
        }
        return samples;
    }

    private boolean tryParseAllDates(List<String> dateStrings, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        for (String dateStr : dateStrings) {
            try {
                LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                return false;
            }
        }
        return true;
    }

    private boolean isExcelFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private BankStatementFormatDTO mapToFormatDTO(BankStatementFormat entity) {
        BankStatementFormatDTO dto = new BankStatementFormatDTO();
        dto.setId(entity.getId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setBankAccountId(entity.getBankAccountId());
        dto.setFormatName(entity.getFormatName());
        dto.setDateColumn(entity.getDateColumn());
        dto.setDescriptionColumn(entity.getDescriptionColumn());
        dto.setReferenceColumn(entity.getReferenceColumn());
        dto.setDebitColumn(entity.getDebitColumn());
        dto.setCreditColumn(entity.getCreditColumn());
        dto.setBalanceColumn(entity.getBalanceColumn());
        dto.setDateFormat(entity.getDateFormat());
        dto.setSkipHeaderRows(entity.getSkipHeaderRows());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

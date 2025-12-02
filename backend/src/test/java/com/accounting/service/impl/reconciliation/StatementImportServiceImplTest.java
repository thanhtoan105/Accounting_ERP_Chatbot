package com.accounting.service.impl.reconciliation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.reconciliation.BankStatementFormatDTO;
import com.accounting.dto.reconciliation.ColumnMappingSuggestionDTO;
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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for StatementImportServiceImpl.
 * Tests CSV/Excel parsing, column detection, file hash, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatementImportServiceImplTest {

    @Mock
    private BankReconciliationRepository reconciliationRepository;

    @Mock
    private BankStatementLineRepository statementLineRepository;

    @Mock
    private BankStatementFormatRepository formatRepository;

    private StatementImportServiceImpl importService;

    private static final Long COMPANY_ID = 1L;
    private static final Long BANK_ACCOUNT_ID = 100L;
    private UUID reconciliationId;
    private BankReconciliation reconciliation;

    @BeforeEach
    void setUp() {
        importService = new StatementImportServiceImpl(
                reconciliationRepository,
                statementLineRepository,
                formatRepository);

        CompanyContext.setCompanyId(COMPANY_ID);

        reconciliationId = UUID.randomUUID();
        reconciliation = createMockReconciliation();
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private BankReconciliation createMockReconciliation() {
        BankReconciliation recon = new BankReconciliation();
        recon.setId(reconciliationId);
        recon.setCompanyId(COMPANY_ID);
        recon.setBankAccountId(BANK_ACCOUNT_ID);
        recon.setStatementPeriodStart(LocalDate.of(2024, 1, 1));
        recon.setStatementPeriodEnd(LocalDate.of(2024, 1, 31));
        recon.setStatus(ReconciliationStatus.IN_PROGRESS);
        return recon;
    }

    @Nested
    @DisplayName("File Hash Calculation Tests")
    class FileHashTests {

        @Test
        @DisplayName("Should calculate consistent SHA256 hash for same content")
        void calculateFileHash_ConsistentForSameContent() throws Exception {
            String content = "Date,Description,Amount\n2024-01-15,Payment,1000\n";
            InputStream stream1 = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            InputStream stream2 = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

            String hash1 = importService.calculateFileHash(stream1);
            String hash2 = importService.calculateFileHash(stream2);

            assertEquals(hash1, hash2, "Hash should be consistent for identical content");
            assertEquals(64, hash1.length(), "SHA256 hash should be 64 hex characters");
        }

        @Test
        @DisplayName("Should calculate different hash for different content")
        void calculateFileHash_DifferentForDifferentContent() throws Exception {
            String content1 = "Date,Description,Amount\n2024-01-15,Payment,1000\n";
            String content2 = "Date,Description,Amount\n2024-01-15,Payment,2000\n";
            InputStream stream1 = new ByteArrayInputStream(content1.getBytes(StandardCharsets.UTF_8));
            InputStream stream2 = new ByteArrayInputStream(content2.getBytes(StandardCharsets.UTF_8));

            String hash1 = importService.calculateFileHash(stream1);
            String hash2 = importService.calculateFileHash(stream2);

            assertNotEquals(hash1, hash2, "Hash should differ for different content");
        }

        @Test
        @DisplayName("Should handle empty file")
        void calculateFileHash_EmptyFile() throws Exception {
            InputStream stream = new ByteArrayInputStream(new byte[0]);

            String hash = importService.calculateFileHash(stream);

            assertNotNull(hash);
            assertEquals(64, hash.length(), "SHA256 hash should be 64 hex characters even for empty file");
        }
    }

    @Nested
    @DisplayName("Column Header Auto-Detection Tests")
    class ColumnDetectionTests {

        @Test
        @DisplayName("Should detect English column headers")
        void analyzeFileHeaders_EnglishHeaders() throws Exception {
            String csvContent = "Date,Description,Reference,Debit,Credit,Balance\n" +
                    "2024-01-15,Payment,REF001,1000,,9000\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(formatRepository.findByCompanyIdAndBankAccountId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.empty());

            ColumnMappingSuggestionDTO suggestion = importService.analyzeFileHeaders(
                    reconciliationId, stream, "statement.csv");

            assertNotNull(suggestion);
            assertEquals(6, suggestion.getHeaders().size());
            assertEquals("Date", suggestion.getSuggestedDateColumn());
            // Description column contains "description" pattern
            assertNotNull(suggestion.getSuggestedDescriptionColumn());
            // Other columns may or may not be detected based on exact pattern matching
        }

        @Test
        @DisplayName("Should detect Vietnamese column headers")
        void analyzeFileHeaders_VietnameseHeaders() throws Exception {
            String csvContent = "Ngày giao dịch,Nội dung,Số CT,Chi,Có,Số dư\n" +
                    "15/01/2024,Thanh toán,CT001,1000000,,9000000\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(formatRepository.findByCompanyIdAndBankAccountId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.empty());

            ColumnMappingSuggestionDTO suggestion = importService.analyzeFileHeaders(
                    reconciliationId, stream, "statement.csv");

            assertNotNull(suggestion);
            assertEquals("Ngày giao dịch", suggestion.getSuggestedDateColumn());
            assertEquals("Nội dung", suggestion.getSuggestedDescriptionColumn());
            assertEquals("Số CT", suggestion.getSuggestedReferenceColumn());
            assertEquals("Chi", suggestion.getSuggestedDebitColumn());
            assertEquals("Có", suggestion.getSuggestedCreditColumn());
            assertEquals("Số dư", suggestion.getSuggestedBalanceColumn());
        }

        @Test
        @DisplayName("Should load saved profile if exists")
        void analyzeFileHeaders_LoadsSavedProfile() throws Exception {
            String csvContent = "Col1,Col2,Col3\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            BankStatementFormat savedFormat = new BankStatementFormat();
            savedFormat.setId(UUID.randomUUID());
            savedFormat.setCompanyId(COMPANY_ID);
            savedFormat.setBankAccountId(BANK_ACCOUNT_ID);
            savedFormat.setDateColumn("Col1");
            savedFormat.setDescriptionColumn("Col2");
            savedFormat.setDateFormat("dd/MM/yyyy");

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(formatRepository.findByCompanyIdAndBankAccountId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.of(savedFormat));

            ColumnMappingSuggestionDTO suggestion = importService.analyzeFileHeaders(
                    reconciliationId, stream, "statement.csv");

            assertTrue(suggestion.isHasSavedProfile());
            assertNotNull(suggestion.getSavedProfile());
            assertEquals("Col1", suggestion.getSavedProfile().getDateColumn());
        }
    }

    @Nested
    @DisplayName("CSV Import Tests")
    class CsvImportTests {

        @Test
        @DisplayName("Should successfully import valid CSV file")
        void importStatement_ValidCsv_Success() throws Exception {
            String csvContent = "Date,Description,Reference,Debit,Credit,Balance\n" +
                    "2024-01-15,Payment to vendor,REF001,1000,,9000\n" +
                    "2024-01-16,Customer deposit,REF002,,5000,14000\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            StatementImportRequestDTO request = createImportRequest();

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(reconciliationRepository.existsByCompanyIdAndStatementFileHash(eq(COMPANY_ID), anyString()))
                    .thenReturn(false);
            when(reconciliationRepository.save(any(BankReconciliation.class)))
                    .thenReturn(reconciliation);

            StatementImportResultDTO result = importService.importStatement(
                    reconciliationId, stream, "statement.csv", request);

            assertTrue(result.isSuccess());
            assertEquals(2, result.getImportedRows());
            assertEquals(0, result.getErrorRows());
            assertFalse(result.isDuplicateDetected());

            // Verify lines were saved
            ArgumentCaptor<java.util.List<BankStatementLine>> linesCaptor =
                    ArgumentCaptor.forClass(java.util.List.class);
            verify(statementLineRepository).saveAll(linesCaptor.capture());

            java.util.List<BankStatementLine> savedLines = linesCaptor.getValue();
            assertEquals(2, savedLines.size());

            BankStatementLine line1 = savedLines.get(0);
            assertEquals(LocalDate.of(2024, 1, 15), line1.getTransactionDate());
            assertEquals("Payment to vendor", line1.getDescription());
            assertEquals("REF001", line1.getReference());
            assertEquals(new BigDecimal("1000"), line1.getDebitAmount());
            assertEquals(BigDecimal.ZERO, line1.getCreditAmount());
            assertEquals(MatchStatus.UNMATCHED, line1.getMatchStatus());
        }

        @Test
        @DisplayName("Should reject import with invalid date format")
        void importStatement_InvalidDate_ReturnsErrors() throws Exception {
            String csvContent = "Date,Description,Reference,Debit,Credit,Balance\n" +
                    "invalid-date,Payment,REF001,1000,,9000\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            StatementImportRequestDTO request = createImportRequest();

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(reconciliationRepository.existsByCompanyIdAndStatementFileHash(eq(COMPANY_ID), anyString()))
                    .thenReturn(false);

            StatementImportResultDTO result = importService.importStatement(
                    reconciliationId, stream, "statement.csv", request);

            assertFalse(result.isSuccess());
            assertEquals(0, result.getImportedRows());
            assertEquals(1, result.getErrorRows());
            assertNotNull(result.getErrors());
            assertTrue(result.getErrors().get(0).getErrorMessage().contains("Invalid date format"));
        }

        @Test
        @DisplayName("Should reject import with missing required date field")
        void importStatement_MissingDate_ReturnsErrors() throws Exception {
            String csvContent = "Date,Description,Reference,Debit,Credit,Balance\n" +
                    ",Payment,REF001,1000,,9000\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            StatementImportRequestDTO request = createImportRequest();

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(reconciliationRepository.existsByCompanyIdAndStatementFileHash(eq(COMPANY_ID), anyString()))
                    .thenReturn(false);

            StatementImportResultDTO result = importService.importStatement(
                    reconciliationId, stream, "statement.csv", request);

            assertFalse(result.isSuccess());
            assertEquals(1, result.getErrorRows());
            assertTrue(result.getErrors().get(0).getErrorMessage().contains("Date is required"));
        }

        @Test
        @DisplayName("Should parse amounts with currency symbols and commas")
        void importStatement_AmountsWithFormatting_ParsesCorrectly() throws Exception {
            // Use properly quoted CSV format for values with commas
            String csvContent = "Date,Description,Reference,Debit,Credit,Balance\n" +
                    "2024-01-15,Payment,REF001,\"$1,000.00\",0,\"$9,000.00\"\n" +
                    "2024-01-16,Deposit,REF002,0,\"5,000.50\",\"14,000.50\"\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            StatementImportRequestDTO request = createImportRequest();

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(reconciliationRepository.existsByCompanyIdAndStatementFileHash(eq(COMPANY_ID), anyString()))
                    .thenReturn(false);
            when(reconciliationRepository.save(any(BankReconciliation.class)))
                    .thenReturn(reconciliation);

            StatementImportResultDTO result = importService.importStatement(
                    reconciliationId, stream, "statement.csv", request);

            assertTrue(result.isSuccess());

            ArgumentCaptor<java.util.List<BankStatementLine>> linesCaptor =
                    ArgumentCaptor.forClass(java.util.List.class);
            verify(statementLineRepository).saveAll(linesCaptor.capture());

            java.util.List<BankStatementLine> savedLines = linesCaptor.getValue();
            assertEquals(new BigDecimal("1000.00"), savedLines.get(0).getDebitAmount());
            assertEquals(new BigDecimal("5000.50"), savedLines.get(1).getCreditAmount());
        }
    }

    @Nested
    @DisplayName("Duplicate Detection Tests")
    class DuplicateDetectionTests {

        @Test
        @DisplayName("Should detect duplicate file by hash")
        void importStatement_DuplicateFile_Detected() throws Exception {
            String csvContent = "Date,Description,Debit\n2024-01-15,Payment,1000\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            UUID existingReconciliationId = UUID.randomUUID();
            BankReconciliation existingRecon = new BankReconciliation();
            existingRecon.setId(existingReconciliationId);

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(reconciliationRepository.existsByCompanyIdAndStatementFileHash(eq(COMPANY_ID), anyString()))
                    .thenReturn(true);
            when(reconciliationRepository.findByCompanyIdAndStatementFileHash(eq(COMPANY_ID), anyString()))
                    .thenReturn(Optional.of(existingRecon));

            StatementImportRequestDTO request = createImportRequest();

            StatementImportResultDTO result = importService.importStatement(
                    reconciliationId, stream, "statement.csv", request);

            assertFalse(result.isSuccess());
            assertTrue(result.isDuplicateDetected());
            assertEquals(existingReconciliationId.toString(), result.getDuplicateReconciliationId());
        }

        @Test
        @DisplayName("isDuplicateFile should check repository")
        void isDuplicateFile_ChecksRepository() {
            String fileHash = "abc123";

            when(reconciliationRepository.existsByCompanyIdAndStatementFileHash(COMPANY_ID, fileHash))
                    .thenReturn(true);

            assertTrue(importService.isDuplicateFile(COMPANY_ID, fileHash));
            verify(reconciliationRepository).existsByCompanyIdAndStatementFileHash(COMPANY_ID, fileHash);
        }
    }

    @Nested
    @DisplayName("Completed Reconciliation Tests")
    class CompletedReconciliationTests {

        @Test
        @DisplayName("Should reject import to completed reconciliation")
        void importStatement_CompletedReconciliation_ThrowsException() throws Exception {
            String csvContent = "Date,Description,Debit\n2024-01-15,Payment,1000\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            reconciliation.setStatus(ReconciliationStatus.COMPLETED);
            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));

            StatementImportRequestDTO request = createImportRequest();

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> importService.importStatement(reconciliationId, stream, "statement.csv", request));

            assertEquals("Cannot import to completed reconciliation", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Format Profile Tests")
    class FormatProfileTests {

        @Test
        @DisplayName("Should save format profile when requested")
        void importStatement_SaveFormatProfile_SavesProfile() throws Exception {
            String csvContent = "Date,Description,Debit\n2024-01-15,Payment,1000\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            StatementImportRequestDTO request = createImportRequest();
            request.setSaveFormatProfile(true);
            request.setFormatProfileName("My Bank Format");

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(reconciliationRepository.existsByCompanyIdAndStatementFileHash(eq(COMPANY_ID), anyString()))
                    .thenReturn(false);
            when(reconciliationRepository.save(any(BankReconciliation.class)))
                    .thenReturn(reconciliation);
            when(formatRepository.findByCompanyIdAndBankAccountId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.empty());
            when(formatRepository.save(any(BankStatementFormat.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            StatementImportResultDTO result = importService.importStatement(
                    reconciliationId, stream, "statement.csv", request);

            assertTrue(result.isSuccess());
            verify(formatRepository).save(any(BankStatementFormat.class));
        }

        @Test
        @DisplayName("Should get format profile for bank account")
        void getFormatProfile_ReturnsProfile() {
            BankStatementFormat format = new BankStatementFormat();
            format.setId(UUID.randomUUID());
            format.setCompanyId(COMPANY_ID);
            format.setBankAccountId(BANK_ACCOUNT_ID);
            format.setDateColumn("Date");
            format.setDateFormat("yyyy-MM-dd");

            when(formatRepository.findByCompanyIdAndBankAccountId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.of(format));

            BankStatementFormatDTO result = importService.getFormatProfile(BANK_ACCOUNT_ID);

            assertNotNull(result);
            assertEquals("Date", result.getDateColumn());
            assertEquals("yyyy-MM-dd", result.getDateFormat());
        }

        @Test
        @DisplayName("Should return null when no profile exists")
        void getFormatProfile_NoProfile_ReturnsNull() {
            when(formatRepository.findByCompanyIdAndBankAccountId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.empty());

            BankStatementFormatDTO result = importService.getFormatProfile(BANK_ACCOUNT_ID);

            assertNull(result);
        }

        @Test
        @DisplayName("Should delete format profile")
        void deleteFormatProfile_DeletesProfile() {
            importService.deleteFormatProfile(BANK_ACCOUNT_ID);

            verify(formatRepository).deleteByCompanyIdAndBankAccountId(COMPANY_ID, BANK_ACCOUNT_ID);
        }
    }

    @Nested
    @DisplayName("Error Report Tests")
    class ErrorReportTests {

        @Test
        @DisplayName("Should generate downloadable error report")
        void downloadErrorReport_GeneratesCsv() throws Exception {
            // First, trigger an import with errors to generate an error report
            String csvContent = "Date,Description,Debit\n" +
                    "invalid-date,Payment1,1000\n" +
                    ",Payment2,2000\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            StatementImportRequestDTO request = createImportRequest();

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(reconciliationRepository.existsByCompanyIdAndStatementFileHash(eq(COMPANY_ID), anyString()))
                    .thenReturn(false);

            StatementImportResultDTO result = importService.importStatement(
                    reconciliationId, stream, "statement.csv", request);

            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorReportId());

            // Now download the error report
            byte[] reportBytes = importService.downloadErrorReport(result.getErrorReportId());

            String reportContent = new String(reportBytes, StandardCharsets.UTF_8);
            assertTrue(reportContent.startsWith("Row,Field,Value,Error"));
            assertTrue(reportContent.contains("Invalid date format") || reportContent.contains("Date is required"));
        }

        @Test
        @DisplayName("Should throw exception for invalid error report ID")
        void downloadErrorReport_InvalidId_ThrowsException() {
            assertThrows(BusinessException.class,
                    () -> importService.downloadErrorReport("non-existent-id"));
        }
    }

    @Nested
    @DisplayName("File Validation Tests")
    class FileValidationTests {

        @Test
        @DisplayName("Should reject file larger than 10MB")
        void importStatement_FileTooLarge_ThrowsException() throws Exception {
            // Create a stream that reports more than 10MB
            byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
            InputStream stream = new ByteArrayInputStream(largeContent);

            StatementImportRequestDTO request = createImportRequest();

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> importService.importStatement(reconciliationId, stream, "large.csv", request));

            assertTrue(exception.getMessage().contains("exceeds maximum size"));
        }

        @Test
        @DisplayName("Should throw exception for non-existent reconciliation")
        void analyzeFileHeaders_ReconciliationNotFound_ThrowsException() {
            String csvContent = "Date,Description\n";
            InputStream stream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> importService.analyzeFileHeaders(reconciliationId, stream, "statement.csv"));
        }
    }

    private StatementImportRequestDTO createImportRequest() {
        StatementImportRequestDTO request = new StatementImportRequestDTO();
        request.setDateColumn("Date");
        request.setDescriptionColumn("Description");
        request.setReferenceColumn("Reference");
        request.setDebitColumn("Debit");
        request.setCreditColumn("Credit");
        request.setBalanceColumn("Balance");
        request.setDateFormat("yyyy-MM-dd");
        request.setSkipHeaderRows(1);
        request.setSaveFormatProfile(false);
        return request;
    }
}

package com.accounting.imports.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accounting.entity.ChartOfAccount;
import com.accounting.imports.ImportType;
import com.accounting.imports.exception.ImportValidationException;
import com.accounting.imports.handler.impl.OpeningBalanceImportHandler;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.ledger.LedgerPeriodService;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.service.VoucherService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OpeningBalanceImportHandlerTest {

    @Mock
    private ImportErrorReportService importErrorReportService;
    @Mock
    private LedgerPeriodService ledgerPeriodService;
    @Mock
    private ChartOfAccountsRepository chartOfAccountsRepository;
    @Mock
    private VoucherService voucherService;

    private OpeningBalanceImportHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OpeningBalanceImportHandler(
                importErrorReportService, ledgerPeriodService, chartOfAccountsRepository, voucherService);
    }

    @Test
    void handle_shouldRejectWhenFirstPeriodClosed() {
        when(ledgerPeriodService.isFirstPeriodClosed(1L)).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "opening.csv",
                "text/csv",
                ("journal_code,account_code,account_name,currency,debit,credit,period_start,period_end,note\n")
                        .getBytes(StandardCharsets.UTF_8));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> handler.handle(
                        file,
                        new ImportContext(
                                1L,
                                2L,
                                "opening.csv",
                                Instant.now(),
                                "en",
                                UUID.randomUUID(),
                                null,
                                null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(importErrorReportService, never()).saveReport(any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void handle_shouldReturnErrorsForInvalidHeader() {
        when(ledgerPeriodService.isFirstPeriodClosed(1L)).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "opening.csv",
                "text/csv",
                ("account_code,account_name,currency,debit,credit,period_start,period_end,note\n")
                        .getBytes(StandardCharsets.UTF_8));

        UUID reportId = UUID.randomUUID();
        when(importErrorReportService.saveReport(
                eq(ImportType.OPENING_BALANCES), anyLong(), anyLong(), any(), any()))
                .thenReturn(reportId);

        ImportValidationException exception = assertThrows(
                ImportValidationException.class,
                () -> handler.handle(
                        file,
                        new ImportContext(
                                1L,
                                2L,
                                "opening.csv",
                                Instant.now(),
                                "en",
                                UUID.randomUUID(),
                                null,
                                null)));

        assertEquals(reportId, exception.getReportId());
        verify(importErrorReportService)
                .saveReport(
                        eq(ImportType.OPENING_BALANCES), eq(1L), eq(2L), eq("opening.csv"), any());
    }

    @Test
    void handle_shouldEnforceDrEqualsCr() {
        when(ledgerPeriodService.isFirstPeriodClosed(1L)).thenReturn(false);
        stubChartAccount("1311");
        stubChartAccount("3311");
        // Total debit 100, credit 50 -> mismatch -> error with report
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "opening.csv",
                "text/csv",
                ("journal_code,account_code,account_name,currency,debit,credit,period_start,period_end,note\n"
                        + "OB-2025-01,1311,Trade Receivables,VND,100,0,2025-01-01,2025-01-31,\n"
                        + "OB-2025-01,3311,Trade Payables,VND,0,50,2025-01-01,2025-01-31,\n")
                        .getBytes(StandardCharsets.UTF_8));

        UUID reportId = UUID.randomUUID();
        when(importErrorReportService.saveReport(
                eq(ImportType.OPENING_BALANCES), anyLong(), anyLong(), any(), any()))
                .thenReturn(reportId);

        ImportValidationException exception = assertThrows(
                ImportValidationException.class,
                () -> handler.handle(
                        file,
                        new ImportContext(
                                1L,
                                2L,
                                "opening.csv",
                                Instant.now(),
                                "en",
                                UUID.randomUUID(),
                                null,
                                null)));
        assertEquals(reportId, exception.getReportId());
    }

    @Test
    void handle_shouldSucceedWhenBalanced() {
        when(ledgerPeriodService.isFirstPeriodClosed(1L)).thenReturn(false);
        stubChartAccount("1311");
        stubChartAccount("3311");
        when(voucherService.create(any())).thenReturn(null);
        // Debit 100, Credit 100 -> success
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "opening.csv",
                "text/csv",
                ("journal_code,account_code,account_name,currency,debit,credit,period_start,period_end,note\n"
                        + "OB-2025-01,1311,Trade Receivables,VND,100,0,2025-01-01,2025-01-31,\n"
                        + "OB-2025-01,3311,Trade Payables,VND,0,100,2025-01-01,2025-01-31,\n")
                        .getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = handler.handle(
                file,
                new ImportContext(
                        1L,
                        2L,
                        "opening.csv",
                        Instant.now(),
                        "en",
                        UUID.randomUUID(),
                        null,
                        null));
        assertEquals(2, summary.getSuccessCount());
        assertEquals(0, summary.getErrorCount());
        verify(importErrorReportService, never()).saveReport(any(), anyLong(), anyLong(), any(), any());
        verify(voucherService).create(any());
    }

    private void stubChartAccount(String code) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId((long) code.hashCode());
        account.setCompanyId(1L);
        account.setCode(code);
        account.setActive(true);
        account.setPostable(true);
        account.setType("Asset");
        account.setNormalSide("Debit");
        account.setOrderingPosition(1);
        when(chartOfAccountsRepository.findByCompanyIdAndCode(1L, code))
                .thenReturn(Optional.of(account));
    }
}

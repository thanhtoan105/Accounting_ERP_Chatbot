package com.accounting.imports.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.accounting.dto.CustomerDTO;
import com.accounting.imports.ImportType;
import com.accounting.imports.exception.ImportValidationException;
import com.accounting.imports.handler.impl.CustomerImportHandler;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.service.CustomerService;

@ExtendWith(MockitoExtension.class)
class CustomerImportHandlerTest {

    @Mock
    private CustomerService customerService;
    @Mock
    private ImportErrorReportService importErrorReportService;

    private CustomerImportHandler handler;

    @BeforeEach
    void setUp() {
        jakarta.validation.Validator validator = jakarta.validation.Validation.buildDefaultValidatorFactory()
                .getValidator();
        handler = new CustomerImportHandler(customerService, validator, importErrorReportService);
    }

    @Test
    void handle_shouldImportValidCsv() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "customers.csv",
                "text/csv",
                ("customer_code,name,tax_code,email,phone,address,active\n"
                        + "C001,Valid Customer,0101234567,valid@example.com,+84123456789,Address,TRUE\n")
                        .getBytes(StandardCharsets.UTF_8));

        when(customerService.create(any())).thenReturn(new CustomerDTO());

        ImportSummary summary = handler.handle(
                file,
                new ImportContext(
                        1L,
                        2L,
                        "customers.csv",
                        Instant.now(),
                        "en",
                        java.util.UUID.randomUUID(),
                        null,
                        null));

        assertEquals(1, summary.getSuccessCount());
        assertEquals(0, summary.getErrorCount());
        verify(customerService).create(any());
        verify(importErrorReportService, never()).saveReport(any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void handle_shouldReturnErrorsForInvalidHeader() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "customers.csv",
                "text/csv",
                ("name,tax_code,email,phone,address,active\n"
                        + "Valid Customer,0101234567,valid@example.com,+84123456789,Address,TRUE\n")
                        .getBytes(StandardCharsets.UTF_8));

        UUID reportId = UUID.randomUUID();
        when(importErrorReportService.saveReport(
                eq(ImportType.CUSTOMERS), anyLong(), anyLong(), any(), any()))
                .thenReturn(reportId);

        ImportValidationException exception = assertThrows(
                ImportValidationException.class,
                () -> handler.handle(
                        file,
                        new ImportContext(
                                1L,
                                2L,
                                "customers.csv",
                                Instant.now(),
                                "en",
                                java.util.UUID.randomUUID(),
                                null,
                                null)));

        assertEquals(reportId, exception.getReportId());
        verify(customerService, never()).create(any());
        verify(importErrorReportService)
                .saveReport(eq(ImportType.CUSTOMERS), eq(1L), eq(2L), eq("customers.csv"), any());
    }

    @Test
    void handle_shouldRollbackWhenServiceFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "customers.csv",
                "text/csv",
                ("customer_code,name,tax_code,email,phone,address,active\n"
                        + "C001,Valid Customer,0101234567,valid@example.com,+84123456789,Address,TRUE\n")
                        .getBytes(StandardCharsets.UTF_8));

        when(customerService.create(any()))
                .thenThrow(
                        new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.CONFLICT, "Duplicate"));

        UUID reportId = UUID.randomUUID();
        when(importErrorReportService.saveReport(
                eq(ImportType.CUSTOMERS), anyLong(), anyLong(), any(), any()))
                .thenReturn(reportId);

        ImportValidationException exception = assertThrows(
                ImportValidationException.class,
                () -> handler.handle(
                        file,
                        new ImportContext(
                                1L,
                                2L,
                                "customers.csv",
                                Instant.now(),
                                "en",
                                java.util.UUID.randomUUID(),
                                null,
                                null)));

        assertEquals(reportId, exception.getReportId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.accounting.imports.model.ImportRowError>> errorsCaptor = ArgumentCaptor
                .forClass(List.class);
        verify(importErrorReportService)
                .saveReport(
                        eq(ImportType.CUSTOMERS),
                        eq(1L),
                        eq(2L),
                        eq("customers.csv"),
                        errorsCaptor.capture());
        assertEquals(1, errorsCaptor.getValue().size());
    }
}

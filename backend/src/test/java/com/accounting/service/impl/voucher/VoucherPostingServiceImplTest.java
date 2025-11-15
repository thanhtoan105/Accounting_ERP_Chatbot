package com.accounting.service.impl.voucher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accounting.dto.JournalEntryDTO;
import com.accounting.dto.PostVoucherResponse;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.entity.JournalEntry;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.exception.VoucherPostingException;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.PeriodManagementService;
import com.accounting.service.VoucherService;
import com.accounting.service.VoucherValidationService;
import jakarta.servlet.http.HttpServletRequest;
import com.accounting.service.gl.JournalEntryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherPostingServiceImplTest {

        @Mock
        private VoucherRepository voucherRepository;
        @Mock
        private VoucherLineRepository voucherLineRepository;
        @Mock
        private VoucherValidationService voucherValidationService;
        @Mock
        private JournalEntryService journalEntryService;
        @Mock
        private VoucherService voucherService;
        @Mock
        private com.accounting.service.AuditService auditService;
        @Mock
        private com.accounting.service.util.VoucherAuditHelper voucherAuditHelper;
        @Mock
        private PeriodManagementService periodManagementService;

        private VoucherPostingServiceImpl postingService;
        private Long testCompanyId = 1L;
        private UUID testVoucherId = UUID.randomUUID();

        @BeforeEach
        void setUp() {
                postingService = new VoucherPostingServiceImpl(
                                voucherRepository,
                                voucherLineRepository,
                                voucherValidationService,
                                journalEntryService,
                                voucherService,
                                auditService,
                                voucherAuditHelper,
                                periodManagementService);
                CompanyContext.setCompanyId(testCompanyId);
        }

        @AfterEach
        void tearDown() {
                CompanyContext.clear();
        }

        @Test
        void postVoucher_validDraftVoucher_postsSuccessfully() {
                // Arrange
                Voucher draftVoucher = createDraftVoucher();
                VoucherLine line1 = createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO);
                VoucherLine line2 = createVoucherLine(2L, BigDecimal.ZERO, BigDecimal.valueOf(1000));
                List<VoucherLine> lines = List.of(line1, line2);

                JournalEntry je1 = createJournalEntry(BigDecimal.valueOf(1000), BigDecimal.ZERO);
                JournalEntry je2 = createJournalEntry(BigDecimal.ZERO, BigDecimal.valueOf(1000));
                List<JournalEntry> journalEntries = List.of(je1, je2);

                VoucherDTO voucherDTO = createVoucherDTO("posted");
                VoucherValidationResult validResult = new VoucherValidationResult(true, new HashMap<>());

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                                .thenReturn(Optional.of(draftVoucher));
                when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                                .thenReturn(lines);
                when(voucherValidationService.validate(any(VoucherCreateRequest.class)))
                                .thenReturn(validResult);
                when(journalEntryService.generateJournalEntries(draftVoucher)).thenReturn(journalEntries);
                when(voucherRepository.save(any(Voucher.class))).thenReturn(draftVoucher);
                when(voucherService.getVoucherById(testVoucherId)).thenReturn(Optional.of(voucherDTO));
                
                // Mock period validation - allow posting
                LocalDate voucherDate = draftVoucher.getVoucherDate();
                if (voucherDate != null) {
                    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
                    if (draftVoucher.getPeriodId() != null) {
                        when(periodManagementService.isPeriodOpen(draftVoucher.getPeriodId())).thenReturn(true);
                    }
                }

                // Act
                PostVoucherResponse response = postingService.postVoucher(testVoucherId, null);

                // Assert
                assertNotNull(response);
                assertNotNull(response.getVoucher());
                assertEquals("posted", response.getVoucher().getStatus());
                assertEquals(2, response.getJournalEntries().size());

                verify(voucherRepository).save(any(Voucher.class));
                // Note: Journal entries are generated but saved by JournalEntryService
                // internally
                // The posting service only generates and returns them as DTOs
        }

        @Test
        void postVoucher_alreadyPosted_throwsConflictException() {
                // Arrange
                Voucher postedVoucher = createDraftVoucher();
                postedVoucher.setStatus("posted");
                postedVoucher.setPostedBy(1L);
                postedVoucher.setPostedAt(Instant.now());

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                                .thenReturn(Optional.of(postedVoucher));

                // Act & Assert
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class, () -> postingService.postVoucher(testVoucherId, null));
                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                assertTrue(exception.getReason() != null &&
                                (exception.getReason().contains("DRAFT status") ||
                                                exception.getReason().contains("already posted") ||
                                                exception.getReason().contains("Current status")));

                verify(voucherRepository, never()).save(any(Voucher.class));
                verify(journalEntryService, never()).generateJournalEntries(any(Voucher.class));
        }

        @Test
        void postVoucher_validationFails_throwsVoucherPostingException() {
                // Arrange
                Voucher draftVoucher = createDraftVoucher();
                Map<Integer, Map<String, List<String>>> errors = new HashMap<>();
                Map<String, List<String>> lineErrors = new HashMap<>();
                lineErrors.put("balance", List.of("Debits must equal credits"));
                errors.put(0, lineErrors);
                VoucherValidationResult invalidResult = new VoucherValidationResult(false, errors);

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                                .thenReturn(Optional.of(draftVoucher));
                when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                                .thenReturn(List.of());
                
                // Mock period validation to pass (so we can test voucher validation failure)
                LocalDate voucherDate = draftVoucher.getVoucherDate();
                if (voucherDate != null) {
                    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
                    if (draftVoucher.getPeriodId() != null) {
                        when(periodManagementService.isPeriodOpen(draftVoucher.getPeriodId())).thenReturn(true);
                    }
                }
                
                when(voucherValidationService.validate(any(VoucherCreateRequest.class)))
                                .thenReturn(invalidResult);

                // Act & Assert
                VoucherPostingException exception = assertThrows(
                                VoucherPostingException.class, () -> postingService.postVoucher(testVoucherId, null));
                assertNotNull(exception.getValidationErrors());
                assertTrue(exception.getValidationErrors().containsKey("lines"));

                verify(voucherRepository, never()).save(any(Voucher.class));
                verify(journalEntryService, never()).generateJournalEntries(any(Voucher.class));
        }

        @Test
        void postVoucher_voucherNotFound_throwsNotFoundException() {
                // Arrange
                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                                .thenReturn(Optional.empty());

                // Act & Assert
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class, () -> postingService.postVoucher(testVoucherId, null));
                assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        }

        @Test
        void postVoucher_setsPostedByAndPostedAt() {
                // Arrange
                Voucher draftVoucher = createDraftVoucher();
                List<VoucherLine> lines = List.of(createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO),
                                createVoucherLine(2L, BigDecimal.ZERO, BigDecimal.valueOf(1000)));
                List<JournalEntry> journalEntries = List.of(
                                createJournalEntry(BigDecimal.valueOf(1000), BigDecimal.ZERO),
                                createJournalEntry(BigDecimal.ZERO, BigDecimal.valueOf(1000)));
                VoucherDTO voucherDTO = createVoucherDTO("posted");
                VoucherValidationResult validResult = new VoucherValidationResult(true, new HashMap<>());

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                                .thenReturn(Optional.of(draftVoucher));
                when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                                .thenReturn(lines);
                when(voucherValidationService.validate(any(VoucherCreateRequest.class)))
                                .thenReturn(validResult);
                when(journalEntryService.generateJournalEntries(draftVoucher)).thenReturn(journalEntries);
                when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(voucherService.getVoucherById(testVoucherId)).thenReturn(Optional.of(voucherDTO));
                
                // Mock period validation - allow posting
                LocalDate voucherDate = draftVoucher.getVoucherDate();
                if (voucherDate != null) {
                    when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
                    if (draftVoucher.getPeriodId() != null) {
                        when(periodManagementService.isPeriodOpen(draftVoucher.getPeriodId())).thenReturn(true);
                    }
                }

                // Mock SecurityContext to return user ID
                org.springframework.security.core.Authentication auth = org.mockito.Mockito
                                .mock(org.springframework.security.core.Authentication.class);
                when(auth.getPrincipal()).thenReturn("1");
                org.springframework.security.core.context.SecurityContext securityContext = org.mockito.Mockito
                                .mock(org.springframework.security.core.context.SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(auth);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);

                // Act
                postingService.postVoucher(testVoucherId, null);

                // Assert
                ArgumentCaptor<Voucher> voucherCaptor = ArgumentCaptor.forClass(Voucher.class);
                verify(voucherRepository).save(voucherCaptor.capture());
                Voucher savedVoucher = voucherCaptor.getValue();
                assertEquals("posted", savedVoucher.getStatus());
                assertNotNull(savedVoucher.getPostedBy());
                assertNotNull(savedVoucher.getPostedAt());
        }

        // Helper methods
        private Voucher createDraftVoucher() {
                Voucher voucher = new Voucher();
                voucher.setId(testVoucherId);
                voucher.setCompanyId(testCompanyId);
                voucher.setVoucherNumber("VC2025-001");
                voucher.setVoucherDate(LocalDate.now());
                voucher.setStatus("draft");
                voucher.setCurrency("VND");
                voucher.setTotalDebit(BigDecimal.valueOf(1000));
                voucher.setTotalCredit(BigDecimal.valueOf(1000));
                voucher.setEnteredBy(1L);
                voucher.setCreatedAt(Instant.now());
                voucher.setUpdatedAt(Instant.now());
                return voucher;
        }

        private VoucherLine createVoucherLine(Long accountId, BigDecimal debit, BigDecimal credit) {
                VoucherLine line = new VoucherLine();
                line.setVoucherId(testVoucherId);
                line.setAccountId(accountId);
                line.setDebit(debit);
                line.setCredit(credit);
                line.setCompanyId(testCompanyId);
                return line;
        }

        private JournalEntry createJournalEntry(BigDecimal debitAmount, BigDecimal creditAmount) {
                JournalEntry entry = new JournalEntry();
                entry.setVoucherId(testVoucherId);
                entry.setAccountId(1L);
                entry.setDebitAmount(debitAmount);
                entry.setCreditAmount(creditAmount);
                entry.setCompanyId(testCompanyId);
                entry.setPostedAt(Instant.now());
                return entry;
        }

        private VoucherDTO createVoucherDTO(String status) {
                VoucherDTO dto = new VoucherDTO();
                dto.setId(testVoucherId);
                dto.setStatus(status);
                dto.setVoucherNumber("VC2025-001");
                return dto;
        }

        // ========== PERIOD VALIDATION TESTS ==========

        @Test
        void postVoucher_voucherWithOpenDate_postsSuccessfully() {
                // Arrange
                LocalDate voucherDate = LocalDate.of(2025, 1, 15);
                Voucher draftVoucher = createDraftVoucher();
                draftVoucher.setVoucherDate(voucherDate); // Set open period date

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                        .thenReturn(Optional.of(draftVoucher));
                when(voucherValidationService.validate(any())).thenReturn(new VoucherValidationResult(true, null));

                // Mock period management - date is in open period
                when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
                when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                        .thenReturn(List.of(createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO)));
                when(journalEntryService.generateJournalEntries(any())).thenReturn(List.of(createJournalEntry(BigDecimal.valueOf(1000), BigDecimal.ZERO)));
                when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(voucherService.getVoucherById(testVoucherId)).thenReturn(Optional.of(createVoucherDTO("posted")));

                // Mock SecurityContext to return user ID
                org.springframework.security.core.Authentication auth = org.mockito.Mockito
                                .mock(org.springframework.security.core.Authentication.class);
                when(auth.getPrincipal()).thenReturn("1");
                org.springframework.security.core.context.SecurityContext securityContext = org.mockito.Mockito
                                .mock(org.springframework.security.core.context.SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(auth);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);

                HttpServletRequest request = null; // Mock for audit logging

                // Act & Assert
                PostVoucherResponse response = postingService.postVoucher(testVoucherId, request);

                assertNotNull(response);
                assertEquals("posted", response.getVoucher().getStatus());
        }

        @Test
        void postVoucher_voucherWithClosedDate_throwsBadRequest() {
                // Arrange
                LocalDate voucherDate = LocalDate.of(2024, 12, 15); // Closed period
                Voucher draftVoucher = createDraftVoucher();
                draftVoucher.setVoucherDate(voucherDate);

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                        .thenReturn(Optional.of(draftVoucher));

                // Mock period management - date is NOT in open period
                when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(false);

                // Mock period lookup for error message
                com.accounting.dto.AccountingPeriodDTO closedPeriod = new com.accounting.dto.AccountingPeriodDTO();
                closedPeriod.setId(UUID.randomUUID());
                closedPeriod.setPeriodName("Dec-2024");
                when(periodManagementService.findPeriodByDate(voucherDate)).thenReturn(Optional.of(closedPeriod));

                HttpServletRequest request = null;

                // Act & Assert
                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> postingService.postVoucher(testVoucherId, request));

                assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                assertTrue(exception.getReason().contains("Cannot post voucher in closed or future period: Dec-2024"));

                // Verify audit logging was called
                verify(auditService).logPeriodValidationBlocked(
                        closedPeriod.getId(),
                        "VOUCHER_POSTING",
                        "Cannot post voucher in closed or future period: Dec-2024"
                );
        }

        @Test
        void postVoucher_voucherWithClosedDateNotFound_throwsBadRequestWithUnknownPeriod() {
                // Arrange
                LocalDate voucherDate = LocalDate.of(2024, 12, 15); // Closed period
                Voucher draftVoucher = createDraftVoucher();
                draftVoucher.setVoucherDate(voucherDate);

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                        .thenReturn(Optional.of(draftVoucher));

                // Mock period management - date is NOT in open period and period not found
                when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(false);
                when(periodManagementService.findPeriodByDate(voucherDate)).thenReturn(Optional.empty());

                HttpServletRequest request = null;

                // Act & Assert
                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> postingService.postVoucher(testVoucherId, request));

                assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                assertTrue(exception.getReason().contains("Cannot post voucher in closed or future period: Unknown Period"));

                // Verify audit logging was called with null period ID
                verify(auditService).logPeriodValidationBlocked(
                        null,
                        "VOUCHER_POSTING",
                        "Cannot post voucher in closed or future period: Unknown Period"
                );
        }

        @Test
        void postVoucher_voucherWithClosedPeriodId_throwsBadRequest() {
                // Arrange
                LocalDate voucherDate = LocalDate.now(); // Current date (should be open)
                UUID closedPeriodId = UUID.randomUUID();
                Voucher draftVoucher = createDraftVoucher();
                draftVoucher.setVoucherDate(voucherDate);
                draftVoucher.setPeriodId(closedPeriodId);

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                        .thenReturn(Optional.of(draftVoucher));

                // Mock period management - date is in open period but specific period ID is closed
                when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
                when(periodManagementService.isPeriodOpen(closedPeriodId)).thenReturn(false);

                // Mock period lookup for error message
                com.accounting.dto.AccountingPeriodDTO closedPeriod = new com.accounting.dto.AccountingPeriodDTO();
                closedPeriod.setId(closedPeriodId);
                closedPeriod.setPeriodName("Jan-2025");
                when(periodManagementService.getPeriodById(closedPeriodId)).thenReturn(Optional.of(closedPeriod));

                HttpServletRequest request = null;

                // Act & Assert
                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> postingService.postVoucher(testVoucherId, request));

                assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                assertTrue(exception.getReason().contains("Cannot post voucher in closed period: Jan-2025"));

                // Verify audit logging was called
                verify(auditService).logPeriodValidationBlocked(
                        UUID.fromString(closedPeriodId.toString()),
                        "VOUCHER_POSTING",
                        "Cannot post voucher in closed period: Jan-2025"
                );
        }

        @Test
        void postVoucher_periodValidationServiceThrowsException_throwsInternalServerError() {
                // Arrange
                LocalDate voucherDate = LocalDate.now();
                Voucher draftVoucher = createDraftVoucher();
                draftVoucher.setVoucherDate(voucherDate);

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                        .thenReturn(Optional.of(draftVoucher));

                // Mock period management service throwing exception
                when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenThrow(new RuntimeException("Database error"));

                HttpServletRequest request = null;

                // Act & Assert
                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> postingService.postVoucher(testVoucherId, request));

                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
                assertTrue(exception.getReason().contains("Failed to validate period for voucher posting"));
        }

        @Test
        void postVoucher_auditLoggingFails_continuesWithValidation() {
                // Arrange
                LocalDate voucherDate = LocalDate.of(2024, 12, 15); // Closed period
                Voucher draftVoucher = createDraftVoucher();
                draftVoucher.setVoucherDate(voucherDate);

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                        .thenReturn(Optional.of(draftVoucher));

                // Mock period management - date is NOT in open period
                when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(false);

                // Mock period lookup for error message
                com.accounting.dto.AccountingPeriodDTO closedPeriod = new com.accounting.dto.AccountingPeriodDTO();
                closedPeriod.setId(UUID.randomUUID());
                closedPeriod.setPeriodName("Dec-2024");
                when(periodManagementService.findPeriodByDate(voucherDate)).thenReturn(Optional.of(closedPeriod));

                // Mock audit service throwing exception
                doThrow(new RuntimeException("Audit service error")).when(auditService).logPeriodValidationBlocked(any(), any(), any());

                HttpServletRequest request = null;

                // Act & Assert
                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> postingService.postVoucher(testVoucherId, request));

                assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                assertTrue(exception.getReason().contains("Cannot post voucher in closed or future period: Dec-2024"));
        }

        @Test
        void postVoucher_voucherWithNullPeriodId_validatesOnlyDate() {
                // Arrange
                LocalDate voucherDate = LocalDate.of(2025, 1, 15); // Open period
                Voucher draftVoucher = createDraftVoucher();
                draftVoucher.setVoucherDate(voucherDate);
                draftVoucher.setPeriodId(null); // No specific period ID

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                        .thenReturn(Optional.of(draftVoucher));
                when(voucherValidationService.validate(any())).thenReturn(new VoucherValidationResult(true, null));

                // Mock period management - date is in open period (no period ID to check)
                when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
                when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                        .thenReturn(List.of(createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO)));
                when(journalEntryService.generateJournalEntries(any())).thenReturn(List.of(createJournalEntry(BigDecimal.valueOf(1000), BigDecimal.ZERO)));
                when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(voucherService.getVoucherById(testVoucherId)).thenReturn(Optional.of(createVoucherDTO("posted")));

                // Mock SecurityContext to return user ID
                org.springframework.security.core.Authentication auth = org.mockito.Mockito
                                .mock(org.springframework.security.core.Authentication.class);
                when(auth.getPrincipal()).thenReturn("1");
                org.springframework.security.core.context.SecurityContext securityContext = org.mockito.Mockito
                                .mock(org.springframework.security.core.context.SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(auth);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);

                HttpServletRequest request = null;

                // Act & Assert - Should succeed since date is open
                PostVoucherResponse response = postingService.postVoucher(testVoucherId, request);

                assertNotNull(response);
                assertEquals("posted", response.getVoucher().getStatus());

                // Verify isPeriodOpen was never called since periodId is null
                verify(periodManagementService, never()).isPeriodOpen(any());
        }

        @Test
        void postVoucher_voucherWithOpenDateAndOpenPeriodId_postsSuccessfully() {
                // Arrange
                LocalDate voucherDate = LocalDate.of(2025, 1, 15); // Open period
                UUID openPeriodId = UUID.randomUUID();
                Voucher draftVoucher = createDraftVoucher();
                draftVoucher.setVoucherDate(voucherDate);
                draftVoucher.setPeriodId(openPeriodId);

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
                        .thenReturn(Optional.of(draftVoucher));
                when(voucherValidationService.validate(any())).thenReturn(new VoucherValidationResult(true, null));

                // Mock period management - both date and period are open
                when(periodManagementService.isDateInOpenPeriod(voucherDate)).thenReturn(true);
                when(periodManagementService.isPeriodOpen(openPeriodId)).thenReturn(true);
                when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                        .thenReturn(List.of(createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO)));
                when(journalEntryService.generateJournalEntries(any())).thenReturn(List.of(createJournalEntry(BigDecimal.valueOf(1000), BigDecimal.ZERO)));
                when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(voucherService.getVoucherById(testVoucherId)).thenReturn(Optional.of(createVoucherDTO("posted")));

                // Mock SecurityContext to return user ID
                org.springframework.security.core.Authentication auth = org.mockito.Mockito
                                .mock(org.springframework.security.core.Authentication.class);
                when(auth.getPrincipal()).thenReturn("1");
                org.springframework.security.core.context.SecurityContext securityContext = org.mockito.Mockito
                                .mock(org.springframework.security.core.context.SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(auth);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);

                HttpServletRequest request = null;

                // Act & Assert - Should succeed since both date and period are open
                PostVoucherResponse response = postingService.postVoucher(testVoucherId, request);

                assertNotNull(response);
                assertEquals("posted", response.getVoucher().getStatus());
        }
}

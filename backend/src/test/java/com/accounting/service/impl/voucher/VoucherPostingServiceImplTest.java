package com.accounting.service.impl.voucher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.accounting.service.VoucherService;
import com.accounting.service.VoucherValidationService;
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
                auditService);
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
        List<JournalEntry> journalEntries = List.of(createJournalEntry(BigDecimal.valueOf(1000), BigDecimal.ZERO),
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
}

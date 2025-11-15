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

import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.entity.Voucher;
import com.accounting.service.voucher.VoucherReversalService;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.VoucherService;
import com.accounting.service.voucher.VoucherPostingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
class VoucherReversalServiceImplTest {

        @Mock
        private VoucherRepository voucherRepository;
        @Mock
        private VoucherLineRepository voucherLineRepository;
        @Mock
        private VoucherService voucherService;
        @Mock
        private VoucherPostingService voucherPostingService;
        @Mock
        private com.accounting.service.AuditService auditService;
        @Mock
        private com.accounting.service.util.VoucherAuditHelper voucherAuditHelper;

        private VoucherReversalServiceImpl reversalService;
        private Long testCompanyId = 1L;
        private UUID originalVoucherId = UUID.randomUUID();
        private UUID reversalVoucherId = UUID.randomUUID();

        @BeforeEach
        void setUp() {
                reversalService = new VoucherReversalServiceImpl(
                                voucherRepository,
                                voucherLineRepository,
                                voucherService,
                                voucherPostingService,
                                auditService,
                                voucherAuditHelper);
                CompanyContext.setCompanyId(testCompanyId);
        }

        @AfterEach
        void tearDown() {
                CompanyContext.clear();
        }

        @Test
        void reverseVoucher_postedVoucher_createsReversal() {
                // Arrange
                Voucher postedVoucher = createPostedVoucher();
                VoucherLine line1 = createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO);
                VoucherLine line2 = createVoucherLine(2L, BigDecimal.ZERO, BigDecimal.valueOf(1000));
                List<VoucherLine> originalLines = List.of(line1, line2);

                Voucher reversalVoucher = createDraftVoucher();
                reversalVoucher.setId(reversalVoucherId);
                reversalVoucher.setVoucherNumber("REV-VC2025-001");
                reversalVoucher.setReversalOf(originalVoucherId);

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, originalVoucherId))
                                .thenReturn(Optional.of(postedVoucher));
                when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(originalVoucherId))
                                .thenReturn(originalLines);
                // VoucherReversalServiceImpl creates the reversal voucher directly, not through
                // voucherService.create()
                // So we mock the save to return the reversal voucher with the ID set
                when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> {
                        Voucher v = invocation.getArgument(0);
                        if (v.getVoucherNumber() != null && v.getVoucherNumber().startsWith("REV-")) {
                                v.setId(reversalVoucherId);
                                return v;
                        }
                        return v; // Return original voucher as-is
                });
                // After reversal voucher is saved, mock findByCompanyIdAndId to return it
                when(voucherRepository.findByCompanyIdAndId(testCompanyId, reversalVoucherId))
                                .thenReturn(Optional.of(reversalVoucher));
                when(voucherPostingService.postVoucher(reversalVoucherId, null)).thenReturn(null);
                // Mock getVoucherById to return DTOs after reversal is created
                when(voucherService.getVoucherById(originalVoucherId))
                                .thenReturn(Optional.of(createVoucherDTO(originalVoucherId, "VC2025-001", "posted")));
                when(voucherService.getVoucherById(reversalVoucherId))
                                .thenReturn(Optional
                                                .of(createVoucherDTO(reversalVoucherId, "REV-VC2025-001", "posted")));

                // Mock SecurityContext
                org.springframework.security.core.Authentication auth = org.mockito.Mockito
                                .mock(org.springframework.security.core.Authentication.class);
                when(auth.getPrincipal()).thenReturn("1");
                org.springframework.security.core.context.SecurityContext securityContext = org.mockito.Mockito
                                .mock(org.springframework.security.core.context.SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(auth);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);

                // Act
                VoucherReversalService.ReversalResult result = reversalService.reverseVoucher(originalVoucherId,
                                "Reversal description", "Test reason", null);

                // Assert
                assertNotNull(result);
                assertNotNull(result.getOriginal());
                assertNotNull(result.getReversal());
                assertEquals("REV-VC2025-001", result.getReversal().getVoucherNumber());
                assertEquals("posted", result.getReversal().getStatus());

                // Verify reversal voucher was created (saved directly, not through
                // voucherService.create)
                ArgumentCaptor<Voucher> voucherCaptor = ArgumentCaptor.forClass(Voucher.class);
                verify(voucherRepository, org.mockito.Mockito.atLeastOnce()).save(voucherCaptor.capture());
                List<Voucher> savedVouchers = voucherCaptor.getAllValues();
                // Find the reversal voucher (has REV- prefix)
                Voucher savedReversal = savedVouchers.stream()
                                .filter(v -> v.getVoucherNumber() != null && v.getVoucherNumber().startsWith("REV-"))
                                .findFirst()
                                .orElse(null);
                assertNotNull(savedReversal);
                assertEquals("REV-VC2025-001", savedReversal.getVoucherNumber());

                // Verify original voucher is linked to reversal
                assertTrue(
                                savedVouchers.stream()
                                                .anyMatch(v -> v.getId().equals(originalVoucherId)
                                                                && v.getReversedByVoucherId() != null));

                // Verify reversal was auto-posted
                verify(voucherPostingService).postVoucher(reversalVoucherId, null);
        }

        @Test
        void reverseVoucher_alreadyReversed_throwsConflictException() {
                // Arrange
                Voucher postedVoucher = createPostedVoucher();
                postedVoucher.setReversedByVoucherId(UUID.randomUUID());

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, originalVoucherId))
                                .thenReturn(Optional.of(postedVoucher));

                // Act & Assert
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> reversalService.reverseVoucher(
                                                originalVoucherId, "Description", "Reason", null));
                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                assertTrue(exception.getReason().contains("already been reversed"));

                verify(voucherService, never()).create(any(VoucherCreateRequest.class));
        }

        @Test
        void reverseVoucher_draftVoucher_throwsBadRequestException() {
                // Arrange
                Voucher draftVoucher = createDraftVoucher();

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, originalVoucherId))
                                .thenReturn(Optional.of(draftVoucher));

                // Act & Assert
                ResponseStatusException exception = assertThrows(
                                ResponseStatusException.class,
                                () -> reversalService.reverseVoucher(
                                                originalVoucherId, "Description", "Reason", null));
                assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                assertTrue(exception.getReason().contains("voucher must be in POSTED status"));

                verify(voucherService, never()).create(any(VoucherCreateRequest.class));
        }

        @Test
        void reverseVoucher_swapsDebitAndCreditAmounts() {
                // Arrange
                Voucher postedVoucher = createPostedVoucher();
                VoucherLine debitLine = createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO);
                VoucherLine creditLine = createVoucherLine(2L, BigDecimal.ZERO, BigDecimal.valueOf(1000));
                List<VoucherLine> originalLines = List.of(debitLine, creditLine);

                Voucher reversalVoucher = createDraftVoucher();
                reversalVoucher.setId(reversalVoucherId);
                reversalVoucher.setVoucherNumber("REV-VC2025-001");
                reversalVoucher.setReversalOf(originalVoucherId);

                when(voucherRepository.findByCompanyIdAndId(testCompanyId, originalVoucherId))
                                .thenReturn(Optional.of(postedVoucher));
                when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(originalVoucherId))
                                .thenReturn(originalLines);
                // VoucherReversalServiceImpl creates the reversal voucher directly, not through
                // voucherService.create()
                // So we mock the save to return the reversal voucher with the ID set
                when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> {
                        Voucher v = invocation.getArgument(0);
                        if (v.getVoucherNumber() != null && v.getVoucherNumber().startsWith("REV-")) {
                                v.setId(reversalVoucherId);
                                return v;
                        }
                        return v; // Return original voucher as-is
                });
                // After reversal voucher is saved, mock findByCompanyIdAndId to return it
                when(voucherRepository.findByCompanyIdAndId(testCompanyId, reversalVoucherId))
                                .thenReturn(Optional.of(reversalVoucher));
                when(voucherPostingService.postVoucher(reversalVoucherId, null)).thenReturn(null);
                // Mock getVoucherById to return DTOs after reversal is created
                when(voucherService.getVoucherById(originalVoucherId))
                                .thenReturn(Optional.of(createVoucherDTO(originalVoucherId, "VC2025-001", "posted")));
                when(voucherService.getVoucherById(reversalVoucherId))
                                .thenReturn(Optional
                                                .of(createVoucherDTO(reversalVoucherId, "REV-VC2025-001", "posted")));

                // Mock SecurityContext
                org.springframework.security.core.Authentication auth = org.mockito.Mockito
                                .mock(org.springframework.security.core.Authentication.class);
                when(auth.getPrincipal()).thenReturn("1");
                org.springframework.security.core.context.SecurityContext securityContext = org.mockito.Mockito
                                .mock(org.springframework.security.core.context.SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(auth);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);

                // Act
                reversalService.reverseVoucher(originalVoucherId, "Description", "Reason", null);

                // Assert - Verify reversal lines were created with swapped amounts
                ArgumentCaptor<List<VoucherLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
                verify(voucherLineRepository).saveAll(linesCaptor.capture());
                List<VoucherLine> savedReversalLines = linesCaptor.getValue();
                assertNotNull(savedReversalLines);
                assertEquals(2, savedReversalLines.size());

                // Verify first line has swapped amounts (original debit becomes credit,
                // original credit becomes debit)
                VoucherLine firstReversalLine = savedReversalLines.get(0);
                // Original line1 had account1 with debit=1000, credit=0
                // Reversal line should have account1 with debit=0, credit=1000
                assertEquals(1L, firstReversalLine.getAccountId());
                assertEquals(BigDecimal.ZERO, firstReversalLine.getDebit());
                assertEquals(BigDecimal.valueOf(1000), firstReversalLine.getCredit());
        }

        // Helper methods
        private Voucher createPostedVoucher() {
                Voucher voucher = new Voucher();
                voucher.setId(originalVoucherId);
                voucher.setCompanyId(testCompanyId);
                voucher.setVoucherNumber("VC2025-001");
                voucher.setVoucherDate(LocalDate.now());
                voucher.setStatus("posted");
                voucher.setCurrency("VND");
                voucher.setTotalDebit(BigDecimal.valueOf(1000));
                voucher.setTotalCredit(BigDecimal.valueOf(1000));
                voucher.setEnteredBy(1L);
                voucher.setPostedBy(1L);
                voucher.setPostedAt(Instant.now());
                voucher.setCreatedAt(Instant.now());
                voucher.setUpdatedAt(Instant.now());
                return voucher;
        }

        private Voucher createDraftVoucher() {
                Voucher voucher = new Voucher();
                voucher.setId(originalVoucherId);
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
                line.setVoucherId(originalVoucherId);
                line.setAccountId(accountId);
                line.setDebit(debit);
                line.setCredit(credit);
                line.setCompanyId(testCompanyId);
                return line;
        }

        private VoucherDTO createVoucherDTO(UUID id, String voucherNumber, String status) {
                VoucherDTO dto = new VoucherDTO();
                dto.setId(id);
                dto.setVoucherNumber(voucherNumber);
                dto.setStatus(status);
                dto.setVoucherDate(LocalDate.now());
                dto.setDescription("Test voucher");
                dto.setCurrency("VND");
                dto.setTotalDebit(BigDecimal.valueOf(1000));
                dto.setTotalCredit(BigDecimal.valueOf(1000));
                return dto;
        }
}

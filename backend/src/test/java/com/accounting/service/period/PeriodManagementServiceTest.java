package com.accounting.service.period;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.PeriodCloseRequest;
import com.accounting.dto.PeriodReopenRequest;
import com.accounting.dto.PeriodSummaryDTO;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.impl.PeriodManagementServiceImpl;
import com.accounting.service.util.VoucherAuditHelper;

@ExtendWith(MockitoExtension.class)
public class PeriodManagementServiceTest {

    @Mock
    private AccountingPeriodRepository periodRepository;

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private com.accounting.repository.UserRepository userRepository;

    @Mock
    private VoucherAuditHelper voucherAuditHelper;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private PeriodManagementServiceImpl periodService;

    private AccountingPeriod testPeriod;
    private AccountingPeriod testClosedPeriod;

    @BeforeEach
    void setUp() {
        // Setup test company context
        CompanyContext.setCompanyId(1L);

        // Setup test periods
        testPeriod = new AccountingPeriod();
        testPeriod.setId(UUID.randomUUID());
        testPeriod.setCompanyId(1L);
        testPeriod.setFiscalYear(2025);
        testPeriod.setPeriodNumber(1);
        testPeriod.setPeriodName("January 2025");
        testPeriod.setStartDate(LocalDate.of(2025, Month.JANUARY, 1));
        testPeriod.setEndDate(LocalDate.of(2025, Month.JANUARY, 31));
        testPeriod.setStatus(PeriodStatus.OPEN);
        testPeriod.setCreatedAt(Instant.now());
        testPeriod.setUpdatedAt(Instant.now());
        testPeriod.setVersion(0L);

        testClosedPeriod = new AccountingPeriod();
        testClosedPeriod.setId(UUID.randomUUID());
        testClosedPeriod.setCompanyId(1L);
        testClosedPeriod.setFiscalYear(2024);
        testClosedPeriod.setPeriodNumber(12);
        testClosedPeriod.setPeriodName("December 2024");
        testClosedPeriod.setStartDate(LocalDate.of(2024, Month.DECEMBER, 1));
        testClosedPeriod.setEndDate(LocalDate.of(2024, Month.DECEMBER, 31));
        testClosedPeriod.setStatus(PeriodStatus.CLOSED);
        testClosedPeriod.setClosedBy(1L);
        testClosedPeriod.setClosedAt(Instant.now());
        testClosedPeriod.setCloseReason("Month end closing");
        testClosedPeriod.setCreatedAt(Instant.now());
        testClosedPeriod.setUpdatedAt(Instant.now());
        testClosedPeriod.setVersion(1L);
    }

    @Test
    void getCurrentPeriod_WithExistingPeriod_ReturnsPeriod() {
        // Given
        when(periodRepository.findCurrentPeriodByCompanyId(1L))
                .thenReturn(Optional.of(testPeriod));

        // When
        Optional<AccountingPeriodDTO> result = periodService.getCurrentPeriod();

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(testPeriod.getId());
        assertThat(result.get().getPeriodName()).isEqualTo("January 2025");
        assertThat(result.get().getStatus()).isEqualTo(PeriodStatus.OPEN);
        verify(periodRepository).findCurrentPeriodByCompanyId(1L);
    }

    @Test
    void getCurrentPeriod_NoPeriodFound_ReturnsEmpty() {
        // Given
        when(periodRepository.findCurrentPeriodByCompanyId(1L))
                .thenReturn(Optional.empty());

        // When
        Optional<AccountingPeriodDTO> result = periodService.getCurrentPeriod();

        // Then
        assertThat(result).isEmpty();
        verify(periodRepository).findCurrentPeriodByCompanyId(1L);
    }

    @Test
    void getOpenPeriods_WithOpenPeriods_ReturnsPeriods() {
        // Given
        List<AccountingPeriod> openPeriods = List.of(testPeriod);
        LocalDate currentDate = LocalDate.now();
        when(periodRepository.findOpenPeriodsAroundDate(1L, PeriodStatus.OPEN.name(), currentDate))
                .thenReturn(openPeriods);

        // When
        List<AccountingPeriodDTO> result = periodService.getOpenPeriods();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPeriodName()).isEqualTo("January 2025");
        assertThat(result.get(0).getStatus()).isEqualTo(PeriodStatus.OPEN);
        verify(periodRepository).findOpenPeriodsAroundDate(eq(1L), eq(PeriodStatus.OPEN.name()), any(LocalDate.class));
    }

    @Test
    void getPeriodById_WithValidPeriod_ReturnsPeriod() {
        // Given
        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));

        // When
        Optional<AccountingPeriodDTO> result = periodService.getPeriodById(testPeriod.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(testPeriod.getId());
        assertThat(result.get().getPeriodName()).isEqualTo("January 2025");
        verify(periodRepository).findByCompanyIdAndId(1L, testPeriod.getId());
    }

    @Test
    void getPeriodById_PeriodNotFound_ReturnsEmpty() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(periodRepository.findByCompanyIdAndId(1L, nonExistentId))
                .thenReturn(Optional.empty());

        // When
        Optional<AccountingPeriodDTO> result = periodService.getPeriodById(nonExistentId);

        // Then
        assertThat(result).isEmpty();
        verify(periodRepository).findByCompanyIdAndId(1L, nonExistentId);
    }

    @Test
    void findPeriodByDate_WithValidDate_ReturnsPeriod() {
        // Given
        LocalDate date = LocalDate.of(2025, Month.JANUARY, 15);
        when(periodRepository.findByCompanyIdAndDate(1L, date))
                .thenReturn(Optional.of(testPeriod));

        // When
        Optional<AccountingPeriodDTO> result = periodService.findPeriodByDate(date);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPeriodName()).isEqualTo("January 2025");
        verify(periodRepository).findByCompanyIdAndDate(1L, date);
    }

    @Test
    void isPeriodOpen_OpenPeriod_ReturnsTrue() {
        // Given
        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));

        // When
        boolean result = periodService.isPeriodOpen(testPeriod.getId());

        // Then
        assertThat(result).isTrue();
        verify(periodRepository).findByCompanyIdAndId(1L, testPeriod.getId());
    }

    @Test
    void isPeriodOpen_ClosedPeriod_ReturnsFalse() {
        // Given
        when(periodRepository.findByCompanyIdAndId(1L, testClosedPeriod.getId()))
                .thenReturn(Optional.of(testClosedPeriod));

        // When
        boolean result = periodService.isPeriodOpen(testClosedPeriod.getId());

        // Then
        assertThat(result).isFalse();
        verify(periodRepository).findByCompanyIdAndId(1L, testClosedPeriod.getId());
    }

    @Test
    void isPeriodOpen_PeriodNotFound_ReturnsFalse() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(periodRepository.findByCompanyIdAndId(1L, nonExistentId))
                .thenReturn(Optional.empty());

        // When
        boolean result = periodService.isPeriodOpen(nonExistentId);

        // Then
        assertThat(result).isFalse();
        verify(periodRepository).findByCompanyIdAndId(1L, nonExistentId);
    }

    @Test
    void closePeriod_ValidPeriod_ReturnsClosedPeriod() {
        // Given
        PeriodCloseRequest request = new PeriodCloseRequest();
        request.setReason("Month end closing");

        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));
        when(periodRepository.countDraftVouchersInPeriod(1L, testPeriod.getId()))
                .thenReturn(0L); // No draft vouchers
        when(voucherRepository.findByCompanyIdAndPeriodId(1L, testPeriod.getId()))
                .thenReturn(List.of()); // No vouchers
        when(voucherAuditHelper.calculateDiffHash(any(), any())).thenReturn("hash123");
        when(objectMapper.valueToTree(any())).thenReturn(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
        when(periodRepository.save(any(AccountingPeriod.class))).thenAnswer(invocation -> {
            AccountingPeriod period = invocation.getArgument(0);
            period.setStatus(PeriodStatus.CLOSED);
            period.setClosedBy(1L);
            period.setClosedAt(Instant.now());
            period.setCloseReason("Month end closing");
            return period;
        });

        // Mock SecurityUtils
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            // When
            AccountingPeriodDTO result = periodService.closePeriod(testPeriod.getId(), request);

            // Then
            assertThat(result.getStatus()).isEqualTo(PeriodStatus.CLOSED);
            assertThat(result.getCloseReason()).isEqualTo("Month end closing");
            verify(periodRepository).findByCompanyIdAndId(1L, testPeriod.getId());
            verify(periodRepository).save(any(AccountingPeriod.class));
            verify(auditService).logPeriodClosed(testPeriod.getId(), "Month end closing", "hash123");
        }
    }

    @Test
    void closePeriod_PeriodNotFound_ThrowsException() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        PeriodCloseRequest request = new PeriodCloseRequest();
        request.setReason("Test close");

        when(periodRepository.findByCompanyIdAndId(1L, nonExistentId))
                .thenReturn(Optional.empty());

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            periodService.closePeriod(nonExistentId, request);
        });
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void closePeriod_PeriodAlreadyClosed_ThrowsException() {
        // Given
        PeriodCloseRequest request = new PeriodCloseRequest();
        request.setReason("Already closed");

        when(periodRepository.findByCompanyIdAndId(1L, testClosedPeriod.getId()))
                .thenReturn(Optional.of(testClosedPeriod));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            periodService.closePeriod(testClosedPeriod.getId(), request);
        });
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void closePeriod_FuturePeriod_ThrowsException() {
        // Given
        AccountingPeriod futurePeriod = new AccountingPeriod();
        futurePeriod.setId(UUID.randomUUID());
        futurePeriod.setCompanyId(1L);
        futurePeriod.setFiscalYear(2025);
        futurePeriod.setPeriodNumber(12);
        futurePeriod.setPeriodName("December 2025");
        futurePeriod.setStartDate(LocalDate.of(2025, Month.DECEMBER, 1));
        futurePeriod.setEndDate(LocalDate.of(2025, Month.DECEMBER, 31));
        futurePeriod.setStatus(PeriodStatus.OPEN);

        PeriodCloseRequest request = new PeriodCloseRequest();
        request.setReason("Test close future period");

        when(periodRepository.findByCompanyIdAndId(1L, futurePeriod.getId()))
                .thenReturn(Optional.of(futurePeriod));

        // Mock SecurityUtils
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            // When & Then
            ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
                periodService.closePeriod(futurePeriod.getId(), request);
            });
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void reopenPeriod_ValidClosedPeriod_ReturnsOpenPeriod() {
        // Given
        PeriodReopenRequest request = new PeriodReopenRequest();
        request.setReason("Correction needed");
        request.setApprovalMetadata("Approved by CFO");

        when(periodRepository.findByCompanyIdAndId(1L, testClosedPeriod.getId()))
                .thenReturn(Optional.of(testClosedPeriod));
        when(voucherAuditHelper.toJson(any())).thenReturn("{\"id\":\"test\"}");
        when(voucherAuditHelper.calculateSHA256(anyString())).thenReturn("hash123");
        when(periodRepository.save(any(AccountingPeriod.class))).thenAnswer(invocation -> {
            AccountingPeriod period = invocation.getArgument(0);
            period.setStatus(PeriodStatus.OPEN);
            period.setClosedBy(null);
            period.setClosedAt(null);
            period.setCloseReason(null);
            return period;
        });

        // Mock SecurityUtils
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            // When
            AccountingPeriodDTO result = periodService.reopenPeriod(testClosedPeriod.getId(), request);

            // Then
            assertThat(result.getStatus()).isEqualTo(PeriodStatus.OPEN);
            assertThat(result.getCloseReason()).isNull();
            verify(periodRepository).findByCompanyIdAndId(1L, testClosedPeriod.getId());
            verify(periodRepository).save(any(AccountingPeriod.class));
            verify(auditService).logPeriodReopened(testClosedPeriod.getId(), "Correction needed", "Approved by CFO", "hash123");
        }
    }

    @Test
    void reopenPeriod_PeriodNotFound_ThrowsException() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        PeriodReopenRequest request = new PeriodReopenRequest();
        request.setReason("Test reopen");
        request.setApprovalMetadata("Test approval");

        when(periodRepository.findByCompanyIdAndId(1L, nonExistentId))
                .thenReturn(Optional.empty());

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            periodService.reopenPeriod(nonExistentId, request);
        });
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reopenPeriod_OpenPeriod_ThrowsException() {
        // Given
        PeriodReopenRequest request = new PeriodReopenRequest();
        request.setReason("Already open");
        request.setApprovalMetadata("Test approval");

        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            periodService.reopenPeriod(testPeriod.getId(), request);
        });
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getAllPeriods_WithPeriods_ReturnsAllPeriods() {
        // Given
        List<AccountingPeriod> allPeriods = List.of(testPeriod, testClosedPeriod);
        when(periodRepository.findByCompanyId(1L)).thenReturn(allPeriods);

        // When
        List<AccountingPeriodDTO> result = periodService.getAllPeriods();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(AccountingPeriodDTO::getPeriodName)
                .containsExactly("January 2025", "December 2024");
        verify(periodRepository).findByCompanyId(1L);
    }

    @Test
    void getPeriodsByFiscalYear_WithPeriods_ReturnsFiscalYearPeriods() {
        // Given
        List<AccountingPeriod> year2024Periods = List.of(testClosedPeriod);
        when(periodRepository.findByCompanyIdAndFiscalYear(1L, 2024))
                .thenReturn(year2024Periods);

        // When
        List<AccountingPeriodDTO> result = periodService.getPeriodsByFiscalYear(2024);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPeriodName()).isEqualTo("December 2024");
        assertThat(result.get(0).getFiscalYear()).isEqualTo(2024);
        verify(periodRepository).findByCompanyIdAndFiscalYear(1L, 2024);
    }

    @Test
    void createPeriod_ValidPeriod_ReturnsCreatedPeriod() {
        // Given
        AccountingPeriod newPeriod = new AccountingPeriod();
        newPeriod.setCompanyId(1L);
        newPeriod.setFiscalYear(2025);
        newPeriod.setPeriodNumber(2);
        newPeriod.setPeriodName("February 2025");
        newPeriod.setStartDate(LocalDate.of(2025, Month.FEBRUARY, 1));
        newPeriod.setEndDate(LocalDate.of(2025, Month.FEBRUARY, 28));

        AccountingPeriod savedPeriod = new AccountingPeriod();
        savedPeriod.setId(UUID.randomUUID());
        savedPeriod.setCompanyId(1L);
        savedPeriod.setFiscalYear(2025);
        savedPeriod.setPeriodNumber(2);
        savedPeriod.setPeriodName("February 2025");
        savedPeriod.setStartDate(LocalDate.of(2025, Month.FEBRUARY, 1));
        savedPeriod.setEndDate(LocalDate.of(2025, Month.FEBRUARY, 28));
        savedPeriod.setStatus(PeriodStatus.OPEN);
        savedPeriod.setCreatedAt(Instant.now());
        savedPeriod.setUpdatedAt(Instant.now());
        savedPeriod.setVersion(0L);

        when(periodRepository.countOverlappingPeriods(1L,
                LocalDate.of(2025, Month.FEBRUARY, 1),
                LocalDate.of(2025, Month.FEBRUARY, 28)))
                .thenReturn(0L); // No overlapping periods
        when(periodRepository.save(any(AccountingPeriod.class))).thenReturn(savedPeriod);

        // When
        AccountingPeriodDTO result = periodService.createPeriod(newPeriod);

        // Then
        assertThat(result.getPeriodName()).isEqualTo("February 2025");
        assertThat(result.getFiscalYear()).isEqualTo(2025);
        assertThat(result.getStatus()).isEqualTo(PeriodStatus.OPEN);
        verify(periodRepository).countOverlappingPeriods(1L,
                LocalDate.of(2025, Month.FEBRUARY, 1),
                LocalDate.of(2025, Month.FEBRUARY, 28));
        verify(periodRepository).save(any(AccountingPeriod.class));
    }

    @Test
    void createPeriod_OverlappingPeriods_ThrowsException() {
        // Given
        AccountingPeriod newPeriod = new AccountingPeriod();
        newPeriod.setCompanyId(1L);
        newPeriod.setFiscalYear(2025);
        newPeriod.setPeriodNumber(1);
        newPeriod.setPeriodName("January 2025 - Duplicate");
        newPeriod.setStartDate(LocalDate.of(2025, Month.JANUARY, 1));
        newPeriod.setEndDate(LocalDate.of(2025, Month.JANUARY, 31));

        when(periodRepository.countOverlappingPeriods(1L,
                LocalDate.of(2025, Month.JANUARY, 1),
                LocalDate.of(2025, Month.JANUARY, 31)))
                .thenReturn(1L); // One overlapping period

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            periodService.createPeriod(newPeriod);
        });
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void closePeriod_WithDraftVouchers_ThrowsException() {
        // Given
        PeriodCloseRequest request = new PeriodCloseRequest();
        request.setReason("Month end closing");

        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));
        when(periodRepository.countDraftVouchersInPeriod(1L, testPeriod.getId()))
                .thenReturn(3L); // 3 draft vouchers exist

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            periodService.closePeriod(testPeriod.getId(), request);
        });
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("draft voucher");
    }

    @Test
    void getPeriodSummary_WithValidPeriod_ReturnsSummary() {
        // Given
        when(periodRepository.findByCompanyIdAndId(1L, testPeriod.getId()))
                .thenReturn(Optional.of(testPeriod));
        when(periodRepository.countDraftVouchersInPeriod(1L, testPeriod.getId()))
                .thenReturn(5L);
        when(voucherRepository.findByCompanyIdAndPeriodId(1L, testPeriod.getId()))
                .thenReturn(List.of()); // No vouchers for simplicity
        when(periodRepository.findCurrentPeriodByCompanyId(1L))
                .thenReturn(Optional.of(testPeriod));

        // When
        Optional<PeriodSummaryDTO> result = periodService.getPeriodSummary(testPeriod.getId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPeriodName()).isEqualTo("January 2025");
        assertThat(result.get().getDraftVouchersCount()).isEqualTo(5L);
        assertThat(result.get().getPostingFlowStatus()).isEqualTo("ACTIVE");
        assertThat(result.get().getIsCurrentPeriod()).isTrue();
    }

    @Test
    void isDateInOpenPeriod_OpenDate_ReturnsTrue() {
        // Given
        LocalDate date = LocalDate.of(2025, Month.JANUARY, 15);
        when(periodRepository.findByCompanyIdAndDate(1L, date))
                .thenReturn(Optional.of(testPeriod));

        // When
        boolean result = periodService.isDateInOpenPeriod(date);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isDateInOpenPeriod_ClosedDate_ReturnsFalse() {
        // Given
        LocalDate date = LocalDate.of(2024, Month.DECEMBER, 15);
        when(periodRepository.findByCompanyIdAndDate(1L, date))
                .thenReturn(Optional.of(testClosedPeriod));

        // When
        boolean result = periodService.isDateInOpenPeriod(date);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void validatePeriodForVoucherOperation_ClosedPeriod_ThrowsException() {
        // Given
        when(periodRepository.findByCompanyIdAndId(1L, testClosedPeriod.getId()))
                .thenReturn(Optional.of(testClosedPeriod));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            periodService.validatePeriodForVoucherOperation(testClosedPeriod.getId(), "VOUCHER_CREATE");
        });
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("closed period");
        verify(auditService).logPeriodValidationBlocked(testClosedPeriod.getId(), "VOUCHER_CREATE", "PERIOD_CLOSED");
    }

    @Test
    void validatePeriodForVoucherOperation_FuturePeriod_ThrowsException() {
        // Given
        AccountingPeriod futurePeriod = new AccountingPeriod();
        futurePeriod.setId(UUID.randomUUID());
        futurePeriod.setCompanyId(1L);
        futurePeriod.setFiscalYear(2026);
        futurePeriod.setPeriodNumber(1);
        futurePeriod.setPeriodName("January 2026");
        futurePeriod.setStartDate(LocalDate.of(2026, Month.JANUARY, 1));
        futurePeriod.setEndDate(LocalDate.of(2026, Month.JANUARY, 31));
        futurePeriod.setStatus(PeriodStatus.OPEN);

        when(periodRepository.findByCompanyIdAndId(1L, futurePeriod.getId()))
                .thenReturn(Optional.of(futurePeriod));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            periodService.validatePeriodForVoucherOperation(futurePeriod.getId(), "VOUCHER_POST");
        });
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).contains("future period");
        verify(auditService).logPeriodValidationBlocked(futurePeriod.getId(), "VOUCHER_POST", "PERIOD_FUTURE");
    }
}
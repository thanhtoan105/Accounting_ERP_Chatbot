package com.accounting.service.impl.reconciliation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.reconciliation.BankReconciliationDTO;
import com.accounting.dto.reconciliation.BankStatementLineDTO;
import com.accounting.dto.reconciliation.CreateAdjustmentRequestDTO;
import com.accounting.dto.reconciliation.CreateReconciliationRequestDTO;
import com.accounting.dto.reconciliation.MatchRequestDTO;
import com.accounting.dto.reconciliation.ReconciliationAdjustmentDTO;
import com.accounting.entity.BankAccount;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.entity.reconciliation.AdjustmentStatus;
import com.accounting.entity.reconciliation.AdjustmentType;
import com.accounting.entity.reconciliation.BankReconciliation;
import com.accounting.entity.reconciliation.BankStatementLine;
import com.accounting.entity.reconciliation.MatchStatus;
import com.accounting.entity.reconciliation.ReconciliationAdjustment;
import com.accounting.entity.reconciliation.ReconciliationStatus;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.repository.reconciliation.BankReconciliationRepository;
import com.accounting.repository.reconciliation.BankStatementLineRepository;
import com.accounting.repository.reconciliation.ReconciliationAdjustmentRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ReconciliationMatcherService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for BankReconciliationServiceImpl.
 * Tests reconciliation CRUD, matching, adjustments, and completion workflow.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BankReconciliationServiceImplTest {

    @Mock
    private BankReconciliationRepository reconciliationRepository;

    @Mock
    private BankStatementLineRepository statementLineRepository;

    @Mock
    private ReconciliationAdjustmentRepository adjustmentRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherLineRepository voucherLineRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReconciliationMatcherService matcherService;

    private BankReconciliationServiceImpl reconciliationService;

    private static final Long COMPANY_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long BANK_ACCOUNT_ID = 100L;
    private static final Long GL_ACCOUNT_ID = 200L;
    private static final String GL_ACCOUNT_CODE = "1121";

    private UUID reconciliationId;
    private BankReconciliation reconciliation;
    private BankAccount bankAccount;

    @BeforeEach
    void setUp() {
        reconciliationService = new BankReconciliationServiceImpl(
                reconciliationRepository,
                statementLineRepository,
                adjustmentRepository,
                bankAccountRepository,
                chartOfAccountsRepository,
                voucherRepository,
                voucherLineRepository,
                userRepository,
                matcherService);

        CompanyContext.setCompanyId(COMPANY_ID);

        reconciliationId = UUID.randomUUID();
        reconciliation = createMockReconciliation();
        bankAccount = createMockBankAccount();
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
        recon.setStatementBalance(new BigDecimal("10000"));
        recon.setLedgerBalance(new BigDecimal("10000"));
        recon.setStatus(ReconciliationStatus.IN_PROGRESS);
        return recon;
    }

    private BankAccount createMockBankAccount() {
        BankAccount account = new BankAccount();
        account.setId(BANK_ACCOUNT_ID);
        account.setCompanyId(COMPANY_ID);
        account.setGlAccountCode(GL_ACCOUNT_CODE);
        account.setBankName("Test Bank");
        account.setAccountNumber("123456789");
        account.setOpeningBalance(new BigDecimal("5000"));
        return account;
    }

    private BankStatementLine createStatementLine(UUID id, MatchStatus status) {
        BankStatementLine line = new BankStatementLine();
        line.setId(id);
        line.setReconciliation(reconciliation);
        line.setLineNumber(1);
        line.setTransactionDate(LocalDate.of(2024, 1, 15));
        line.setDebitAmount(BigDecimal.ZERO);
        line.setCreditAmount(new BigDecimal("1000"));
        line.setMatchStatus(status);
        return line;
    }

    private ReconciliationAdjustment createAdjustment(UUID id, AdjustmentStatus status) {
        ReconciliationAdjustment adj = new ReconciliationAdjustment();
        adj.setId(id);
        adj.setReconciliation(reconciliation);
        adj.setAdjustmentType(AdjustmentType.BANK_FEE);
        adj.setAmount(new BigDecimal("50"));
        adj.setDescription("Bank fee");
        adj.setAccountCode("6425");
        adj.setStatus(status);
        adj.setCreatedById(USER_ID);
        return adj;
    }

    @Nested
    @DisplayName("Create Reconciliation Tests")
    class CreateReconciliationTests {

        @Test
        @DisplayName("Should create reconciliation successfully")
        void createReconciliation_ValidRequest_Success() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                CreateReconciliationRequestDTO request = new CreateReconciliationRequestDTO();
                request.setBankAccountId(BANK_ACCOUNT_ID);
                request.setStatementPeriodStart(LocalDate.of(2024, 1, 1));
                request.setStatementPeriodEnd(LocalDate.of(2024, 1, 31));
                request.setStatementBalance(new BigDecimal("10000"));

                when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                        .thenReturn(Optional.of(bankAccount));
                when(reconciliationRepository.existsOverlappingPeriod(
                        eq(COMPANY_ID), eq(BANK_ACCOUNT_ID), any(), any()))
                        .thenReturn(false);
                when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, GL_ACCOUNT_CODE))
                        .thenReturn(Optional.of(new ChartOfAccount()));
                when(voucherLineRepository.findByCompanyIdAndAccountIdAndBankAccountId(any(), any(), any()))
                        .thenReturn(Collections.emptyList());
                when(reconciliationRepository.save(any(BankReconciliation.class)))
                        .thenAnswer(inv -> {
                            BankReconciliation saved = inv.getArgument(0);
                            saved.setId(UUID.randomUUID());
                            return saved;
                        });
                when(statementLineRepository.countByReconciliationId(any())).thenReturn(0L);
                when(statementLineRepository.countByReconciliationIdAndMatchStatus(any(), any())).thenReturn(0L);

                BankReconciliationDTO result = reconciliationService.createReconciliation(request);

                assertNotNull(result);
                assertEquals(BANK_ACCOUNT_ID, result.getBankAccountId());
                assertEquals(ReconciliationStatus.NOT_STARTED, result.getStatus());
                verify(reconciliationRepository).save(any(BankReconciliation.class));
            }
        }

        @Test
        @DisplayName("Should reject when start date is after end date")
        void createReconciliation_InvalidDateRange_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                CreateReconciliationRequestDTO request = new CreateReconciliationRequestDTO();
                request.setBankAccountId(BANK_ACCOUNT_ID);
                request.setStatementPeriodStart(LocalDate.of(2024, 2, 1)); // After end
                request.setStatementPeriodEnd(LocalDate.of(2024, 1, 31));
                request.setStatementBalance(new BigDecimal("10000"));

                when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                        .thenReturn(Optional.of(bankAccount));

                assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.createReconciliation(request));
            }
        }

        @Test
        @DisplayName("Should reject overlapping period")
        void createReconciliation_OverlappingPeriod_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                CreateReconciliationRequestDTO request = new CreateReconciliationRequestDTO();
                request.setBankAccountId(BANK_ACCOUNT_ID);
                request.setStatementPeriodStart(LocalDate.of(2024, 1, 1));
                request.setStatementPeriodEnd(LocalDate.of(2024, 1, 31));
                request.setStatementBalance(new BigDecimal("10000"));

                when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                        .thenReturn(Optional.of(bankAccount));
                when(reconciliationRepository.existsOverlappingPeriod(
                        eq(COMPANY_ID), eq(BANK_ACCOUNT_ID), any(), any()))
                        .thenReturn(true);

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.createReconciliation(request));
                assertTrue(exception.getMessage().contains("overlapping"));
            }
        }

        @Test
        @DisplayName("Should throw exception for non-existent bank account")
        void createReconciliation_BankAccountNotFound_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                CreateReconciliationRequestDTO request = new CreateReconciliationRequestDTO();
                request.setBankAccountId(999L);
                request.setStatementPeriodStart(LocalDate.of(2024, 1, 1));
                request.setStatementPeriodEnd(LocalDate.of(2024, 1, 31));
                request.setStatementBalance(new BigDecimal("10000"));

                when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, 999L))
                        .thenReturn(Optional.empty());

                assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.createReconciliation(request));
            }
        }
    }

    @Nested
    @DisplayName("Manual Match Tests")
    class ManualMatchTests {

        @Test
        @DisplayName("Should successfully match statement line to voucher")
        void manualMatch_ValidMatch_Success() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID lineId = UUID.randomUUID();
                UUID voucherId = UUID.randomUUID();
                BankStatementLine line = createStatementLine(lineId, MatchStatus.UNMATCHED);
                Voucher voucher = new Voucher();
                voucher.setId(voucherId);
                voucher.setStatus("posted");

                MatchRequestDTO request = new MatchRequestDTO();
                request.setStatementLineId(lineId);
                request.setVoucherId(voucherId);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(statementLineRepository.findById(lineId))
                        .thenReturn(Optional.of(line));
                when(voucherRepository.findByCompanyIdAndId(COMPANY_ID, voucherId))
                        .thenReturn(Optional.of(voucher));
                when(matcherService.canMatchVoucher(voucherId, reconciliationId))
                        .thenReturn(true);
                when(matcherService.calculateMatchConfidence(lineId, voucherId))
                        .thenReturn(0.85);
                when(matcherService.generateMatchReason(lineId, voucherId))
                        .thenReturn("Exact date match; Exact amount match");
                when(statementLineRepository.save(any(BankStatementLine.class)))
                        .thenAnswer(inv -> inv.getArgument(0));

                BankStatementLineDTO result = reconciliationService.manualMatch(reconciliationId, request);

                assertEquals(MatchStatus.MATCHED, result.getMatchStatus());
                assertEquals(voucherId, result.getMatchedVoucherId());
                verify(statementLineRepository).save(argThat(saved ->
                        saved.getMatchStatus() == MatchStatus.MATCHED &&
                                saved.getMatchedVoucherId().equals(voucherId)));
            }
        }

        @Test
        @DisplayName("Should reject matching to unposted voucher")
        void manualMatch_UnpostedVoucher_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID lineId = UUID.randomUUID();
                UUID voucherId = UUID.randomUUID();
                BankStatementLine line = createStatementLine(lineId, MatchStatus.UNMATCHED);
                Voucher voucher = new Voucher();
                voucher.setId(voucherId);
                voucher.setStatus("draft"); // Not posted

                MatchRequestDTO request = new MatchRequestDTO();
                request.setStatementLineId(lineId);
                request.setVoucherId(voucherId);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(statementLineRepository.findById(lineId))
                        .thenReturn(Optional.of(line));
                when(voucherRepository.findByCompanyIdAndId(COMPANY_ID, voucherId))
                        .thenReturn(Optional.of(voucher));

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.manualMatch(reconciliationId, request));
                assertTrue(exception.getMessage().contains("unposted"));
            }
        }

        @Test
        @DisplayName("Should reject matching to already matched voucher in other reconciliation")
        void manualMatch_VoucherAlreadyMatched_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID lineId = UUID.randomUUID();
                UUID voucherId = UUID.randomUUID();
                BankStatementLine line = createStatementLine(lineId, MatchStatus.UNMATCHED);
                Voucher voucher = new Voucher();
                voucher.setId(voucherId);
                voucher.setStatus("posted");

                MatchRequestDTO request = new MatchRequestDTO();
                request.setStatementLineId(lineId);
                request.setVoucherId(voucherId);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(statementLineRepository.findById(lineId))
                        .thenReturn(Optional.of(line));
                when(voucherRepository.findByCompanyIdAndId(COMPANY_ID, voucherId))
                        .thenReturn(Optional.of(voucher));
                when(matcherService.canMatchVoucher(voucherId, reconciliationId))
                        .thenReturn(false);

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.manualMatch(reconciliationId, request));
                assertTrue(exception.getMessage().contains("already matched"));
            }
        }

        @Test
        @DisplayName("Should reject matching on completed reconciliation")
        void manualMatch_CompletedReconciliation_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                reconciliation.setStatus(ReconciliationStatus.COMPLETED);

                MatchRequestDTO request = new MatchRequestDTO();
                request.setStatementLineId(UUID.randomUUID());
                request.setVoucherId(UUID.randomUUID());

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.manualMatch(reconciliationId, request));
                assertTrue(exception.getMessage().contains("completed"));
            }
        }
    }

    @Nested
    @DisplayName("Unmatch Tests")
    class UnmatchTests {

        @Test
        @DisplayName("Should successfully unmatch a statement line")
        void unmatch_ValidLine_Success() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID lineId = UUID.randomUUID();
                BankStatementLine line = createStatementLine(lineId, MatchStatus.MATCHED);
                line.setMatchedVoucherId(UUID.randomUUID());
                line.setMatchedAt(Instant.now());

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(statementLineRepository.findById(lineId))
                        .thenReturn(Optional.of(line));
                when(statementLineRepository.save(any(BankStatementLine.class)))
                        .thenAnswer(inv -> inv.getArgument(0));

                BankStatementLineDTO result = reconciliationService.unmatch(reconciliationId, lineId);

                assertEquals(MatchStatus.UNMATCHED, result.getMatchStatus());
                assertNull(result.getMatchedVoucherId());
                verify(statementLineRepository).save(argThat(saved ->
                        saved.getMatchStatus() == MatchStatus.UNMATCHED &&
                                saved.getMatchedVoucherId() == null));
            }
        }
    }

    @Nested
    @DisplayName("Adjustment Workflow Tests")
    class AdjustmentTests {

        @Test
        @DisplayName("Should create pending adjustment")
        void createAdjustment_ValidRequest_CreatedPending() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID lineId = UUID.randomUUID();
                BankStatementLine line = createStatementLine(lineId, MatchStatus.UNMATCHED);

                CreateAdjustmentRequestDTO request = new CreateAdjustmentRequestDTO();
                request.setStatementLineId(lineId);
                request.setAdjustmentType(AdjustmentType.BANK_FEE);
                request.setAmount(new BigDecimal("50"));
                request.setDescription("Monthly bank fee");
                request.setAccountCode("6425");

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(statementLineRepository.findById(lineId))
                        .thenReturn(Optional.of(line));
                when(statementLineRepository.save(any(BankStatementLine.class)))
                        .thenReturn(line);
                when(adjustmentRepository.save(any(ReconciliationAdjustment.class)))
                        .thenAnswer(inv -> {
                            ReconciliationAdjustment saved = inv.getArgument(0);
                            saved.setId(UUID.randomUUID());
                            return saved;
                        });

                ReconciliationAdjustmentDTO result = reconciliationService.createAdjustment(
                        reconciliationId, request);

                assertNotNull(result);
                assertEquals(AdjustmentStatus.PENDING, result.getStatus());
                assertEquals(AdjustmentType.BANK_FEE, result.getAdjustmentType());
            }
        }

        @Test
        @DisplayName("Should approve pending adjustment")
        void approveAdjustment_PendingAdjustment_Approved() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID adjustmentId = UUID.randomUUID();
                ReconciliationAdjustment adjustment = createAdjustment(adjustmentId, AdjustmentStatus.PENDING);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(adjustmentRepository.findByReconciliationIdAndId(reconciliationId, adjustmentId))
                        .thenReturn(Optional.of(adjustment));
                when(adjustmentRepository.save(any(ReconciliationAdjustment.class)))
                        .thenAnswer(inv -> inv.getArgument(0));

                ReconciliationAdjustmentDTO result = reconciliationService.approveAdjustment(
                        reconciliationId, adjustmentId);

                assertEquals(AdjustmentStatus.APPROVED, result.getStatus());
                assertNotNull(result.getApprovedAt());
            }
        }

        @Test
        @DisplayName("Should reject pending adjustment with reason")
        void rejectAdjustment_PendingAdjustment_RejectedWithReason() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID adjustmentId = UUID.randomUUID();
                ReconciliationAdjustment adjustment = createAdjustment(adjustmentId, AdjustmentStatus.PENDING);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(adjustmentRepository.findByReconciliationIdAndId(reconciliationId, adjustmentId))
                        .thenReturn(Optional.of(adjustment));
                when(adjustmentRepository.save(any(ReconciliationAdjustment.class)))
                        .thenAnswer(inv -> inv.getArgument(0));

                ReconciliationAdjustmentDTO result = reconciliationService.rejectAdjustment(
                        reconciliationId, adjustmentId, "Incorrect amount");

                assertEquals(AdjustmentStatus.REJECTED, result.getStatus());
                assertTrue(result.getDescription().contains("REJECTED: Incorrect amount"));
            }
        }

        @Test
        @DisplayName("Should post approved adjustment")
        void postAdjustment_ApprovedAdjustment_Posted() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID adjustmentId = UUID.randomUUID();
                ReconciliationAdjustment adjustment = createAdjustment(adjustmentId, AdjustmentStatus.APPROVED);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(adjustmentRepository.findByReconciliationIdAndId(reconciliationId, adjustmentId))
                        .thenReturn(Optional.of(adjustment));
                when(adjustmentRepository.save(any(ReconciliationAdjustment.class)))
                        .thenAnswer(inv -> inv.getArgument(0));

                ReconciliationAdjustmentDTO result = reconciliationService.postAdjustment(
                        reconciliationId, adjustmentId);

                assertEquals(AdjustmentStatus.POSTED, result.getStatus());
            }
        }

        @Test
        @DisplayName("Should reject posting unapproved adjustment")
        void postAdjustment_PendingAdjustment_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID adjustmentId = UUID.randomUUID();
                ReconciliationAdjustment adjustment = createAdjustment(adjustmentId, AdjustmentStatus.PENDING);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(adjustmentRepository.findByReconciliationIdAndId(reconciliationId, adjustmentId))
                        .thenReturn(Optional.of(adjustment));

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.postAdjustment(reconciliationId, adjustmentId));
                assertTrue(exception.getMessage().contains("approved before posting"));
            }
        }

        @Test
        @DisplayName("Should only allow deleting pending adjustments")
        void deleteAdjustment_PostedAdjustment_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                UUID adjustmentId = UUID.randomUUID();
                ReconciliationAdjustment adjustment = createAdjustment(adjustmentId, AdjustmentStatus.POSTED);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(adjustmentRepository.findByReconciliationIdAndId(reconciliationId, adjustmentId))
                        .thenReturn(Optional.of(adjustment));

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.deleteAdjustment(reconciliationId, adjustmentId));
                assertTrue(exception.getMessage().contains("pending"));
            }
        }
    }

    @Nested
    @DisplayName("Complete Reconciliation Tests")
    class CompleteReconciliationTests {

        @Test
        @DisplayName("Should complete reconciliation when all lines matched")
        void completeReconciliation_AllMatched_Success() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                // Set up statement and ledger balances to match
                reconciliation.setStatementBalance(new BigDecimal("11000")); // 10000 + 1000 matched
                reconciliation.setLedgerBalance(new BigDecimal("10000"));

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(statementLineRepository.countByReconciliationIdAndMatchStatus(
                        reconciliationId, MatchStatus.UNMATCHED))
                        .thenReturn(0L);
                when(adjustmentRepository.hasUnpostedAdjustments(reconciliationId))
                        .thenReturn(false);

                // Matched lines for balance calculation (credit of 1000)
                BankStatementLine matchedLine = createStatementLine(UUID.randomUUID(), MatchStatus.MATCHED);
                when(statementLineRepository.findByReconciliationIdAndMatchStatus(
                        reconciliationId, MatchStatus.MATCHED))
                        .thenReturn(List.of(matchedLine));

                when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                        .thenReturn(Optional.of(bankAccount));
                when(bankAccountRepository.save(any(BankAccount.class)))
                        .thenReturn(bankAccount);
                when(reconciliationRepository.save(any(BankReconciliation.class)))
                        .thenAnswer(inv -> inv.getArgument(0));
                when(statementLineRepository.countByReconciliationId(any())).thenReturn(1L);
                when(statementLineRepository.countByReconciliationIdAndMatchStatus(
                        any(), eq(MatchStatus.MATCHED))).thenReturn(1L);
                when(statementLineRepository.countByReconciliationIdAndMatchStatus(
                        any(), eq(MatchStatus.ADJUSTMENT_REQUIRED))).thenReturn(0L);
                when(statementLineRepository.findByReconciliationIdOrderByLineNumber(any()))
                        .thenReturn(Collections.emptyList());
                when(adjustmentRepository.findByReconciliationId(any()))
                        .thenReturn(Collections.emptyList());

                BankReconciliationDTO result = reconciliationService.completeReconciliation(
                        reconciliationId, "Final notes");

                assertEquals(ReconciliationStatus.COMPLETED, result.getStatus());
                assertNotNull(result.getCompletedAt());
                verify(bankAccountRepository).save(any(BankAccount.class));
            }
        }

        @Test
        @DisplayName("Should reject completion with unmatched lines")
        void completeReconciliation_HasUnmatchedLines_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(statementLineRepository.countByReconciliationIdAndMatchStatus(
                        reconciliationId, MatchStatus.UNMATCHED))
                        .thenReturn(5L);

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.completeReconciliation(reconciliationId, null));
                assertTrue(exception.getMessage().contains("unmatched"));
            }
        }

        @Test
        @DisplayName("Should reject completion with unposted adjustments")
        void completeReconciliation_HasUnpostedAdjustments_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(statementLineRepository.countByReconciliationIdAndMatchStatus(
                        reconciliationId, MatchStatus.UNMATCHED))
                        .thenReturn(0L);
                when(adjustmentRepository.hasUnpostedAdjustments(reconciliationId))
                        .thenReturn(true);

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.completeReconciliation(reconciliationId, null));
                assertTrue(exception.getMessage().contains("adjustment"));
            }
        }
    }

    @Nested
    @DisplayName("Reopen Reconciliation Tests")
    class ReopenReconciliationTests {

        @Test
        @DisplayName("Should reopen completed reconciliation")
        void reopenReconciliation_Completed_Success() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                reconciliation.setStatus(ReconciliationStatus.COMPLETED);
                reconciliation.setCompletedAt(Instant.now());
                reconciliation.setCompletedById(USER_ID);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));
                when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                        .thenReturn(Optional.of(bankAccount));
                when(reconciliationRepository.save(any(BankReconciliation.class)))
                        .thenAnswer(inv -> inv.getArgument(0));
                when(statementLineRepository.countByReconciliationId(any())).thenReturn(0L);
                when(statementLineRepository.countByReconciliationIdAndMatchStatus(any(), any())).thenReturn(0L);
                when(statementLineRepository.findByReconciliationIdOrderByLineNumber(any()))
                        .thenReturn(Collections.emptyList());
                when(adjustmentRepository.findByReconciliationId(any()))
                        .thenReturn(Collections.emptyList());

                BankReconciliationDTO result = reconciliationService.reopenReconciliation(reconciliationId);

                assertEquals(ReconciliationStatus.IN_PROGRESS, result.getStatus());
                assertNull(result.getCompletedAt());
            }
        }

        @Test
        @DisplayName("Should reject reopening non-completed reconciliation")
        void reopenReconciliation_NotCompleted_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                reconciliation.setStatus(ReconciliationStatus.IN_PROGRESS);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.reopenReconciliation(reconciliationId));
                assertTrue(exception.getMessage().contains("not completed"));
            }
        }
    }

    @Nested
    @DisplayName("Delete Reconciliation Tests")
    class DeleteReconciliationTests {

        @Test
        @DisplayName("Should delete non-completed reconciliation")
        void deleteReconciliation_NotCompleted_Success() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));

                reconciliationService.deleteReconciliation(reconciliationId);

                verify(adjustmentRepository).deleteByReconciliationId(reconciliationId);
                verify(statementLineRepository).deleteByReconciliationId(reconciliationId);
                verify(reconciliationRepository).delete(reconciliation);
            }
        }

        @Test
        @DisplayName("Should reject deleting completed reconciliation")
        void deleteReconciliation_Completed_ThrowsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

                reconciliation.setStatus(ReconciliationStatus.COMPLETED);

                when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                        .thenReturn(Optional.of(reconciliation));

                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> reconciliationService.deleteReconciliation(reconciliationId));
                assertTrue(exception.getMessage().contains("completed"));
            }
        }
    }

    @Nested
    @DisplayName("List and Query Tests")
    class ListQueryTests {

        @Test
        @DisplayName("Should list reconciliations with pagination")
        void listReconciliations_WithPagination_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<BankReconciliation> page = new PageImpl<>(List.of(reconciliation));

            when(reconciliationRepository.findByCompanyId(COMPANY_ID, pageable))
                    .thenReturn(page);
            when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.of(bankAccount));
            when(statementLineRepository.countByReconciliationId(any())).thenReturn(0L);
            when(statementLineRepository.countByReconciliationIdAndMatchStatus(any(), any())).thenReturn(0L);

            var result = reconciliationService.listReconciliations(null, null, pageable);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Should get reconciliation details")
        void getReconciliation_Exists_ReturnsDTO() {
            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.of(bankAccount));
            when(statementLineRepository.countByReconciliationId(any())).thenReturn(0L);
            when(statementLineRepository.countByReconciliationIdAndMatchStatus(any(), any())).thenReturn(0L);
            when(statementLineRepository.findByReconciliationIdOrderByLineNumber(any()))
                    .thenReturn(Collections.emptyList());
            when(adjustmentRepository.findByReconciliationId(any()))
                    .thenReturn(Collections.emptyList());

            BankReconciliationDTO result = reconciliationService.getReconciliation(reconciliationId);

            assertNotNull(result);
            assertEquals(reconciliationId, result.getId());
            assertEquals(BANK_ACCOUNT_ID, result.getBankAccountId());
        }
    }

    @Nested
    @DisplayName("Export Tests")
    class ExportTests {

        @Test
        @DisplayName("Should export reconciliation to Excel")
        void exportToExcel_ValidReconciliation_ReturnsBytes() {
            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));
            when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.of(bankAccount));
            when(statementLineRepository.findByReconciliationIdOrderByLineNumber(reconciliationId))
                    .thenReturn(Collections.emptyList());

            byte[] result = reconciliationService.exportToExcel(reconciliationId);

            assertNotNull(result);
            assertTrue(result.length > 0);
            // Check for XLSX magic bytes (PK header)
            assertEquals((byte) 0x50, result[0]);
            assertEquals((byte) 0x4B, result[1]);
        }

        @Test
        @DisplayName("PDF export should throw UnsupportedOperationException")
        void exportToPdf_NotImplemented_ThrowsException() {
            when(reconciliationRepository.findByCompanyIdAndId(COMPANY_ID, reconciliationId))
                    .thenReturn(Optional.of(reconciliation));

            assertThrows(UnsupportedOperationException.class,
                    () -> reconciliationService.exportToPdf(reconciliationId));
        }
    }

    @Nested
    @DisplayName("Context Validation Tests")
    class ContextValidationTests {

        @Test
        @DisplayName("Should throw exception when company context not set")
        void anyOperation_NoCompanyContext_ThrowsException() {
            CompanyContext.clear();

            assertThrows(ResponseStatusException.class,
                    () -> reconciliationService.getReconciliation(reconciliationId));
        }
    }
}

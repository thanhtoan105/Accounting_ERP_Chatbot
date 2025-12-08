package com.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.VoucherDTO;
import com.accounting.dto.cashbook.CashBookResponseDTO;
import com.accounting.dto.cashbook.CashBookSummaryDTO;
import com.accounting.entity.BankAccount;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.SupplierRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.impl.cashbook.CashBookServiceImpl;

/**
 * Unit tests for CashBookService.
 * Tests balance calculations, filter logic, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class CashBookServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long BANK_ACCOUNT_ID = 100L;
    private static final Long GL_ACCOUNT_ID = 200L;
    private static final String GL_ACCOUNT_CODE = "1111";

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherLineRepository voucherLineRepository;

    @Mock
    private VoucherService voucherService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private CashBookServiceImpl cashBookService;

    private BankAccount testBankAccount;
    private ChartOfAccount testGlAccount;

    @BeforeEach
    void setUp() {
        CompanyContext.setCompanyId(COMPANY_ID);

        testBankAccount = new BankAccount();
        testBankAccount.setId(BANK_ACCOUNT_ID);
        testBankAccount.setCompanyId(COMPANY_ID);
        testBankAccount.setAccountNumber("123456789");
        testBankAccount.setBankName("Test Bank");
        testBankAccount.setType(BankAccount.AccountType.BANK);
        testBankAccount.setGlAccountCode(GL_ACCOUNT_CODE);
        testBankAccount.setOpeningBalance(new BigDecimal("1000000.00"));
        testBankAccount.setActive(true);

        testGlAccount = new ChartOfAccount();
        testGlAccount.setId(GL_ACCOUNT_ID);
        testGlAccount.setCompanyId(COMPANY_ID);
        testGlAccount.setCode(GL_ACCOUNT_CODE);
        testGlAccount.setName("Cash");
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Nested
    @DisplayName("getCashBook - Basic Operations")
    class GetCashBookBasicTests {

        @Test
        @DisplayName("Should return empty transactions when no voucher lines exist")
        void shouldReturnEmptyTransactionsWhenNoVoucherLinesExist() {
            // Given
            when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.of(testBankAccount));
            when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, GL_ACCOUNT_CODE))
                    .thenReturn(Optional.of(testGlAccount));
            when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, GL_ACCOUNT_ID))
                    .thenReturn(new ArrayList<>());

            // When
            CashBookResponseDTO response = cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, null, null, null, null, 0, 20);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getBankAccountId()).isEqualTo(BANK_ACCOUNT_ID);
            assertThat(response.getAccountNumber()).isEqualTo("123456789");
            assertThat(response.getBankName()).isEqualTo("Test Bank");
            assertThat(response.getOpeningBalance()).isEqualByComparingTo("1000000.00");
            assertThat(response.getClosingBalance()).isEqualByComparingTo("1000000.00");
            assertThat(response.getTotalInflow()).isEqualByComparingTo("0");
            assertThat(response.getTotalOutflow()).isEqualByComparingTo("0");
            assertThat(response.getTransactions()).isEmpty();
            assertThat(response.getTotalCount()).isZero();
        }

        @Test
        @DisplayName("Should throw exception when bank account not found")
        void shouldThrowExceptionWhenBankAccountNotFound() {
            // Given
            when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, null, null, null, null, 0, 20))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Bank account not found");
        }

        @Test
        @DisplayName("Should throw exception when GL account code not configured")
        void shouldThrowExceptionWhenGlAccountCodeNotConfigured() {
            // Given
            testBankAccount.setGlAccountCode(null);
            when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.of(testBankAccount));

            // When/Then
            assertThatThrownBy(() -> cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, null, null, null, null, 0, 20))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("no GL account code configured");
        }

        @Test
        @DisplayName("Should throw exception when company context is missing")
        void shouldThrowExceptionWhenCompanyContextMissing() {
            // Given
            CompanyContext.clear();

            // When/Then
            assertThatThrownBy(() -> cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, null, null, null, null, 0, 20))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Missing company context");
        }
    }

    @Nested
    @DisplayName("getCashBook - Balance Calculations")
    class GetCashBookBalanceTests {

        @Test
        @DisplayName("Should calculate running balance correctly for receipts")
        void shouldCalculateRunningBalanceForReceipts() {
            // Given
            setupMocksForTransactionTest();

            UUID voucherId1 = UUID.randomUUID();
            UUID voucherId2 = UUID.randomUUID();

            // Create voucher lines (receipts = debits to cash account)
            VoucherLine line1 = createVoucherLine(voucherId1, new BigDecimal("500000"), BigDecimal.ZERO);
            VoucherLine line2 = createVoucherLine(voucherId2, new BigDecimal("300000"), BigDecimal.ZERO);

            when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, GL_ACCOUNT_ID))
                    .thenReturn(List.of(line1, line2));

            // Create vouchers
            Voucher voucher1 = createPostedVoucher(voucherId1, LocalDate.of(2024, 1, 1));
            Voucher voucher2 = createPostedVoucher(voucherId2, LocalDate.of(2024, 1, 2));

            when(voucherRepository.findById(voucherId1)).thenReturn(Optional.of(voucher1));
            when(voucherRepository.findById(voucherId2)).thenReturn(Optional.of(voucher2));

            // When
            CashBookResponseDTO response = cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, null, null, null, null, 0, 20);

            // Then
            assertThat(response.getTransactions()).hasSize(2);
            assertThat(response.getTotalInflow()).isEqualByComparingTo("800000");
            assertThat(response.getTotalOutflow()).isEqualByComparingTo("0");
            // Opening: 1,000,000 + Inflow: 800,000 = 1,800,000
            assertThat(response.getClosingBalance()).isEqualByComparingTo("1800000");

            // Check running balances
            // First transaction: 1,000,000 + 500,000 = 1,500,000
            assertThat(response.getTransactions().get(0).getRunningBalance())
                    .isEqualByComparingTo("1500000");
            // Second transaction: 1,500,000 + 300,000 = 1,800,000
            assertThat(response.getTransactions().get(1).getRunningBalance())
                    .isEqualByComparingTo("1800000");
        }

        @Test
        @DisplayName("Should calculate running balance correctly for payments")
        void shouldCalculateRunningBalanceForPayments() {
            // Given
            setupMocksForTransactionTest();

            UUID voucherId1 = UUID.randomUUID();
            UUID voucherId2 = UUID.randomUUID();

            // Create voucher lines (payments = credits from cash account)
            VoucherLine line1 = createVoucherLine(voucherId1, BigDecimal.ZERO, new BigDecimal("200000"));
            VoucherLine line2 = createVoucherLine(voucherId2, BigDecimal.ZERO, new BigDecimal("100000"));

            when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, GL_ACCOUNT_ID))
                    .thenReturn(List.of(line1, line2));

            Voucher voucher1 = createPostedVoucher(voucherId1, LocalDate.of(2024, 1, 1));
            Voucher voucher2 = createPostedVoucher(voucherId2, LocalDate.of(2024, 1, 2));

            when(voucherRepository.findById(voucherId1)).thenReturn(Optional.of(voucher1));
            when(voucherRepository.findById(voucherId2)).thenReturn(Optional.of(voucher2));

            // When
            CashBookResponseDTO response = cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, null, null, null, null, 0, 20);

            // Then
            assertThat(response.getTotalInflow()).isEqualByComparingTo("0");
            assertThat(response.getTotalOutflow()).isEqualByComparingTo("300000");
            // Opening: 1,000,000 - Outflow: 300,000 = 700,000
            assertThat(response.getClosingBalance()).isEqualByComparingTo("700000");
        }

        @Test
        @DisplayName("Should calculate opening balance including historical transactions")
        void shouldCalculateOpeningBalanceWithHistoricalTransactions() {
            // Given
            setupMocksForTransactionTest();

            UUID historicalVoucherId = UUID.randomUUID();
            UUID currentVoucherId = UUID.randomUUID();

            // Historical transaction (before filter date)
            VoucherLine historicalLine = createVoucherLine(historicalVoucherId, new BigDecimal("250000"),
                    BigDecimal.ZERO);
            // Current transaction (within filter date range)
            VoucherLine currentLine = createVoucherLine(currentVoucherId, new BigDecimal("150000"), BigDecimal.ZERO);

            when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, GL_ACCOUNT_ID))
                    .thenReturn(List.of(historicalLine, currentLine));

            // Historical voucher dated before the filter
            Voucher historicalVoucher = createPostedVoucher(historicalVoucherId, LocalDate.of(2024, 1, 5));
            // Current voucher within the date range
            Voucher currentVoucher = createPostedVoucher(currentVoucherId, LocalDate.of(2024, 1, 15));

            when(voucherRepository.findById(historicalVoucherId)).thenReturn(Optional.of(historicalVoucher));
            when(voucherRepository.findById(currentVoucherId)).thenReturn(Optional.of(currentVoucher));

            // When - filter from Jan 10 to Jan 31
            CashBookResponseDTO response = cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 31), null, null, 0, 20);

            // Then
            // Opening balance should include the bank account opening balance + historical
            // transaction
            // 1,000,000 + 250,000 = 1,250,000
            assertThat(response.getOpeningBalance()).isEqualByComparingTo("1250000");
            // Only the current transaction should be in the list
            assertThat(response.getTransactions()).hasSize(1);
            // Closing balance = Opening + current transaction
            // 1,250,000 + 150,000 = 1,400,000
            assertThat(response.getClosingBalance()).isEqualByComparingTo("1400000");
        }

        @Test
        @DisplayName("Should handle negative balance correctly")
        void shouldHandleNegativeBalanceCorrectly() {
            // Given
            testBankAccount.setOpeningBalance(new BigDecimal("100000.00"));
            setupMocksForTransactionTest();

            UUID voucherId = UUID.randomUUID();
            // Payment larger than opening balance
            VoucherLine line = createVoucherLine(voucherId, BigDecimal.ZERO, new BigDecimal("150000"));

            when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, GL_ACCOUNT_ID))
                    .thenReturn(List.of(line));

            Voucher voucher = createPostedVoucher(voucherId, LocalDate.of(2024, 1, 1));
            when(voucherRepository.findById(voucherId)).thenReturn(Optional.of(voucher));

            // When
            CashBookResponseDTO response = cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, null, null, null, null, 0, 20);

            // Then
            // Opening: 100,000 - Outflow: 150,000 = -50,000
            assertThat(response.getClosingBalance()).isEqualByComparingTo("-50000");
            assertThat(response.getTransactions().get(0).isNegativeBalance()).isTrue();
        }
    }

    @Nested
    @DisplayName("getCashBook - Filtering")
    class GetCashBookFilterTests {

        @Test
        @DisplayName("Should filter by transaction type - receipts only")
        void shouldFilterByTransactionTypeReceiptsOnly() {
            // Given
            setupMocksForTransactionTest();

            UUID receiptVoucherId = UUID.randomUUID();
            UUID paymentVoucherId = UUID.randomUUID();

            VoucherLine receiptLine = createVoucherLine(receiptVoucherId, new BigDecimal("500000"), BigDecimal.ZERO);
            VoucherLine paymentLine = createVoucherLine(paymentVoucherId, BigDecimal.ZERO, new BigDecimal("200000"));

            when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, GL_ACCOUNT_ID))
                    .thenReturn(List.of(receiptLine, paymentLine));

            Voucher receiptVoucher = createPostedVoucher(receiptVoucherId, LocalDate.of(2024, 1, 1));
            Voucher paymentVoucher = createPostedVoucher(paymentVoucherId, LocalDate.of(2024, 1, 2));

            when(voucherRepository.findById(receiptVoucherId)).thenReturn(Optional.of(receiptVoucher));
            when(voucherRepository.findById(paymentVoucherId)).thenReturn(Optional.of(paymentVoucher));

            // When - filter for receipts only
            CashBookResponseDTO response = cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, null, null, "receipt", null, 0, 20);

            // Then
            assertThat(response.getTransactions()).hasSize(1);
            assertThat(response.getTransactions().get(0).getTransactionType()).isEqualTo("receipt");
        }

        @Test
        @DisplayName("Should filter by date range")
        void shouldFilterByDateRange() {
            // Given
            setupMocksForTransactionTest();

            UUID voucherId1 = UUID.randomUUID();
            UUID voucherId2 = UUID.randomUUID();
            UUID voucherId3 = UUID.randomUUID();

            VoucherLine line1 = createVoucherLine(voucherId1, new BigDecimal("100000"), BigDecimal.ZERO);
            VoucherLine line2 = createVoucherLine(voucherId2, new BigDecimal("200000"), BigDecimal.ZERO);
            VoucherLine line3 = createVoucherLine(voucherId3, new BigDecimal("300000"), BigDecimal.ZERO);

            when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, GL_ACCOUNT_ID))
                    .thenReturn(List.of(line1, line2, line3));

            // Vouchers with different dates
            Voucher voucher1 = createPostedVoucher(voucherId1, LocalDate.of(2024, 1, 5)); // Before range
            Voucher voucher2 = createPostedVoucher(voucherId2, LocalDate.of(2024, 1, 15)); // Within range
            Voucher voucher3 = createPostedVoucher(voucherId3, LocalDate.of(2024, 1, 25)); // After range

            when(voucherRepository.findById(voucherId1)).thenReturn(Optional.of(voucher1));
            when(voucherRepository.findById(voucherId2)).thenReturn(Optional.of(voucher2));
            when(voucherRepository.findById(voucherId3)).thenReturn(Optional.of(voucher3));

            // When - filter from Jan 10 to Jan 20
            CashBookResponseDTO response = cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 20), null, null, 0, 20);

            // Then
            assertThat(response.getTransactions()).hasSize(1);
            assertThat(response.getTransactions().get(0).getDebit()).isEqualByComparingTo("200000");
        }

        @Test
        @DisplayName("Should only include posted vouchers")
        void shouldOnlyIncludePostedVouchers() {
            // Given
            setupMocksForTransactionTest();

            UUID postedVoucherId = UUID.randomUUID();
            UUID draftVoucherId = UUID.randomUUID();

            VoucherLine postedLine = createVoucherLine(postedVoucherId, new BigDecimal("500000"), BigDecimal.ZERO);
            VoucherLine draftLine = createVoucherLine(draftVoucherId, new BigDecimal("300000"), BigDecimal.ZERO);

            when(voucherLineRepository.findByCompanyIdAndAccountId(COMPANY_ID, GL_ACCOUNT_ID))
                    .thenReturn(List.of(postedLine, draftLine));

            Voucher postedVoucher = createPostedVoucher(postedVoucherId, LocalDate.of(2024, 1, 1));
            Voucher draftVoucher = new Voucher();
            draftVoucher.setId(draftVoucherId);
            draftVoucher.setStatus("draft"); // Not posted
            draftVoucher.setVoucherDate(LocalDate.of(2024, 1, 2));

            when(voucherRepository.findById(postedVoucherId)).thenReturn(Optional.of(postedVoucher));
            when(voucherRepository.findById(draftVoucherId)).thenReturn(Optional.of(draftVoucher));

            // When
            CashBookResponseDTO response = cashBookService.getCashBook(
                    BANK_ACCOUNT_ID, null, null, null, null, 0, 20);

            // Then
            assertThat(response.getTransactions()).hasSize(1);
            assertThat(response.getTransactions().get(0).getDebit()).isEqualByComparingTo("500000");
        }
    }

    @Nested
    @DisplayName("getCashBookSummary - Multi-Account View")
    class GetCashBookSummaryTests {

        @Test
        @DisplayName("Should return summary for all active accounts when no IDs specified")
        void shouldReturnSummaryForAllActiveAccounts() {
            // Given
            BankAccount cashAccount = createBankAccount(101L, "Cash Account", "1111", BankAccount.AccountType.CASH);
            BankAccount bankAccount = createBankAccount(102L, "Bank Account", "1121", BankAccount.AccountType.BANK);

            when(bankAccountRepository.findByCompanyIdAndActive(COMPANY_ID, true))
                    .thenReturn(List.of(cashAccount, bankAccount));

            ChartOfAccount cashGl = createChartOfAccount(201L, "1111");
            ChartOfAccount bankGl = createChartOfAccount(202L, "1121");

            when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "1111"))
                    .thenReturn(Optional.of(cashGl));
            when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "1121"))
                    .thenReturn(Optional.of(bankGl));

            when(voucherLineRepository.findByCompanyIdAndAccountId(eq(COMPANY_ID), any()))
                    .thenReturn(new ArrayList<>());

            // When
            CashBookSummaryDTO summary = cashBookService.getCashBookSummary(null, null, null);

            // Then
            assertThat(summary.getAccounts()).hasSize(2);
            assertThat(summary.getGrandTotals()).isNotNull();
            assertThat(summary.getGrandTotals().getTotalTransactionCount()).isZero();
        }

        @Test
        @DisplayName("Should calculate grand totals correctly")
        void shouldCalculateGrandTotalsCorrectly() {
            // Given
            BankAccount account1 = createBankAccount(101L, "Account 1", "1111", BankAccount.AccountType.CASH);
            account1.setOpeningBalance(new BigDecimal("500000"));
            BankAccount account2 = createBankAccount(102L, "Account 2", "1121", BankAccount.AccountType.BANK);
            account2.setOpeningBalance(new BigDecimal("300000"));

            when(bankAccountRepository.findByCompanyIdAndActive(COMPANY_ID, true))
                    .thenReturn(List.of(account1, account2));

            ChartOfAccount gl1 = createChartOfAccount(201L, "1111");
            ChartOfAccount gl2 = createChartOfAccount(202L, "1121");

            when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "1111"))
                    .thenReturn(Optional.of(gl1));
            when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, "1121"))
                    .thenReturn(Optional.of(gl2));

            when(voucherLineRepository.findByCompanyIdAndAccountId(eq(COMPANY_ID), any()))
                    .thenReturn(new ArrayList<>());

            // When
            CashBookSummaryDTO summary = cashBookService.getCashBookSummary(null, null, null);

            // Then
            assertThat(summary.getGrandTotals().getTotalOpeningBalance())
                    .isEqualByComparingTo("800000");
            assertThat(summary.getGrandTotals().getTotalClosingBalance())
                    .isEqualByComparingTo("800000");
        }
    }

    @Nested
    @DisplayName("getVoucherDetail - Drill-Down")
    class GetVoucherDetailTests {

        @Test
        @DisplayName("Should return voucher detail for valid voucher")
        void shouldReturnVoucherDetailForValidVoucher() {
            // Given
            UUID voucherId = UUID.randomUUID();
            VoucherDTO voucherDTO = new VoucherDTO();
            voucherDTO.setId(voucherId);

            when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.of(testBankAccount));
            when(voucherService.getVoucherById(voucherId))
                    .thenReturn(Optional.of(voucherDTO));

            // When
            VoucherDTO result = cashBookService.getVoucherDetail(BANK_ACCOUNT_ID, voucherId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(voucherId);
        }

        @Test
        @DisplayName("Should throw exception when voucher not found")
        void shouldThrowExceptionWhenVoucherNotFound() {
            // Given
            UUID voucherId = UUID.randomUUID();

            when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                    .thenReturn(Optional.of(testBankAccount));
            when(voucherService.getVoucherById(voucherId))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> cashBookService.getVoucherDetail(BANK_ACCOUNT_ID, voucherId))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Voucher not found");
        }
    }

    // Helper methods

    private void setupMocksForTransactionTest() {
        when(bankAccountRepository.findByCompanyIdAndId(COMPANY_ID, BANK_ACCOUNT_ID))
                .thenReturn(Optional.of(testBankAccount));
        when(chartOfAccountsRepository.findByCompanyIdAndCode(COMPANY_ID, GL_ACCOUNT_CODE))
                .thenReturn(Optional.of(testGlAccount));
    }

    private VoucherLine createVoucherLine(UUID voucherId, BigDecimal debit, BigDecimal credit) {
        VoucherLine line = new VoucherLine();
        line.setId(UUID.randomUUID());
        line.setVoucherId(voucherId);
        line.setCompanyId(COMPANY_ID);
        line.setAccountId(GL_ACCOUNT_ID);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setLineNumber(1);
        return line;
    }

    private Voucher createPostedVoucher(UUID voucherId, LocalDate date) {
        Voucher voucher = new Voucher();
        voucher.setId(voucherId);
        voucher.setCompanyId(COMPANY_ID);
        voucher.setVoucherNumber("VCH-" + voucherId.toString().substring(0, 8));
        voucher.setVoucherDate(date);
        voucher.setStatus("posted");
        voucher.setPostedAt(Instant.now());
        voucher.setDescription("Test voucher");
        return voucher;
    }

    private BankAccount createBankAccount(Long id, String bankName, String glCode, BankAccount.AccountType type) {
        BankAccount account = new BankAccount();
        account.setId(id);
        account.setCompanyId(COMPANY_ID);
        account.setAccountNumber("ACC-" + id);
        account.setBankName(bankName);
        account.setType(type);
        account.setGlAccountCode(glCode);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setActive(true);
        return account;
    }

    private ChartOfAccount createChartOfAccount(Long id, String code) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setCompanyId(COMPANY_ID);
        account.setCode(code);
        account.setName("Account " + code);
        return account;
    }
}

package com.accounting.service.impl.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.accounting.entity.BankAccount;
import com.accounting.entity.JournalEntry;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.JournalEntryRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountBalanceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountBalanceServiceImplTest {

  @Mock private BankAccountRepository bankAccountRepository;

  @Mock private JournalEntryRepository journalEntryRepository;

  private AccountBalanceService accountBalanceService;

  @BeforeEach
  void setUp() {
    accountBalanceService =
        new AccountBalanceServiceImpl(bankAccountRepository, journalEntryRepository);
    CompanyContext.setCompanyId(1L);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void getAccountBalance_withJournalEntries_returnsCorrectBalance() {
    // Setup
    Long accountId = 1L;
    BankAccount account = createBankAccount(accountId, new BigDecimal("10000"));
    when(bankAccountRepository.findByCompanyIdAndId(1L, accountId)).thenReturn(Optional.of(account));

    JournalEntry debitEntry = createJournalEntry(accountId, new BigDecimal("5000"), BigDecimal.ZERO);
    JournalEntry creditEntry = createJournalEntry(accountId, BigDecimal.ZERO, new BigDecimal("2000"));

    when(journalEntryRepository.findAll())
        .thenReturn(Arrays.asList(debitEntry, creditEntry));

    // Execute
    BigDecimal balance = accountBalanceService.getAccountBalance(accountId);

    // Assert
    // Opening: 10000, Debit: +5000, Credit: -2000 = 13000
    assertEquals(new BigDecimal("13000"), balance);
  }

  @Test
  void getAccountBalance_noJournalEntries_returnsOpeningBalance() {
    // Setup
    Long accountId = 1L;
    BankAccount account = createBankAccount(accountId, new BigDecimal("10000"));
    when(bankAccountRepository.findByCompanyIdAndId(1L, accountId)).thenReturn(Optional.of(account));
    when(journalEntryRepository.findAll()).thenReturn(Collections.emptyList());

    // Execute
    BigDecimal balance = accountBalanceService.getAccountBalance(accountId);

    // Assert
    assertEquals(new BigDecimal("10000"), balance);
  }

  @Test
  void validateSufficientBalance_sufficientBalance_returnsTrue() {
    // Setup
    Long accountId = 1L;
    BankAccount account = createBankAccount(accountId, new BigDecimal("10000"));
    when(bankAccountRepository.findByCompanyIdAndId(1L, accountId)).thenReturn(Optional.of(account));
    when(journalEntryRepository.findAll()).thenReturn(Collections.emptyList());

    BigDecimal paymentAmount = new BigDecimal("5000");

    // Execute
    boolean result = accountBalanceService.validateSufficientBalance(accountId, paymentAmount);

    // Assert
    assertTrue(result);
  }

  @Test
  void validateSufficientBalance_insufficientBalance_returnsFalse() {
    // Setup
    Long accountId = 1L;
    BankAccount account = createBankAccount(accountId, new BigDecimal("1000"));
    when(bankAccountRepository.findByCompanyIdAndId(1L, accountId)).thenReturn(Optional.of(account));
    when(journalEntryRepository.findAll()).thenReturn(Collections.emptyList());

    BigDecimal paymentAmount = new BigDecimal("5000");

    // Execute
    boolean result = accountBalanceService.validateSufficientBalance(accountId, paymentAmount);

    // Assert
    assertFalse(result);
  }

  @Test
  void checkOverdraft_smallOverdraft_returnsWarning() {
    // Setup
    Long accountId = 1L;
    BankAccount account = createBankAccount(accountId, new BigDecimal("1000"));
    when(bankAccountRepository.findByCompanyIdAndId(1L, accountId)).thenReturn(Optional.of(account));
    when(journalEntryRepository.findAll()).thenReturn(Collections.emptyList());

    BigDecimal paymentAmount = new BigDecimal("1100"); // Small overdraft (100), less than 10% of payment

    // Execute
    AccountBalanceService.OverdraftResult result =
        accountBalanceService.checkOverdraft(accountId, paymentAmount);

    // Assert
    assertEquals(AccountBalanceService.OverdraftStatus.WARNING, result.getStatus());
  }

  @Test
  void checkOverdraft_largeOverdraft_returnsBlock() {
    // Setup
    Long accountId = 1L;
    BankAccount account = createBankAccount(accountId, new BigDecimal("1000"));
    when(bankAccountRepository.findByCompanyIdAndId(1L, accountId)).thenReturn(Optional.of(account));
    when(journalEntryRepository.findAll()).thenReturn(Collections.emptyList());

    BigDecimal paymentAmount = new BigDecimal("5000"); // Large overdraft (4000), more than 10% of payment

    // Execute
    AccountBalanceService.OverdraftResult result =
        accountBalanceService.checkOverdraft(accountId, paymentAmount);

    // Assert
    assertEquals(AccountBalanceService.OverdraftStatus.BLOCK, result.getStatus());
  }

  @Test
  void checkOverdraft_noOverdraft_returnsOk() {
    // Setup
    Long accountId = 1L;
    BankAccount account = createBankAccount(accountId, new BigDecimal("10000"));
    when(bankAccountRepository.findByCompanyIdAndId(1L, accountId)).thenReturn(Optional.of(account));
    when(journalEntryRepository.findAll()).thenReturn(Collections.emptyList());

    BigDecimal paymentAmount = new BigDecimal("5000"); // No overdraft

    // Execute
    AccountBalanceService.OverdraftResult result =
        accountBalanceService.checkOverdraft(accountId, paymentAmount);

    // Assert
    assertEquals(AccountBalanceService.OverdraftStatus.OK, result.getStatus());
  }

  // Helper methods
  private BankAccount createBankAccount(Long id, BigDecimal openingBalance) {
    BankAccount account = new BankAccount();
    account.setId(id);
    account.setCompanyId(1L);
    account.setAccountNumber("ACC-" + id);
    account.setBankName("Test Bank");
    account.setType(BankAccount.AccountType.BANK);
    account.setOpeningBalance(openingBalance);
    account.setActive(true);
    account.setCreatedAt(Instant.now());
    account.setUpdatedAt(Instant.now());
    return account;
  }

  private JournalEntry createJournalEntry(Long accountId, BigDecimal debitAmount, BigDecimal creditAmount) {
    JournalEntry entry = new JournalEntry();
    entry.setId(java.util.UUID.randomUUID());
    entry.setCompanyId(1L);
    entry.setAccountId(accountId);
    entry.setDebitAmount(debitAmount);
    entry.setCreditAmount(creditAmount);
    entry.setVoucherId(java.util.UUID.randomUUID());
    entry.setPostedAt(Instant.now());
    entry.setCreatedAt(Instant.now());
    entry.setUpdatedAt(Instant.now());
    return entry;
  }
}


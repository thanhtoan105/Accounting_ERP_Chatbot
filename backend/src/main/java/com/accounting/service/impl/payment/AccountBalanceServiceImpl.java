package com.accounting.service.impl.payment;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.entity.BankAccount;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.JournalEntryRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountBalanceService;

/**
 * Implementation of AccountBalanceService.
 * Calculates account balance from opening balance and journal entries.
 */
@Service
public class AccountBalanceServiceImpl implements AccountBalanceService {


  private final BankAccountRepository bankAccountRepository;
  private final JournalEntryRepository journalEntryRepository;

  public AccountBalanceServiceImpl(
      BankAccountRepository bankAccountRepository, JournalEntryRepository journalEntryRepository) {
    this.bankAccountRepository = bankAccountRepository;
    this.journalEntryRepository = journalEntryRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal getAccountBalance(Long accountId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Optional<BankAccount> bankAccountOpt =
        bankAccountRepository.findByCompanyIdAndId(companyId, accountId);
    if (bankAccountOpt.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Bank account not found: " + accountId);
    }

    BankAccount bankAccount = bankAccountOpt.get();
    BigDecimal openingBalance = bankAccount.getOpeningBalance();

    // Calculate net journal entries for this account
    // For asset accounts (cash/bank), balance = opening + debit - credit
    BigDecimal netJournalEntries =
        calculateNetJournalEntries(accountId, companyId, bankAccount.getType());

    return openingBalance.add(netJournalEntries);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean validateSufficientBalance(Long accountId, BigDecimal paymentAmount) {
    BigDecimal currentBalance = getAccountBalance(accountId);
    return currentBalance.compareTo(paymentAmount) >= 0;
  }

  @Override
  @Transactional(readOnly = true)
  public OverdraftResult checkOverdraft(Long accountId, BigDecimal paymentAmount) {
    BigDecimal currentBalance = getAccountBalance(accountId);
    BigDecimal projectedBalance = currentBalance.subtract(paymentAmount);

    // Default policy: WARNING if overdraft, BLOCK if overdraft > 10% of payment amount
    // TODO: Read overdraftPolicy from CompanySettings when available
    if (projectedBalance.compareTo(BigDecimal.ZERO) >= 0) {
      return new OverdraftResult(
          OverdraftStatus.OK,
          "Sufficient balance available",
          currentBalance,
          projectedBalance);
    }

    BigDecimal overdraftAmount = projectedBalance.abs();
    BigDecimal threshold = paymentAmount.multiply(new BigDecimal("0.10")); // 10% threshold

    if (overdraftAmount.compareTo(threshold) > 0) {
      return new OverdraftResult(
          OverdraftStatus.BLOCK,
          String.format(
              "Insufficient balance. Current: %s, Required: %s, Shortfall: %s",
              currentBalance, paymentAmount, overdraftAmount),
          currentBalance,
          projectedBalance);
    } else {
      return new OverdraftResult(
          OverdraftStatus.WARNING,
          String.format(
              "Overdraft warning. Current: %s, Required: %s, Shortfall: %s",
              currentBalance, paymentAmount, overdraftAmount),
          currentBalance,
          projectedBalance);
    }
  }

  /**
   * Calculate net journal entries for an account.
   * For asset accounts (CASH/BANK): net = debit - credit
   * For liability accounts: net = credit - debit
   */
  private BigDecimal calculateNetJournalEntries(
      Long accountId, Long companyId, BankAccount.AccountType accountType) {
    // Query journal entries for this account
    // For now, we'll use a simple sum query
    // TODO: Optimize with native query for better performance
    return journalEntryRepository
        .findAll()
        .stream()
        .filter(je -> je.getAccountId().equals(accountId) && je.getCompanyId().equals(companyId))
        .map(
            je -> {
              if (accountType == BankAccount.AccountType.CASH
                  || accountType == BankAccount.AccountType.BANK) {
                // Asset account: debit increases, credit decreases
                return je.getDebitAmount().subtract(je.getCreditAmount());
              } else {
                // Liability account: credit increases, debit decreases
                return je.getCreditAmount().subtract(je.getDebitAmount());
              }
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}

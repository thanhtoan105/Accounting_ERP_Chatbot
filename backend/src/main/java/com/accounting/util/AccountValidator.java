package com.accounting.util;

import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import org.springframework.stereotype.Component;

/**
 * Validator utility for account operations.
 * Used for voucher account picker validation (AC#8).
 */
@Component
public class AccountValidator {

  private final ChartOfAccountsRepository chartOfAccountsRepository;

  public AccountValidator(ChartOfAccountsRepository chartOfAccountsRepository) {
    this.chartOfAccountsRepository = chartOfAccountsRepository;
  }

  /**
   * Validate that account has no children (is a leaf account).
   *
   * @param accountId account ID
   * @return true if account is a leaf (no children), false otherwise
   */
  public boolean validateLeafOnly(Long accountId) {
    return !chartOfAccountsRepository.hasChildren(accountId);
  }

  /**
   * Validate that account has postable=true flag.
   *
   * @param accountId account ID
   * @return true if account is postable, false otherwise
   */
  public boolean validatePostable(Long accountId) {
    return chartOfAccountsRepository
        .findById(accountId)
        .map(ChartOfAccount::getPostable)
        .orElse(false);
  }

  /**
   * Validate account code format: numeric, 1-4 digits.
   *
   * @param code account code
   * @return true if code format is valid, false otherwise
   */
  public boolean validateCodeFormat(String code) {
    if (code == null || code.isEmpty()) {
      return false;
    }
    // Must be numeric and 1-4 digits
    return code.matches("^\\d{1,4}$");
  }

  /**
   * Validate account code hierarchy: child code must start with parent code.
   * Example: "1311" must have parent "131", and "131" must have parent "1".
   *
   * @param code child account code
   * @param parentCode parent account code (can be null for root accounts)
   * @return true if hierarchy is valid, false otherwise
   */
  public boolean validateCodeHierarchy(String code, String parentCode) {
    if (code == null || code.isEmpty()) {
      return false;
    }
    // Root accounts (no parent) are always valid
    if (parentCode == null || parentCode.isEmpty()) {
      return true;
    }
    // Child code must start with parent code
    return code.startsWith(parentCode);
  }

  /**
   * Validate account is both leaf and postable (required for voucher line items).
   *
   * @param accountId account ID
   * @return true if account is valid for posting (leaf and postable), false otherwise
   */
  public boolean validateForPosting(Long accountId) {
    return validateLeafOnly(accountId) && validatePostable(accountId);
  }
}

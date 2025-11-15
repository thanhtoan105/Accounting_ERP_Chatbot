package com.accounting.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountValidatorTest {

  @Mock
  private ChartOfAccountsRepository chartOfAccountsRepository;

  private AccountValidator accountValidator;

  @BeforeEach
  void setUp() {
    accountValidator = new AccountValidator(chartOfAccountsRepository);
  }

  @Test
  void validateCodeFormat_validNumericCode_returnsTrue() {
    assertTrue(accountValidator.validateCodeFormat("1"));
    assertTrue(accountValidator.validateCodeFormat("11"));
    assertTrue(accountValidator.validateCodeFormat("111"));
    assertTrue(accountValidator.validateCodeFormat("1111"));
    assertTrue(accountValidator.validateCodeFormat("131"));
  }

  @Test
  void validateCodeFormat_invalidCode_returnsFalse() {
    assertFalse(accountValidator.validateCodeFormat(null));
    assertFalse(accountValidator.validateCodeFormat(""));
    assertFalse(accountValidator.validateCodeFormat("abc"));
    assertFalse(accountValidator.validateCodeFormat("12345")); // > 4 digits
    assertFalse(accountValidator.validateCodeFormat("12a"));
    assertFalse(accountValidator.validateCodeFormat("1.1"));
  }

  @Test
  void validateCodeHierarchy_validHierarchy_returnsTrue() {
    assertTrue(accountValidator.validateCodeHierarchy("131", null)); // Root account
    assertTrue(accountValidator.validateCodeHierarchy("1311", "131"));
    assertTrue(accountValidator.validateCodeHierarchy("13111", "1311"));
    assertTrue(accountValidator.validateCodeHierarchy("11", "1"));
  }

  @Test
  void validateCodeHierarchy_invalidHierarchy_returnsFalse() {
    assertFalse(accountValidator.validateCodeHierarchy("1311", "132")); // Doesn't start with parent
    assertFalse(accountValidator.validateCodeHierarchy("131", "1311")); // Child code shorter than parent
    assertFalse(accountValidator.validateCodeHierarchy(null, "131"));
    assertFalse(accountValidator.validateCodeHierarchy("", "131"));
  }

  @Test
  void validateLeafOnly_noChildren_returnsTrue() {
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(false);
    assertTrue(accountValidator.validateLeafOnly(1L));
  }

  @Test
  void validateLeafOnly_hasChildren_returnsFalse() {
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(true);
    assertFalse(accountValidator.validateLeafOnly(1L));
  }

  @Test
  void validatePostable_postableAccount_returnsTrue() {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(1L);
    account.setPostable(true);
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));
    assertTrue(accountValidator.validatePostable(1L));
  }

  @Test
  void validatePostable_nonPostableAccount_returnsFalse() {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(1L);
    account.setPostable(false);
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));
    assertFalse(accountValidator.validatePostable(1L));
  }

  @Test
  void validatePostable_accountNotFound_returnsFalse() {
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.empty());
    assertFalse(accountValidator.validatePostable(1L));
  }

  @Test
  void validateForPosting_leafAndPostable_returnsTrue() {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(1L);
    account.setPostable(true);
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(false);
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));
    assertTrue(accountValidator.validateForPosting(1L));
  }

  @Test
  void validateForPosting_notLeaf_returnsFalse() {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(1L);
    account.setPostable(true);
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(true);
    assertFalse(accountValidator.validateForPosting(1L));
  }

  @Test
  void validateForPosting_notPostable_returnsFalse() {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(1L);
    account.setPostable(false);
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(false);
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));
    assertFalse(accountValidator.validateForPosting(1L));
  }
}

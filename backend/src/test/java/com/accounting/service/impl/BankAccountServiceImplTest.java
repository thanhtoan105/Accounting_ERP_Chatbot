package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.accounting.dto.BalanceTooltipDTO;
import com.accounting.dto.BankAccountCreateRequest;
import com.accounting.dto.BankAccountDTO;
import com.accounting.dto.BankAccountUpdateRequest;
import com.accounting.entity.BankAccount;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceImplTest {

  @Mock
  private BankAccountRepository bankAccountRepository;

  @Mock
  private ChartOfAccountsRepository chartOfAccountsRepository;

  @Mock
  private AuditService auditService;

  private BankAccountServiceImpl bankAccountService;

  private static final Long TEST_COMPANY_ID = 1L;
  private static final Long TEST_BANK_ACCOUNT_ID = 100L;

  @BeforeEach
  void setUp() {
    bankAccountService = new BankAccountServiceImpl(bankAccountRepository, chartOfAccountsRepository, auditService);
    CompanyContext.setCompanyId(TEST_COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void findAll_withPagination_returnsPage() {
    Pageable pageable = PageRequest.of(0, 20);
    List<BankAccount> bankAccounts = List.of(createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Test Bank"));
    Page<BankAccount> bankAccountPage = new PageImpl<>(bankAccounts, pageable, 1);

    when(bankAccountRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(bankAccountPage);

    Page<BankAccountDTO> result = bankAccountService.findAll(pageable, null, null, null);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals(1, result.getContent().size());
    assertEquals("Test Bank", result.getContent().get(0).getBankName());
  }

  @Test
  void findAll_withTypeFilter_filtersByType() {
    Pageable pageable = PageRequest.of(0, 20);
    BankAccount cashAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Cash Account");
    cashAccount.setType(BankAccount.AccountType.CASH);
    List<BankAccount> bankAccounts = List.of(cashAccount);
    Page<BankAccount> bankAccountPage = new PageImpl<>(bankAccounts, pageable, 1);

    when(bankAccountRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(bankAccountPage);

    Page<BankAccountDTO> result = bankAccountService.findAll(pageable, "CASH", null, null);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals(BankAccount.AccountType.CASH, result.getContent().get(0).getType());
  }

  @Test
  void findAll_withStatusFilter_filtersByStatus() {
    Pageable pageable = PageRequest.of(0, 20);
    BankAccount activeAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Active Bank");
    activeAccount.setActive(true);
    List<BankAccount> bankAccounts = List.of(activeAccount);
    Page<BankAccount> bankAccountPage = new PageImpl<>(bankAccounts, pageable, 1);

    when(bankAccountRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(bankAccountPage);

    Page<BankAccountDTO> result = bankAccountService.findAll(pageable, null, true, null);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertTrue(result.getContent().get(0).getActive());
  }

  @Test
  void findAll_withSearchTerm_usesNativeSearch() {
    Pageable pageable = PageRequest.of(0, 20);
    BankAccount matchingAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Matching Bank");
    List<BankAccount> bankAccounts = List.of(matchingAccount);

    when(bankAccountRepository.searchByAccountNumberOrBankNameNative(TEST_COMPANY_ID, "Matching"))
        .thenReturn(bankAccounts);

    Page<BankAccountDTO> result = bankAccountService.findAll(pageable, null, null, "Matching");

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals("Matching Bank", result.getContent().get(0).getBankName());
  }

  @Test
  void findAll_missingCompanyContext_throwsException() {
    CompanyContext.clear();
    Pageable pageable = PageRequest.of(0, 20);

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> bankAccountService.findAll(pageable, null, null, null));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Missing company context"));
  }

  @Test
  void getBankAccountById_accountFound_returnsDTO() {
    BankAccount bankAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Test Bank");
    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.of(bankAccount));

    Optional<BankAccountDTO> result = bankAccountService.getBankAccountById(TEST_BANK_ACCOUNT_ID);

    assertTrue(result.isPresent());
    assertEquals("Test Bank", result.get().getBankName());
    assertEquals("ACC-001", result.get().getAccountNumber());
  }

  @Test
  void getBankAccountById_accountNotFound_returnsEmpty() {
    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.empty());

    Optional<BankAccountDTO> result = bankAccountService.getBankAccountById(TEST_BANK_ACCOUNT_ID);

    assertFalse(result.isPresent());
  }

  @Test
  void create_withValidData_createsAccount() {
    BankAccountCreateRequest request = new BankAccountCreateRequest();
    request.setAccountNumber("ACC-001");
    request.setBankName("Test Bank");
    request.setType(BankAccount.AccountType.BANK);
    request.setOpeningBalance(BigDecimal.valueOf(1000.00));
    request.setActive(true);

    BankAccount savedAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Test Bank");

    when(bankAccountRepository.existsByCompanyIdAndAccountNumber(TEST_COMPANY_ID, "ACC-001", null))
        .thenReturn(false);
    when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(savedAccount);

    BankAccountDTO result = bankAccountService.create(request);

    assertNotNull(result);
    assertEquals("Test Bank", result.getBankName());
    assertEquals("ACC-001", result.getAccountNumber());
    verify(auditService).logBankAccountCreated(eq(TEST_BANK_ACCOUNT_ID), eq("ACC-001"), any(), any(), any());
  }

  @Test
  void create_withDuplicateAccountNumber_throwsConflict() {
    BankAccountCreateRequest request = new BankAccountCreateRequest();
    request.setAccountNumber("ACC-001");
    request.setBankName("Test Bank");
    request.setType(BankAccount.AccountType.BANK);
    request.setOpeningBalance(BigDecimal.valueOf(1000.00));

    when(bankAccountRepository.existsByCompanyIdAndAccountNumber(TEST_COMPANY_ID, "ACC-001", null))
        .thenReturn(true);

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> bankAccountService.create(request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("already exists"));
    verify(bankAccountRepository, never()).save(any(BankAccount.class));
  }

  @Test
  void update_withValidData_updatesAccount() {
    BankAccount existingAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Old Bank Name");
    BankAccount updatedAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "New Bank Name");

    BankAccountUpdateRequest request = new BankAccountUpdateRequest();
    request.setBankName("New Bank Name");

    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.of(existingAccount));
    when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(updatedAccount);

    BankAccountDTO result = bankAccountService.update(TEST_BANK_ACCOUNT_ID, request);

    assertNotNull(result);
    assertEquals("New Bank Name", result.getBankName());
    verify(auditService).logBankAccountUpdated(eq(TEST_BANK_ACCOUNT_ID), eq("ACC-001"), any(), any(), any(), any(),
        any());
  }

  @Test
  void update_accountNotFound_throwsNotFoundException() {
    BankAccountUpdateRequest request = new BankAccountUpdateRequest();
    request.setBankName("New Bank Name");

    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.empty());

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> bankAccountService.update(TEST_BANK_ACCOUNT_ID, request));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("not found"));
  }

  @Test
  void delete_withPostedReferences_throwsConflict() {
    BankAccount bankAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Test Bank");
    bankAccount.setGlAccountCode("1121");

    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.of(bankAccount));
    when(bankAccountRepository.countPostedReferencesByGlAccountCode(TEST_COMPANY_ID, "1121"))
        .thenReturn(5L);
    when(bankAccountRepository.findPostedTransactionExamples(TEST_COMPANY_ID, "1121", 5))
        .thenReturn(List.of("VCH-001", "VCH-002"));

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> bankAccountService.delete(TEST_BANK_ACCOUNT_ID));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    verify(bankAccountRepository, never()).delete(any(BankAccount.class));
    verify(auditService).logBankAccountDeleted(eq(TEST_BANK_ACCOUNT_ID), eq("ACC-001"), any(), any(), any());
  }

  @Test
  void delete_withNoReferences_deletesSuccessfully() {
    BankAccount bankAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Test Bank");
    bankAccount.setGlAccountCode("1121");

    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.of(bankAccount));
    when(bankAccountRepository.countPostedReferencesByGlAccountCode(TEST_COMPANY_ID, "1121"))
        .thenReturn(0L);

    bankAccountService.delete(TEST_BANK_ACCOUNT_ID);

    verify(bankAccountRepository).delete(bankAccount);
    verify(auditService).logBankAccountDeleted(eq(TEST_BANK_ACCOUNT_ID), eq("ACC-001"), any(), any(), any());
  }

  @Test
  void delete_accountNotFound_throwsNotFoundException() {
    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.empty());

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> bankAccountService.delete(TEST_BANK_ACCOUNT_ID));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    verify(bankAccountRepository, never()).delete(any(BankAccount.class));
  }

  @Test
  void activate_setsActiveToTrue() {
    BankAccount bankAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Test Bank");
    bankAccount.setActive(false);

    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.of(bankAccount));
    when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(bankAccount);

    bankAccountService.activate(TEST_BANK_ACCOUNT_ID);

    verify(bankAccountRepository).save(bankAccount);
    assertTrue(bankAccount.getActive());
    verify(auditService).logBankAccountActivated(eq(TEST_BANK_ACCOUNT_ID), eq("ACC-001"), any(), any());
  }

  @Test
  void deactivate_setsActiveToFalse() {
    BankAccount bankAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Test Bank");
    bankAccount.setActive(true);

    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.of(bankAccount));
    when(bankAccountRepository.save(any(BankAccount.class))).thenReturn(bankAccount);

    bankAccountService.deactivate(TEST_BANK_ACCOUNT_ID);

    verify(bankAccountRepository).save(bankAccount);
    assertFalse(bankAccount.getActive());
    verify(auditService).logBankAccountDeactivated(eq(TEST_BANK_ACCOUNT_ID), eq("ACC-001"), any(), any());
  }

  @Test
  void getBalanceTooltip_returnsTooltipData() {
    BankAccount bankAccount = createBankAccount(TEST_BANK_ACCOUNT_ID, "ACC-001", "Test Bank");
    bankAccount.setOpeningBalance(BigDecimal.valueOf(5000.00));

    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.of(bankAccount));

    BalanceTooltipDTO result = bankAccountService.getBalanceTooltip(TEST_BANK_ACCOUNT_ID);

    assertNotNull(result);
    assertEquals(BigDecimal.valueOf(5000.00), result.getCurrentBalance());
    // lastTxDate and lastReconciledDate may be null for MVP placeholder
    // implementation
  }

  @Test
  void getBalanceTooltip_accountNotFound_throwsNotFoundException() {
    when(bankAccountRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_BANK_ACCOUNT_ID))
        .thenReturn(Optional.empty());

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> bankAccountService.getBalanceTooltip(TEST_BANK_ACCOUNT_ID));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
  }

  private BankAccount createBankAccount(Long id, String accountNumber, String bankName) {
    BankAccount bankAccount = new BankAccount();
    bankAccount.setId(id);
    bankAccount.setCompanyId(TEST_COMPANY_ID);
    bankAccount.setAccountNumber(accountNumber);
    bankAccount.setBankName(bankName);
    bankAccount.setType(BankAccount.AccountType.BANK);
    bankAccount.setOpeningBalance(BigDecimal.valueOf(1000.00));
    bankAccount.setActive(true);
    bankAccount.setCreatedAt(Instant.now());
    bankAccount.setUpdatedAt(Instant.now());
    return bankAccount;
  }
}

package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.entity.AccountControl;
import com.accounting.repository.AccountControlRepository;
import com.accounting.security.CompanyContext;

@ExtendWith(MockitoExtension.class)
class AccountControlServiceImplTest {

  @Mock
  private AccountControlRepository accountControlRepository;

  @InjectMocks
  private AccountControlServiceImpl accountControlService;

  private Long testCompanyId;
  private Long otherCompanyId;
  private UUID testAccountControlId;
  private Long testAccountId;

  @BeforeEach
  void setUp() {
    testCompanyId = 1L;
    otherCompanyId = 2L;
    testAccountControlId = UUID.randomUUID();
    testAccountId = 100L;
    CompanyContext.setCompanyId(testCompanyId);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void getAccountControlById_whenExists_returnsAccountControl() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    when(accountControlRepository.findById(testAccountControlId))
        .thenReturn(Optional.of(accountControl));

    Optional<AccountControl> result = accountControlService.getAccountControlById(testAccountControlId);

    assertTrue(result.isPresent());
    assertEquals(testAccountControlId, result.get().getId());
    assertEquals(testAccountId, result.get().getAccountId());
    assertEquals(testCompanyId, result.get().getCompanyId());
  }

  @Test
  void getAccountControlById_whenDifferentCompany_returnsEmpty() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, otherCompanyId);
    when(accountControlRepository.findById(testAccountControlId))
        .thenReturn(Optional.of(accountControl));

    Optional<AccountControl> result = accountControlService.getAccountControlById(testAccountControlId);

    assertFalse(result.isPresent());
  }

  @Test
  void getAccountControlById_whenNotExists_returnsEmpty() {
    when(accountControlRepository.findById(testAccountControlId))
        .thenReturn(Optional.empty());

    Optional<AccountControl> result = accountControlService.getAccountControlById(testAccountControlId);

    assertFalse(result.isPresent());
  }

  @Test
  void getAccountControlById_whenNoCompanyContext_throwsException() {
    CompanyContext.clear();
    
    assertThrows(ResponseStatusException.class, () -> {
      accountControlService.getAccountControlById(testAccountControlId);
    });
  }

  @Test
  void getAccountControlByAccountId_whenExists_returnsAccountControl() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    when(accountControlRepository.findByAccountIdAndCompanyId(testAccountId, testCompanyId))
        .thenReturn(Optional.of(accountControl));

    Optional<AccountControl> result = accountControlService.getAccountControlByAccountId(testAccountId, null);

    assertTrue(result.isPresent());
    assertEquals(testAccountControlId, result.get().getId());
  }

  @Test
  void getAccountControlByAccountId_whenExplicitCompanyId_usesProvidedId() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, otherCompanyId);
    when(accountControlRepository.findByAccountIdAndCompanyId(testAccountId, otherCompanyId))
        .thenReturn(Optional.of(accountControl));

    Optional<AccountControl> result = accountControlService.getAccountControlByAccountId(testAccountId, otherCompanyId);

    assertTrue(result.isPresent());
    assertEquals(otherCompanyId, result.get().getCompanyId());
  }

  @Test
  void getAccountControlByAccountId_whenNotExists_returnsEmpty() {
    when(accountControlRepository.findByAccountIdAndCompanyId(testAccountId, testCompanyId))
        .thenReturn(Optional.empty());

    Optional<AccountControl> result = accountControlService.getAccountControlByAccountId(testAccountId, null);

    assertFalse(result.isPresent());
  }

  @Test
  void getAllAccountControls_returnsAllForCurrentCompany() {
    List<AccountControl> accountControls = new ArrayList<>();
    accountControls.add(createAccountControl(UUID.randomUUID(), 100L, testCompanyId));
    accountControls.add(createAccountControl(UUID.randomUUID(), 200L, testCompanyId));
    
    when(accountControlRepository.findByCompanyId(testCompanyId))
        .thenReturn(accountControls);

    List<AccountControl> result = accountControlService.getAllAccountControls();

    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(ac -> ac.getCompanyId().equals(testCompanyId)));
  }

  @Test
  void getAllAccountControls_whenNoCompanyContext_throwsException() {
    CompanyContext.clear();
    
    assertThrows(ResponseStatusException.class, () -> {
      accountControlService.getAllAccountControls();
    });
  }

  @Test
  void getRequiredDimensions_whenExists_returnsAccountControl() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    accountControl.setRequiresCustomer(true);
    accountControl.setRequiresSupplier(true);
    
    when(accountControlRepository.findByAccountIdAndCompanyId(testAccountId, testCompanyId))
        .thenReturn(Optional.of(accountControl));

    Optional<AccountControl> result = accountControlService.getRequiredDimensions(testAccountId, null);

    assertTrue(result.isPresent());
    assertTrue(result.get().getRequiresCustomer());
    assertTrue(result.get().getRequiresSupplier());
  }

  @Test
  void getRequiredDimensions_whenNotExists_returnsEmpty() {
    when(accountControlRepository.findByAccountIdAndCompanyId(testAccountId, testCompanyId))
        .thenReturn(Optional.empty());

    Optional<AccountControl> result = accountControlService.getRequiredDimensions(testAccountId, null);

    assertFalse(result.isPresent());
  }

  @Test
  void getRequiredDimensions_whenNoCompanyContext_returnsEmpty() {
    CompanyContext.clear();
    
    Optional<AccountControl> result = accountControlService.getRequiredDimensions(testAccountId, null);
    
    assertFalse(result.isPresent());
  }

  @Test
  void saveAccountControl_setsCompanyIdAndTimestamps() {
    AccountControl accountControl = new AccountControl();
    accountControl.setAccountId(testAccountId);
    accountControl.setRequiresCustomer(true);
    
    AccountControl saved = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    saved.setRequiresCustomer(true);
    
    when(accountControlRepository.save(any(AccountControl.class)))
        .thenReturn(saved);

    AccountControl result = accountControlService.saveAccountControl(accountControl);

    assertNotNull(result);
    assertEquals(testCompanyId, accountControl.getCompanyId());
    assertNotNull(accountControl.getCreatedAt());
    assertNotNull(accountControl.getUpdatedAt());
    verify(accountControlRepository).save(accountControl);
  }

  @Test
  void saveAccountControl_whenNoCompanyContext_throwsException() {
    CompanyContext.clear();
    AccountControl accountControl = new AccountControl();
    accountControl.setAccountId(testAccountId);
    
    assertThrows(ResponseStatusException.class, () -> {
      accountControlService.saveAccountControl(accountControl);
    });
    
    verify(accountControlRepository, never()).save(any());
  }

  @Test
  void deleteAccountControl_whenExists_deletesAccountControl() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    when(accountControlRepository.findById(testAccountControlId))
        .thenReturn(Optional.of(accountControl));

    accountControlService.deleteAccountControl(testAccountControlId);

    verify(accountControlRepository).deleteById(testAccountControlId);
  }

  @Test
  void deleteAccountControl_whenNotExists_throwsException() {
    when(accountControlRepository.findById(testAccountControlId))
        .thenReturn(Optional.empty());

    assertThrows(ResponseStatusException.class, () -> {
      accountControlService.deleteAccountControl(testAccountControlId);
    });
    
    verify(accountControlRepository, never()).deleteById(any());
  }

  @Test
  void deleteAccountControl_whenDifferentCompany_throwsException() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, otherCompanyId);
    when(accountControlRepository.findById(testAccountControlId))
        .thenReturn(Optional.of(accountControl));

    assertThrows(ResponseStatusException.class, () -> {
      accountControlService.deleteAccountControl(testAccountControlId);
    });
    
    verify(accountControlRepository, never()).deleteById(any());
  }

  @Test
  void deleteAccountControl_whenNoCompanyContext_throwsException() {
    CompanyContext.clear();
    
    assertThrows(ResponseStatusException.class, () -> {
      accountControlService.deleteAccountControl(testAccountControlId);
    });
  }

  @Test
  void validateRequiredDimensions_whenAllRequiredAndProvided_returnsNoErrors() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    accountControl.setRequiresCustomer(true);
    accountControl.setRequiresSupplier(true);
    accountControl.setRequiresCostCenter(true);
    accountControl.setRequiresItem(true);

    List<String> errors = accountControlService.validateRequiredDimensions(
        accountControl, 1L, 2L, 3L, 4L);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validateRequiredDimensions_whenCustomerRequiredButMissing_returnsError() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    accountControl.setRequiresCustomer(true);

    List<String> errors = accountControlService.validateRequiredDimensions(
        accountControl, null, 2L, 3L, 4L);

    assertEquals(1, errors.size());
    assertTrue(errors.get(0).contains("Customer is required"));
  }

  @Test
  void validateRequiredDimensions_whenSupplierRequiredButMissing_returnsError() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    accountControl.setRequiresSupplier(true);

    List<String> errors = accountControlService.validateRequiredDimensions(
        accountControl, 1L, null, 3L, 4L);

    assertEquals(1, errors.size());
    assertTrue(errors.get(0).contains("Supplier is required"));
  }

  @Test
  void validateRequiredDimensions_whenCostCenterRequiredButMissing_returnsError() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    accountControl.setRequiresCostCenter(true);

    List<String> errors = accountControlService.validateRequiredDimensions(
        accountControl, 1L, 2L, null, 4L);

    assertEquals(1, errors.size());
    assertTrue(errors.get(0).contains("Cost Center is required"));
  }

  @Test
  void validateRequiredDimensions_whenItemRequiredButMissing_returnsError() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    accountControl.setRequiresItem(true);

    List<String> errors = accountControlService.validateRequiredDimensions(
        accountControl, 1L, 2L, 3L, null);

    assertEquals(1, errors.size());
    assertTrue(errors.get(0).contains("Item is required"));
  }

  @Test
  void validateRequiredDimensions_whenMultipleRequiredButMissing_returnsMultipleErrors() {
    AccountControl accountControl = createAccountControl(testAccountControlId, testAccountId, testCompanyId);
    accountControl.setRequiresCustomer(true);
    accountControl.setRequiresSupplier(true);
    accountControl.setRequiresCostCenter(true);

    List<String> errors = accountControlService.validateRequiredDimensions(
        accountControl, null, null, null, 4L);

    assertEquals(3, errors.size());
  }

  @Test
  void validateRequiredDimensions_whenNullAccountControl_returnsNoErrors() {
    List<String> errors = accountControlService.validateRequiredDimensions(
        null, 1L, 2L, 3L, 4L);

    assertTrue(errors.isEmpty());
  }

  private AccountControl createAccountControl(UUID id, Long accountId, Long companyId) {
    AccountControl accountControl = new AccountControl();
    accountControl.setId(id);
    accountControl.setAccountId(accountId);
    accountControl.setCompanyId(companyId);
    accountControl.setRequiresCustomer(false);
    accountControl.setRequiresSupplier(false);
    accountControl.setRequiresCostCenter(false);
    accountControl.setRequiresItem(false);
    accountControl.setCreatedAt(Instant.now());
    accountControl.setUpdatedAt(Instant.now());
    return accountControl;
  }
}

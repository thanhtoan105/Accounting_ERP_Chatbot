package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.security.CompanyContext;

@ExtendWith(MockitoExtension.class)
class ChartOfAccountsServiceImplTest {

  @Mock private ChartOfAccountsRepository chartOfAccountsRepository;

  private ChartOfAccountsServiceImpl chartOfAccountsService;

  @BeforeEach
  void setUp() {
    chartOfAccountsService = new ChartOfAccountsServiceImpl(chartOfAccountsRepository);
    CompanyContext.setCompanyId(1L);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void buildHierarchy_returnsTreeStructure() {
    // Setup: Create hierarchy: 1 -> 11 -> 111
    ChartOfAccount root = createAccount(1L, "1", "Tài sản", null);
    ChartOfAccount child = createAccount(2L, "11", "Tài sản ngắn hạn", 1L);
    ChartOfAccount grandchild = createAccount(3L, "111", "Tiền mặt", 2L);

    List<ChartOfAccount> allAccounts = List.of(root, child, grandchild);
    when(chartOfAccountsRepository.findByCompanyId(1L)).thenReturn(allAccounts);

    var hierarchy = chartOfAccountsService.buildHierarchy();

    assertNotNull(hierarchy);
    assertEquals(1, hierarchy.size());
    assertEquals("1", hierarchy.get(0).getCode());
    assertEquals(1, hierarchy.get(0).getChildren().size());
    assertEquals("11", hierarchy.get(0).getChildren().get(0).getCode());
    assertEquals(1, hierarchy.get(0).getChildren().get(0).getChildren().size());
    assertEquals("111", hierarchy.get(0).getChildren().get(0).getChildren().get(0).getCode());
  }

  @Test
  void buildHierarchy_missingCompanyContext_throwsException() {
    CompanyContext.clear();

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> chartOfAccountsService.buildHierarchy());

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason().contains("Missing company context"));
  }

  @Test
  void findAll_withFilters_appliesFilters() {
    ChartOfAccount account1 = createAccount(1L, "1111", "Tiền Việt Nam", null);
    account1.setPostable(true);
    ChartOfAccount account2 = createAccount(2L, "1311", "Phải thu khách hàng", null);
    account2.setPostable(true);

    List<ChartOfAccount> filteredAccounts = List.of(account1);
    when(chartOfAccountsRepository.findAll(any(Specification.class))).thenReturn(filteredAccounts);

    var result = chartOfAccountsService.findAll(true, null, null, null, null, null);

    assertEquals(1, result.size());
    assertEquals("1111", result.get(0).getCode());
  }

  @Test
  void findPostableLeafAccounts_returnsOnlyPostableLeaves() {
    ChartOfAccount postable1 = createAccount(1L, "1111", "Tiền Việt Nam", null);
    postable1.setPostable(true);
    ChartOfAccount postable2 = createAccount(2L, "1311", "Phải thu", null);
    postable2.setPostable(true);
    ChartOfAccount nonPostable = createAccount(3L, "111", "Tiền", null);
    nonPostable.setPostable(false);

    List<ChartOfAccount> postableAccounts = List.of(postable1, postable2, nonPostable);
    when(chartOfAccountsRepository.findByCompanyIdAndPostableTrue(1L)).thenReturn(postableAccounts);
    when(chartOfAccountsRepository.hasChildren(1L)).thenReturn(false);
    when(chartOfAccountsRepository.hasChildren(2L)).thenReturn(false);
    when(chartOfAccountsRepository.hasChildren(3L)).thenReturn(true);

    var result = chartOfAccountsService.findPostableLeafAccounts();

    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(acc -> acc.getPostable()));
  }

  @Test
  void getAccountById_accountFound_returnsDTO() {
    ChartOfAccount account = createAccount(1L, "1111", "Tiền Việt Nam", null);
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    var result = chartOfAccountsService.getAccountById(1L);

    assertTrue(result.isPresent());
    assertEquals("1111", result.get().getCode());
    assertEquals("Tiền Việt Nam", result.get().getName());
  }

  @Test
  void getAccountById_accountNotFound_returnsEmpty() {
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.empty());

    var result = chartOfAccountsService.getAccountById(1L);

    assertFalse(result.isPresent());
  }

  @Test
  void getAccountById_wrongCompany_returnsEmpty() {
    ChartOfAccount account = createAccount(1L, "1111", "Tiền Việt Nam", null);
    account.setCompanyId(2L); // Different company
    when(chartOfAccountsRepository.findById(1L)).thenReturn(Optional.of(account));

    var result = chartOfAccountsService.getAccountById(1L);

    assertFalse(result.isPresent());
  }

  private ChartOfAccount createAccount(Long id, String code, String name, Long parentId) {
    ChartOfAccount account = new ChartOfAccount();
    account.setId(id);
    account.setCompanyId(1L);
    account.setCode(code);
    account.setName(name);
    account.setType("Asset");
    account.setNormalSide("Debit");
    account.setPostable(false);
    account.setParentId(parentId);
    account.setOrderingPosition(Integer.parseInt(code));
    return account;
  }
}

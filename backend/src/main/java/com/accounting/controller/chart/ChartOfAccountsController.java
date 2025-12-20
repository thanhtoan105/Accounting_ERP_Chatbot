package com.accounting.controller.chart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.ChartOfAccountCreateRequest;
import com.accounting.dto.ChartOfAccountDTO;
import com.accounting.dto.ChartOfAccountHierarchyDTO;
import com.accounting.dto.ChartOfAccountUpdateRequest;
import com.accounting.service.ChartOfAccountsService;

import jakarta.validation.Valid;

/**
 * REST controller for Chart of Accounts operations.
 * All authenticated users can view COA; edit operations are blocked in MVP.
 */
@RestController
@RequestMapping("/api/v1/chart-of-accounts")
public class ChartOfAccountsController {

  private final ChartOfAccountsService chartOfAccountsService;

  public ChartOfAccountsController(ChartOfAccountsService chartOfAccountsService) {
    this.chartOfAccountsService = chartOfAccountsService;
  }

  /**
   * Get Chart of Accounts in hierarchical structure (tree format).
   * Supports query params for filtering.
   * Requires authenticated user (any role).
   *
   * @param postable filter by postable flag (optional)
   * @param codePrefix filter by code prefix (optional, e.g., "131")
   * @param parentId filter by parent ID (optional, for cascading selectors)
   * @param type filter by account type (optional: Asset, Liability, Equity, Revenue, Expense)
   * @param search search term for code or Vietnamese name (optional, supports unaccented matching)
   * @param active filter by active status (optional: true = In Use, false = Out of Use)
   * @return hierarchical COA structure
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getChartOfAccounts(
      @RequestParam(required = false) Boolean postable,
      @RequestParam(required = false) String codePrefix,
      @RequestParam(required = false) Long parentId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active) {

    // If any filter is provided, use flat list; otherwise return hierarchy
    if (postable != null || codePrefix != null || parentId != null || type != null || search != null || active != null) {
      List<ChartOfAccountDTO> accounts =
          chartOfAccountsService.findAll(postable, codePrefix, parentId, type, search, active);

      Map<String, Object> body = new HashMap<>();
      body.put("data", accounts);
      body.put("total", accounts.size());
      return ResponseEntity.ok(body);
    } else {
      // Return hierarchical structure
      List<ChartOfAccountHierarchyDTO> hierarchy = chartOfAccountsService.buildHierarchy();

      Map<String, Object> body = new HashMap<>();
      body.put("data", hierarchy);
      body.put("total", countAccounts(hierarchy));
      return ResponseEntity.ok(body);
    }
  }

  /**
   * Get single account by ID.
   * Requires authenticated user (any role).
   *
   * @param id account ID
   * @return account details
   */
  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getAccountById(@PathVariable Long id) {
    return chartOfAccountsService
        .getAccountById(id)
        .map(
            account -> {
              Map<String, Object> body = new HashMap<>();
              body.put("data", account);
              return ResponseEntity.ok(body);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Get postable leaf accounts (for voucher account picker).
   * Requires authenticated user (any role).
   *
   * @return list of postable leaf accounts
   */
  @GetMapping("/postable")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getPostableAccounts() {
    List<ChartOfAccountDTO> accounts = chartOfAccountsService.findPostableLeafAccounts();

    Map<String, Object> body = new HashMap<>();
    body.put("data", accounts);
    body.put("total", accounts.size());
    return ResponseEntity.ok(body);
  }

  /**
   * Create a new account.
   * Requires admin or chief_accountant role.
   *
   * @param request create request with account details
   * @return created account
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> createAccount(
      @Valid @RequestBody ChartOfAccountCreateRequest request) {
    ChartOfAccountDTO account = chartOfAccountsService.createAccount(request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", account);
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * Update an existing account.
   * Requires admin or chief_accountant role.
   *
   * @param id account ID
   * @param request update request with account details
   * @return updated account
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> updateAccount(
      @PathVariable Long id, @Valid @RequestBody ChartOfAccountUpdateRequest request) {
    ChartOfAccountDTO account = chartOfAccountsService.updateAccount(id, request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", account);
    return ResponseEntity.ok(body);
  }

  /**
   * Soft delete an account (set active=false).
   * Requires admin or chief_accountant role.
   *
   * @param id account ID
   * @return no content
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
    chartOfAccountsService.softDeleteAccount(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Activate an account (set active=true).
   * Requires admin or chief_accountant role.
   *
   * @param id account ID
   * @return no content
   */
  @PatchMapping("/{id}/activate")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Void> activateAccount(@PathVariable Long id) {
    chartOfAccountsService.activateAccount(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deactivate an account (set active=false).
   * Requires admin or chief_accountant role.
   *
   * @param id account ID
   * @return no content
   */
  @PatchMapping("/{id}/deactivate")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Void> deactivateAccount(@PathVariable Long id) {
    chartOfAccountsService.deactivateAccount(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Recursively count total accounts in hierarchy.
   */
  private int countAccounts(List<ChartOfAccountHierarchyDTO> hierarchy) {
    int count = hierarchy.size();
    for (ChartOfAccountHierarchyDTO node : hierarchy) {
      if (node.getChildren() != null && !node.getChildren().isEmpty()) {
        count += countAccounts(node.getChildren());
      }
    }
    return count;
  }
}

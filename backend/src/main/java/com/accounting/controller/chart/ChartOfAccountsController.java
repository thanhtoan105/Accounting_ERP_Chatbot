package com.accounting.controller.chart;

import com.accounting.dto.ChartOfAccountDTO;
import com.accounting.dto.ChartOfAccountHierarchyDTO;
import com.accounting.service.ChartOfAccountsService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
   * @return hierarchical COA structure
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getChartOfAccounts(
      @RequestParam(required = false) Boolean postable,
      @RequestParam(required = false) String codePrefix,
      @RequestParam(required = false) Long parentId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String search) {

    // If any filter is provided, use flat list; otherwise return hierarchy
    if (postable != null || codePrefix != null || parentId != null || type != null || search != null) {
      List<ChartOfAccountDTO> accounts =
          chartOfAccountsService.findAll(postable, codePrefix, parentId, type, search);

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

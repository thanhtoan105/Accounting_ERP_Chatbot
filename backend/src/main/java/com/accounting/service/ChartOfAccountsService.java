package com.accounting.service;

import com.accounting.dto.ChartOfAccountDTO;
import com.accounting.dto.ChartOfAccountHierarchyDTO;
import java.util.List;
import java.util.Optional;

/**
 * Service for Chart of Accounts operations including hierarchy building and filtering.
 */
public interface ChartOfAccountsService {

  /**
   * Build hierarchical structure of accounts (tree format).
   * Groups accounts by parent_id and returns nested structure.
   *
   * @return list of root accounts with nested children
   */
  List<ChartOfAccountHierarchyDTO> buildHierarchy();

  /**
   * Find all accounts with optional filters.
   * Company-scoped - only returns accounts in the current company.
   *
   * @param postable filter by postable flag (optional, null to ignore)
   * @param codePrefix filter by code prefix (optional, null to ignore)
   * @param parentId filter by parent ID (optional, null for root accounts)
   * @param type filter by account type (optional, null to ignore)
   * @param search search term for code or name (optional, null to ignore)
   * @return list of accounts matching filters
   */
  List<ChartOfAccountDTO> findAll(Boolean postable, String codePrefix, Long parentId, String type, String search);

  /**
   * Find only postable leaf accounts (accounts with postable=true and no children).
   * Used by voucher account picker.
   *
   * @return list of postable leaf accounts
   */
  List<ChartOfAccountDTO> findPostableLeafAccounts();

  /**
   * Get account by ID (must be in same company).
   *
   * @param accountId account ID
   * @return account or empty if not found
   */
  Optional<ChartOfAccountDTO> getAccountById(Long accountId);

  /**
   * Search accounts by code or name with unaccented Vietnamese support.
   *
   * @param searchTerm search term (matches code or Vietnamese name)
   * @return list of matching accounts
   */
  List<ChartOfAccountDTO> search(String searchTerm);
}

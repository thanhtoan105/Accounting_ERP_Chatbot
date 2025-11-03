package com.accounting.service.impl;

import com.accounting.dto.ChartOfAccountDTO;
import com.accounting.dto.ChartOfAccountHierarchyDTO;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ChartOfAccountsService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of ChartOfAccountsService for COA operations.
 */
@Service
@Transactional
public class ChartOfAccountsServiceImpl implements ChartOfAccountsService {

  private final ChartOfAccountsRepository chartOfAccountsRepository;

  public ChartOfAccountsServiceImpl(ChartOfAccountsRepository chartOfAccountsRepository) {
    this.chartOfAccountsRepository = chartOfAccountsRepository;
  }

  @Override
  public List<ChartOfAccountHierarchyDTO> buildHierarchy() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Fetch all accounts for company
    List<ChartOfAccount> allAccounts = chartOfAccountsRepository.findByCompanyId(companyId);

    // Group accounts by parent ID
    Map<Long, List<ChartOfAccount>> accountsByParent = new HashMap<>();
    List<ChartOfAccountHierarchyDTO> rootAccounts = new ArrayList<>();

    for (ChartOfAccount account : allAccounts) {
      Long parentId = account.getParentId();
      if (parentId == null) {
        // Root account
        rootAccounts.add(toHierarchyDTO(account));
      } else {
        accountsByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(account);
      }
    }

    // Sort root accounts by ordering position
    rootAccounts.sort(Comparator.comparing(ChartOfAccountHierarchyDTO::getOrderingPosition));

    // Build nested structure recursively
    for (ChartOfAccountHierarchyDTO root : rootAccounts) {
      buildChildren(root, accountsByParent);
    }

    return rootAccounts;
  }

  /**
   * Recursively build children for a node.
   */
  private void buildChildren(
      ChartOfAccountHierarchyDTO node, Map<Long, List<ChartOfAccount>> accountsByParent) {
    List<ChartOfAccount> children = accountsByParent.get(node.getId());
    if (children == null || children.isEmpty()) {
      return;
    }

    // Sort children by ordering position
    children.sort(
        Comparator.comparing(ChartOfAccount::getOrderingPosition)
            .thenComparing(ChartOfAccount::getCode));

    for (ChartOfAccount child : children) {
      ChartOfAccountHierarchyDTO childDTO = toHierarchyDTO(child);
      node.addChild(childDTO);
      buildChildren(childDTO, accountsByParent);
    }

    // Sort children DTOs by ordering position
    node.getChildren()
        .sort(Comparator.comparing(ChartOfAccountHierarchyDTO::getOrderingPosition));
  }

  @Override
  public List<ChartOfAccountDTO> findAll(
      Boolean postable, String codePrefix, Long parentId, String type, String search) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Build specification with company scope and optional filters
    Specification<ChartOfAccount> spec =
        (root, query, criteriaBuilder) -> {
          List<Predicate> predicates = new ArrayList<>();

          // Always filter by company
          predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));

          // Filter by postable flag
          if (postable != null) {
            predicates.add(criteriaBuilder.equal(root.get("postable"), postable));
          }

          // Filter by code prefix
          if (codePrefix != null && !codePrefix.isBlank()) {
            predicates.add(
                criteriaBuilder.like(
                    root.get("code"), codePrefix + "%"));
          }

          // Filter by parent ID
          if (parentId != null) {
            predicates.add(criteriaBuilder.equal(root.get("parentId"), parentId));
          }

          // Filter by type
          if (type != null && !type.isBlank()) {
            predicates.add(criteriaBuilder.equal(root.get("type"), type));
          }

          // Search by code or name (unaccented Vietnamese support)
          if (search != null && !search.isBlank()) {
            // Use native query with unaccent function for better performance
            // For JPA Criteria, we'll use LIKE with lower case as fallback
            String searchPattern = "%" + search.toLowerCase() + "%";
            Predicate codePredicate =
                criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("code")), searchPattern);
            Predicate namePredicate =
                criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")), searchPattern);
            predicates.add(criteriaBuilder.or(codePredicate, namePredicate));
          }

          return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

    List<ChartOfAccount> accounts = chartOfAccountsRepository.findAll(spec);
    return accounts.stream().map(this::toDTO).collect(Collectors.toList());
  }

  @Override
  public List<ChartOfAccountDTO> findPostableLeafAccounts() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Find all postable accounts
    List<ChartOfAccount> postableAccounts = chartOfAccountsRepository.findByCompanyIdAndPostableTrue(companyId);

    // Filter to only leaf accounts (no children)
    return postableAccounts.stream()
        .filter(account -> !chartOfAccountsRepository.hasChildren(account.getId()))
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<ChartOfAccountDTO> getAccountById(Long accountId) {
    return chartOfAccountsRepository
        .findById(accountId)
        .filter(account -> {
          // Verify company scope
          Long companyId = CompanyContext.getCompanyId();
          return companyId != null && account.getCompanyId().equals(companyId);
        })
        .map(this::toDTO);
  }

  @Override
  public List<ChartOfAccountDTO> search(String searchTerm) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Use findAll with search filter
    return findAll(null, null, null, null, searchTerm);
  }

  /**
   * Convert entity to DTO.
   */
  private ChartOfAccountDTO toDTO(ChartOfAccount account) {
    String parentCode = null;
    if (account.getParentId() != null) {
      parentCode =
          chartOfAccountsRepository
              .findById(account.getParentId())
              .map(ChartOfAccount::getCode)
              .orElse(null);
    }

    return new ChartOfAccountDTO(
        account.getId(),
        account.getCompanyId(),
        account.getCode(),
        account.getName(),
        account.getType(),
        account.getNormalSide(),
        account.getPostable(),
        account.getParentId(),
        parentCode,
        account.getOrderingPosition(),
        null, // Balance - to be implemented in AC#11
        null, // createdAt - add if needed
        null  // updatedAt - add if needed
    );
  }

  /**
   * Convert entity to hierarchy DTO.
   */
  private ChartOfAccountHierarchyDTO toHierarchyDTO(ChartOfAccount account) {
    return new ChartOfAccountHierarchyDTO(
        account.getId(),
        account.getCode(),
        account.getName(),
        account.getType(),
        account.getNormalSide(),
        account.getPostable(),
        account.getParentId(),
        account.getOrderingPosition());
  }
}

package com.accounting.service.impl;

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

import com.accounting.dto.ChartOfAccountCreateRequest;
import com.accounting.dto.ChartOfAccountDTO;
import com.accounting.dto.ChartOfAccountHierarchyDTO;
import com.accounting.dto.ChartOfAccountUpdateRequest;
import com.accounting.dto.embedding.EmbeddingAction;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.ChartOfAccountsService;
import com.accounting.service.EmbeddingTriggerService;

import jakarta.persistence.criteria.Predicate;

/**
 * Implementation of ChartOfAccountsService for COA operations.
 */
@Service
@Transactional
public class ChartOfAccountsServiceImpl implements ChartOfAccountsService {

  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final EmbeddingTriggerService embeddingTriggerService;

  public ChartOfAccountsServiceImpl(
      ChartOfAccountsRepository chartOfAccountsRepository,
      EmbeddingTriggerService embeddingTriggerService) {
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.embeddingTriggerService = embeddingTriggerService;
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
      Boolean postable, String codePrefix, Long parentId, String type, String search, Boolean active) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // If search is provided, use native query with unaccent function for better
    // Vietnamese support
    if (search != null && !search.isBlank()) {
      String trimmedSearch = search.trim();
      List<ChartOfAccount> accounts = chartOfAccountsRepository.searchByCodeOrNameNative(companyId, trimmedSearch);

      // Apply additional filters in-memory (since native query doesn't support JPA
      // Specification)
      return accounts.stream()
          .filter(account -> {
            if (postable != null && account.getPostable() != postable) {
              return false;
            }
            if (codePrefix != null && !codePrefix.isBlank() && !account.getCode().startsWith(codePrefix)) {
              return false;
            }
            if (parentId != null) {
              Long accountParentId = account.getParentId();
              if (accountParentId == null && parentId != null) {
                return false;
              }
              if (accountParentId != null && !accountParentId.equals(parentId)) {
                return false;
              }
            }
            if (type != null && !type.isBlank() && !account.getType().equals(type)) {
              return false;
            }
            if (active != null && account.getActive() != active) {
              return false;
            }
            return true;
          })
          .map(this::toDTO)
          .collect(Collectors.toList());
    }

    // Build specification with company scope and optional filters (no search)
    Specification<ChartOfAccount> spec = (root, query, criteriaBuilder) -> {
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

      // Filter by active status
      if (active != null) {
        predicates.add(criteriaBuilder.equal(root.get("active"), active));
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
    return findAll(null, null, null, null, searchTerm, null);
  }

  @Override
  public ChartOfAccountDTO createAccount(ChartOfAccountCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Validate code uniqueness
    if (chartOfAccountsRepository.existsByCompanyIdAndCode(companyId, request.getCode(), null)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Account code already exists: " + request.getCode());
    }

    // Validate parent exists if provided
    if (request.getParentId() != null) {
      Optional<ChartOfAccount> parent = chartOfAccountsRepository.findById(request.getParentId());
      if (parent.isEmpty() || !parent.get().getCompanyId().equals(companyId)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Parent account not found or belongs to different company");
      }
    }

    ChartOfAccount account = new ChartOfAccount();
    account.setCompanyId(companyId);
    account.setCode(request.getCode());
    account.setName(request.getName());
    account.setNameEnglish(request.getNameEnglish());
    account.setDescription(request.getDescription());
    account.setType(request.getType());
    account.setNormalSide(request.getNormalSide());
    account.setParentId(request.getParentId());
    account.setOrderingPosition(request.getOrderingPosition());
    account.setPostable(false); // Will be updated based on children
    account.setActive(true); // New accounts are active by default

    ChartOfAccount saved = chartOfAccountsRepository.save(account);
    
    String parentCode = null;
    if (saved.getParentId() != null) {
      parentCode = chartOfAccountsRepository.findById(saved.getParentId())
          .map(ChartOfAccount::getCode)
          .orElse(null);
    }
    embeddingTriggerService.triggerChartOfAccountEmbedding(saved, parentCode, EmbeddingAction.UPSERT);
    
    return toDTO(saved);
  }

  @Override
  public ChartOfAccountDTO updateAccount(Long id, ChartOfAccountUpdateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    ChartOfAccount account = chartOfAccountsRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Account not found: " + id));

    // Verify company scope
    if (!account.getCompanyId().equals(companyId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Account belongs to different company");
    }

    // Update fields if provided
    if (request.getCode() != null) {
      // Validate code uniqueness (excluding current account)
      if (chartOfAccountsRepository.existsByCompanyIdAndCode(companyId, request.getCode(), id)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Account code already exists: " + request.getCode());
      }
      account.setCode(request.getCode());
    }
    if (request.getName() != null) {
      account.setName(request.getName());
    }
    if (request.getNameEnglish() != null) {
      account.setNameEnglish(request.getNameEnglish());
    }
    if (request.getDescription() != null) {
      account.setDescription(request.getDescription());
    }
    if (request.getType() != null) {
      account.setType(request.getType());
    }
    if (request.getNormalSide() != null) {
      account.setNormalSide(request.getNormalSide());
    }
    if (request.getParentId() != null) {
      // Validate parent exists if provided
      if (request.getParentId() != null) {
        Optional<ChartOfAccount> parent = chartOfAccountsRepository.findById(request.getParentId());
        if (parent.isEmpty() || !parent.get().getCompanyId().equals(companyId)) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Parent account not found or belongs to different company");
        }
        // Prevent circular reference
        if (request.getParentId().equals(id)) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Account cannot be its own parent");
        }
      }
      account.setParentId(request.getParentId());
    }
    if (request.getOrderingPosition() != null) {
      account.setOrderingPosition(request.getOrderingPosition());
    }

    ChartOfAccount saved = chartOfAccountsRepository.save(account);
    
    String parentCode = null;
    if (saved.getParentId() != null) {
      parentCode = chartOfAccountsRepository.findById(saved.getParentId())
          .map(ChartOfAccount::getCode)
          .orElse(null);
    }
    embeddingTriggerService.triggerChartOfAccountEmbedding(saved, parentCode, EmbeddingAction.UPSERT);
    
    return toDTO(saved);
  }

  @Override
  public void softDeleteAccount(Long id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    ChartOfAccount account = chartOfAccountsRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Account not found: " + id));

    // Verify company scope
    if (!account.getCompanyId().equals(companyId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Account belongs to different company");
    }

    account.setActive(false);
    chartOfAccountsRepository.save(account);
  }

  @Override
  public void activateAccount(Long id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    ChartOfAccount account = chartOfAccountsRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Account not found: " + id));

    // Verify company scope
    if (!account.getCompanyId().equals(companyId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Account belongs to different company");
    }

    account.setActive(true);
    chartOfAccountsRepository.save(account);
  }

  @Override
  public void deactivateAccount(Long id) {
    softDeleteAccount(id); // Same as soft delete
  }

  /**
   * Convert entity to DTO.
   */
  private ChartOfAccountDTO toDTO(ChartOfAccount account) {
    String parentCode = null;
    if (account.getParentId() != null) {
      parentCode = chartOfAccountsRepository
          .findById(account.getParentId())
          .map(ChartOfAccount::getCode)
          .orElse(null);
    }

    return new ChartOfAccountDTO(
        account.getId(),
        account.getCompanyId(),
        account.getCode(),
        account.getName(),
        account.getNameEnglish(),
        account.getDescription(),
        account.getActive(),
        account.getType(),
        account.getNormalSide(),
        account.getPostable(),
        account.getParentId(),
        parentCode,
        account.getOrderingPosition(),
        null, // Balance - to be implemented in AC#11
        null, // createdAt - add if needed
        null // updatedAt - add if needed
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
        account.getActive(),
        account.getParentId(),
        account.getOrderingPosition());
  }
}

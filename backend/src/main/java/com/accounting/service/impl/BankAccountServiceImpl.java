package com.accounting.service.impl;

import com.accounting.dto.BalanceTooltipDTO;
import com.accounting.dto.BankAccountCreateRequest;
import com.accounting.dto.BankAccountDTO;
import com.accounting.dto.BankAccountUpdateRequest;
import com.accounting.entity.BankAccount;
import com.accounting.repository.BankAccountRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.BankAccountService;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of BankAccountService for bank account CRUD operations.
 */
@Service
@Transactional
public class BankAccountServiceImpl implements BankAccountService {

  private final BankAccountRepository bankAccountRepository;
  private final AuditService auditService;

  public BankAccountServiceImpl(
      BankAccountRepository bankAccountRepository,
      AuditService auditService) {
    this.bankAccountRepository = bankAccountRepository;
    this.auditService = auditService;
  }

  /**
   * Get current HTTP request from RequestContextHolder.
   */
  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }

  /**
   * Get current user ID from security context.
   * Returns null if authentication is not available.
   */
  private Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      return null;
    }
    try {
      return Long.parseLong(authentication.getPrincipal().toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public Page<BankAccountDTO> findAll(Pageable pageable, String type, Boolean status, String search) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // If search is provided, use native query with unaccent function
    if (search != null && !search.isBlank()) {
      String trimmedSearch = search.trim();
      List<BankAccount> bankAccounts = bankAccountRepository.searchByAccountNumberOrBankNameNative(companyId, trimmedSearch);

      // Apply filters in-memory
      List<BankAccount> filtered = bankAccounts.stream()
          .filter(ba -> {
            if (type != null && !ba.getType().name().equals(type)) {
              return false;
            }
            if (status != null && !ba.getActive().equals(status)) {
              return false;
            }
            return true;
          })
          .toList();

      // Convert to DTO and create page
      List<BankAccountDTO> dtos = filtered.stream().map(this::toDTO).toList();
      return new org.springframework.data.domain.PageImpl<>(dtos, pageable, dtos.size());
    }

    // Build specification with company scope and optional filters
    Specification<BankAccount> spec = (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Always filter by company
      predicates.add(criteriaBuilder.equal(root.get("companyId"), companyId));

      // Filter by account type
      if (type != null && !type.isBlank()) {
        try {
          BankAccount.AccountType accountType = BankAccount.AccountType.valueOf(type.toUpperCase());
          predicates.add(criteriaBuilder.equal(root.get("type"), accountType));
        } catch (IllegalArgumentException e) {
          // Invalid type, return no results
          predicates.add(criteriaBuilder.disjunction());
        }
      }

      // Filter by active status
      if (status != null) {
        predicates.add(criteriaBuilder.equal(root.get("active"), status));
      }

      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };

    Page<BankAccount> bankAccounts = bankAccountRepository.findAll(spec, pageable);
    return bankAccounts.map(this::toDTO);
  }

  @Override
  public Optional<BankAccountDTO> getBankAccountById(Long bankAccountId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    return bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountId)
        .map(this::toDTO);
  }

  @Override
  public BankAccountDTO create(BankAccountCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Check for duplicate account number
    if (bankAccountRepository.existsByCompanyIdAndAccountNumber(companyId, request.getAccountNumber(), null)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Bank account with account number '" + request.getAccountNumber() + "' already exists for this company");
    }

    // Create bank account entity
    BankAccount bankAccount = new BankAccount();
    bankAccount.setCompanyId(companyId);
    bankAccount.setAccountNumber(request.getAccountNumber().trim());
    bankAccount.setBankName(request.getBankName().trim());
    bankAccount.setBranch(request.getBranch() != null ? request.getBranch().trim() : null);
    bankAccount.setType(request.getType());
    bankAccount.setOpeningBalance(request.getOpeningBalance());
    bankAccount.setActive(request.getActive() != null ? request.getActive() : true);

    BankAccount saved = bankAccountRepository.save(bankAccount);
    
    // Audit log
    auditService.logBankAccountCreated(saved.getId(), saved.getAccountNumber(), getCurrentUserId(), getCurrentRequest());
    
    return toDTO(saved);
  }

  @Override
  public BankAccountDTO update(Long bankAccountId, BankAccountUpdateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    BankAccount bankAccount = bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));

    // Capture old values for audit BEFORE updating
    Map<String, String> oldValues = new HashMap<>();
    oldValues.put("bankName", bankAccount.getBankName());
    oldValues.put("branch", bankAccount.getBranch() != null ? bankAccount.getBranch() : "");
    oldValues.put("type", bankAccount.getType().name());
    oldValues.put("openingBalance", bankAccount.getOpeningBalance().toString());
    oldValues.put("active", String.valueOf(bankAccount.getActive()));

    // Update fields (only non-null fields)
    if (request.getBankName() != null) {
      bankAccount.setBankName(request.getBankName().trim());
    }
    if (request.getBranch() != null) {
      bankAccount.setBranch(request.getBranch().trim());
    }
    if (request.getType() != null) {
      bankAccount.setType(request.getType());
    }
    if (request.getOpeningBalance() != null) {
      bankAccount.setOpeningBalance(request.getOpeningBalance());
    }

    BankAccount updated = bankAccountRepository.save(bankAccount);
    
    // Capture new values for audit
    Map<String, String> newValues = new HashMap<>();
    newValues.put("bankName", updated.getBankName());
    newValues.put("branch", updated.getBranch() != null ? updated.getBranch() : "");
    newValues.put("type", updated.getType().name());
    newValues.put("openingBalance", updated.getOpeningBalance().toString());
    newValues.put("active", String.valueOf(updated.getActive()));
    
    // Audit log
    auditService.logBankAccountUpdated(
        updated.getId(), 
        updated.getAccountNumber(), 
        getCurrentUserId(), 
        oldValues, 
        newValues, 
        request.getReason(),
        getCurrentRequest());
    
    return toDTO(updated);
  }

  @Override
  public void delete(Long bankAccountId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    BankAccount bankAccount = bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));

    // <CHANGE> Enforce referential integrity policy: deletion is blocked to preserve history.
    // Until voucher/period/reconciliation entities are integrated, enforce a strict policy:
    // do not allow hard deletes of bank accounts; require deactivation instead.
    String accountNumber = bankAccount.getAccountNumber();
    auditService.logBankAccountDeleted(
        bankAccountId,
        accountNumber,
        "Deletion blocked by policy: Use deactivation to preserve linked transaction history",
        getCurrentUserId(),
        getCurrentRequest());
    throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Cannot delete bank account: Deletion is blocked to preserve financial history. "
            + "Please deactivate the account instead. Deactivation preserves linked transaction data.");
  }

  @Override
  public void activate(Long bankAccountId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    BankAccount bankAccount = bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));

    bankAccount.setActive(true);
    bankAccountRepository.save(bankAccount);
    
    // Audit log
    auditService.logBankAccountActivated(bankAccountId, bankAccount.getAccountNumber(), getCurrentUserId(), getCurrentRequest());
  }

  @Override
  public void deactivate(Long bankAccountId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    BankAccount bankAccount = bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));

    bankAccount.setActive(false);
    bankAccountRepository.save(bankAccount);
    
    // Audit log
    auditService.logBankAccountDeactivated(bankAccountId, bankAccount.getAccountNumber(), getCurrentUserId(), getCurrentRequest());
  }

  @Override
  public BalanceTooltipDTO getBalanceTooltip(Long bankAccountId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }

    // Verify bank account exists and belongs to company
    BankAccount bankAccount = bankAccountRepository
        .findByCompanyIdAndId(companyId, bankAccountId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));

    // TODO: Implement actual balance calculation when period/transaction entities are available
    // For MVP, return placeholder values
    return new BalanceTooltipDTO(
        bankAccount.getOpeningBalance(), // Current balance placeholder
        BigDecimal.ZERO, // Prior balance placeholder
        "Current Period", // Current period placeholder
        "Prior Period" // Prior period placeholder
    );
  }

  /**
   * Convert BankAccount entity to DTO.
   */
  private BankAccountDTO toDTO(BankAccount bankAccount) {
    return new BankAccountDTO(
        bankAccount.getId(),
        bankAccount.getCompanyId(),
        bankAccount.getAccountNumber(),
        bankAccount.getBankName(),
        bankAccount.getBranch(),
        bankAccount.getType(),
        bankAccount.getOpeningBalance(),
        bankAccount.getActive(),
        bankAccount.getCreatedAt(),
        bankAccount.getUpdatedAt());
  }
}


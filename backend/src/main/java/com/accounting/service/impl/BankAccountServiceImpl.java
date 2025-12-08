package com.accounting.service.impl;

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

import com.accounting.dto.BalanceTooltipDTO;
import com.accounting.dto.BankAccountCreateRequest;
import com.accounting.dto.BankAccountDTO;
import com.accounting.dto.BankAccountUpdateRequest;
import com.accounting.entity.BankAccount;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.BankAccountRepository;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.BankAccountService;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Implementation of BankAccountService for bank account CRUD operations.
 */
@Service
@Transactional
public class BankAccountServiceImpl implements BankAccountService {

  private final BankAccountRepository bankAccountRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private final AuditService auditService;

  public BankAccountServiceImpl(
      BankAccountRepository bankAccountRepository,
      ChartOfAccountsRepository chartOfAccountsRepository,
      AuditService auditService) {
    this.bankAccountRepository = bankAccountRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
    this.auditService = auditService;
  }

  /**
   * Get current HTTP request from RequestContextHolder.
   */
  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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

  /**
   * Validate GL account code for a bank account (AC6.1-01).
   * GL account must exist, be postable (leaf account), and belong to valid
   * cash/bank categories.
   *
   * @param companyId     company ID
   * @param glAccountCode GL account code to validate
   * @param accountType   bank account type (CASH or BANK)
   * @throws ResponseStatusException if validation fails
   */
  private void validateGlAccountCode(Long companyId, String glAccountCode, BankAccount.AccountType accountType) {
    if (glAccountCode == null || glAccountCode.isBlank()) {
      return; // GL account is optional for now
    }

    // Find the GL account
    ChartOfAccount glAccount = chartOfAccountsRepository
        .findByCompanyIdAndCode(companyId, glAccountCode)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "GL account code '" + glAccountCode + "' does not exist in Chart of Accounts"));

    // Verify account is postable (leaf account)
    if (!Boolean.TRUE.equals(glAccount.getPostable())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "GL account '" + glAccountCode
              + "' is not a postable (leaf) account. Only leaf accounts can be linked to bank accounts.");
    }

    // Validate account type matches bank account type
    // Cash accounts: 1111, 1112 (Vietnamese dong and foreign currency cash)
    // Bank accounts: 1121, 1122 (Bank deposits in dong and foreign currency)
    String code = glAccount.getCode();
    if (accountType == BankAccount.AccountType.CASH) {
      if (!code.startsWith("1111") && !code.startsWith("1112")) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "GL account '" + glAccountCode
                + "' is not a valid cash account. Cash accounts must be under 1111 or 1112.");
      }
    } else if (accountType == BankAccount.AccountType.BANK) {
      if (!code.startsWith("1121") && !code.startsWith("1122")) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "GL account '" + glAccountCode
                + "' is not a valid bank account. Bank accounts must be under 1121 or 1122.");
      }
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
      List<BankAccount> bankAccounts = bankAccountRepository.searchByAccountNumberOrBankNameNative(companyId,
          trimmedSearch);

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

    // Validate GL account code (AC6.1-01)
    validateGlAccountCode(companyId, request.getGlAccountCode(), request.getType());

    // Create bank account entity
    BankAccount bankAccount = new BankAccount();
    bankAccount.setCompanyId(companyId);
    bankAccount.setAccountNumber(request.getAccountNumber().trim());
    bankAccount.setBankName(request.getBankName().trim());
    bankAccount.setBranch(request.getBranch() != null ? request.getBranch().trim() : null);
    bankAccount.setType(request.getType());
    bankAccount.setOpeningBalance(request.getOpeningBalance());
    bankAccount.setActive(request.getActive() != null ? request.getActive() : true);
    bankAccount.setGlAccountCode(request.getGlAccountCode() != null ? request.getGlAccountCode().trim() : null);
    bankAccount.setOpeningBalanceLocked(false); // New accounts are not locked

    BankAccount saved = bankAccountRepository.save(bankAccount);

    Map<String, String> newValues = new HashMap<>();
    newValues.put("accountNumber", saved.getAccountNumber());
    newValues.put("bankName", saved.getBankName());
    newValues.put("branch", saved.getBranch() != null ? saved.getBranch() : "");
    newValues.put("type", saved.getType().name());
    newValues.put("openingBalance", saved.getOpeningBalance().toPlainString());
    newValues.put("active", String.valueOf(saved.getActive()));
    newValues.put("glAccountCode", saved.getGlAccountCode() != null ? saved.getGlAccountCode() : "");

    auditService.logBankAccountCreated(
        saved.getId(), saved.getAccountNumber(), getCurrentUserId(), newValues, getCurrentRequest());

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
    oldValues.put("glAccountCode", bankAccount.getGlAccountCode() != null ? bankAccount.getGlAccountCode() : "");

    // Check opening balance lock (AC6.1-10)
    if (request.getOpeningBalance() != null && Boolean.TRUE.equals(bankAccount.getOpeningBalanceLocked())) {
      // Opening balance is locked - require admin override with reason
      if (request.getReason() == null || request.getReason().isBlank()) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Opening balance is locked after period close. Admin override requires a reason.");
      }
      // Log the override attempt with HIGH priority
      auditService.logBankAccountUpdated(
          bankAccount.getId(),
          bankAccount.getAccountNumber(),
          getCurrentUserId(),
          Map.of("action", "OPENING_BALANCE_OVERRIDE_ATTEMPT", "reason", request.getReason()),
          Map.of("newOpeningBalance", request.getOpeningBalance().toString()),
          "Admin override: " + request.getReason(),
          getCurrentRequest());
    }

    // Validate GL account code if being changed (AC6.1-01)
    if (request.getGlAccountCode() != null) {
      BankAccount.AccountType effectiveType = request.getType() != null ? request.getType() : bankAccount.getType();
      validateGlAccountCode(companyId, request.getGlAccountCode(), effectiveType);
    }

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
    if (request.getGlAccountCode() != null) {
      bankAccount.setGlAccountCode(request.getGlAccountCode().trim());
    }

    BankAccount updated = bankAccountRepository.save(bankAccount);

    // Capture new values for audit
    Map<String, String> newValues = new HashMap<>();
    newValues.put("bankName", updated.getBankName());
    newValues.put("branch", updated.getBranch() != null ? updated.getBranch() : "");
    newValues.put("type", updated.getType().name());
    newValues.put("openingBalance", updated.getOpeningBalance().toString());
    newValues.put("active", String.valueOf(updated.getActive()));
    newValues.put("glAccountCode", updated.getGlAccountCode() != null ? updated.getGlAccountCode() : "");

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

    // Check for posted references (AC6.1-04)
    if (bankAccount.getGlAccountCode() != null) {
      long postedCount = bankAccountRepository.countPostedReferencesByGlAccountCode(
          companyId, bankAccount.getGlAccountCode());
      if (postedCount > 0) {
        List<String> examples = bankAccountRepository.findPostedTransactionExamples(
            companyId, bankAccount.getGlAccountCode(), 5);
        String exampleText = examples.isEmpty() ? "" : " Examples: " + String.join(", ", examples);
        auditService.logBankAccountDeleted(
            bankAccountId,
            bankAccount.getAccountNumber(),
            "Deletion blocked: " + postedCount + " posted transaction(s) reference this account",
            getCurrentUserId(),
            getCurrentRequest());
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Cannot delete bank account: " + postedCount + " posted transaction(s) reference this account."
                + exampleText +
                " Please deactivate the account instead to preserve financial history.");
      }
    }

    // If no references, allow deletion (but still log the action)
    String accountNumber = bankAccount.getAccountNumber();
    bankAccountRepository.delete(bankAccount);
    auditService.logBankAccountDeleted(
        bankAccountId,
        accountNumber,
        "Bank account deleted successfully",
        getCurrentUserId(),
        getCurrentRequest());
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
    auditService.logBankAccountActivated(bankAccountId, bankAccount.getAccountNumber(), getCurrentUserId(),
        getCurrentRequest());
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

    // Check for unposted transactions referencing this account (AC6.1-03)
    if (bankAccount.getGlAccountCode() != null) {
      long unpostedCount = bankAccountRepository.countUnpostedTransactionsByGlAccountCode(
          companyId, bankAccount.getGlAccountCode());
      if (unpostedCount > 0) {
        List<String> examples = bankAccountRepository.findUnpostedTransactionExamples(
            companyId, bankAccount.getGlAccountCode(), 5);
        String exampleText = examples.isEmpty() ? "" : " Examples: " + String.join(", ", examples);
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Cannot deactivate bank account: " + unpostedCount + " unposted transaction(s) reference this account."
                + exampleText);
      }
    }

    bankAccount.setActive(false);
    bankAccountRepository.save(bankAccount);

    // Audit log
    auditService.logBankAccountDeactivated(bankAccountId, bankAccount.getAccountNumber(), getCurrentUserId(),
        getCurrentRequest());
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

    // Get last transaction date if GL account code is set
    java.time.LocalDate lastTxDate = null;
    if (bankAccount.getGlAccountCode() != null) {
      lastTxDate = bankAccountRepository.findLastTransactionDate(companyId, bankAccount.getGlAccountCode());
    }

    // For MVP, current balance = opening balance (actual calculation requires
    // journal entries integration)
    // TODO: Implement actual balance calculation: opening_balance + SUM(credits) -
    // SUM(debits)
    return new BalanceTooltipDTO(
        bankAccount.getOpeningBalance(),
        lastTxDate,
        bankAccount.getLastReconciledDate());
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
        bankAccount.getGlAccountCode(),
        bankAccount.getOpeningBalanceLocked(),
        bankAccount.getLastReconciledDate(),
        bankAccount.getLastReconciledBalance(),
        bankAccount.getCreatedAt(),
        bankAccount.getUpdatedAt());
  }
}

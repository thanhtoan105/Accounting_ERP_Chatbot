package com.accounting.service.impl;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.AccountDefaultDTO;
import com.accounting.dto.DefaultAccountCreateRequest;
import com.accounting.dto.DefaultAccountDTO;
import com.accounting.dto.DefaultAccountUpdateRequest;
import com.accounting.entity.ChartOfAccount;
import com.accounting.entity.DefaultAccount;
import com.accounting.entity.DefaultAccountEntry;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.repository.DefaultAccountEntryRepository;
import com.accounting.repository.DefaultAccountRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.DefaultAccountService;

/**
 * Implementation of DefaultAccountService.
 */
@Service
@Transactional
public class DefaultAccountServiceImpl implements DefaultAccountService {

  private final DefaultAccountRepository defaultAccountRepository;
  private final DefaultAccountEntryRepository defaultAccountEntryRepository;
  private final ChartOfAccountsRepository chartOfAccountsRepository;
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

  // Valid voucher types
  private static final List<String> VALID_VOUCHER_TYPES = List.of(
      "Cash Payment",
      "Bank Payment",
      "Cash Receipt",
      "Bank Receipt",
      "Other Business Voucher"
  );

  public DefaultAccountServiceImpl(
      DefaultAccountRepository defaultAccountRepository,
      DefaultAccountEntryRepository defaultAccountEntryRepository,
      ChartOfAccountsRepository chartOfAccountsRepository) {
    this.defaultAccountRepository = defaultAccountRepository;
    this.defaultAccountEntryRepository = defaultAccountEntryRepository;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DefaultAccountDTO> findAll(String search, String status, String voucherType) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    List<DefaultAccount> defaultAccounts;
    if (search != null && !search.trim().isEmpty() && status != null && !status.trim().isEmpty()
        && voucherType != null && !voucherType.trim().isEmpty()) {
      // Filter by all three
      List<DefaultAccount> byStatusAndSearch =
          defaultAccountRepository.findByCompanyIdAndStatusAndSearch(companyId, status.trim().toUpperCase(),
              search.trim());
      defaultAccounts = byStatusAndSearch.stream()
          .filter(da -> da.getVoucherType().equalsIgnoreCase(voucherType.trim()))
          .collect(Collectors.toList());
    } else if (search != null && !search.trim().isEmpty() && status != null && !status.trim().isEmpty()) {
      defaultAccounts = defaultAccountRepository.findByCompanyIdAndStatusAndSearch(companyId,
          status.trim().toUpperCase(), search.trim());
    } else if (search != null && !search.trim().isEmpty() && voucherType != null
        && !voucherType.trim().isEmpty()) {
      List<DefaultAccount> bySearch = defaultAccountRepository.findByCompanyIdAndSearch(companyId, search.trim());
      defaultAccounts = bySearch.stream()
          .filter(da -> da.getVoucherType().equalsIgnoreCase(voucherType.trim()))
          .collect(Collectors.toList());
    } else if (status != null && !status.trim().isEmpty() && voucherType != null
        && !voucherType.trim().isEmpty()) {
      List<DefaultAccount> byStatus = defaultAccountRepository.findByCompanyIdAndStatus(companyId,
          status.trim().toUpperCase());
      defaultAccounts = byStatus.stream()
          .filter(da -> da.getVoucherType().equalsIgnoreCase(voucherType.trim()))
          .collect(Collectors.toList());
    } else if (search != null && !search.trim().isEmpty()) {
      defaultAccounts = defaultAccountRepository.findByCompanyIdAndSearch(companyId, search.trim());
    } else if (status != null && !status.trim().isEmpty()) {
      defaultAccounts = defaultAccountRepository.findByCompanyIdAndStatus(companyId, status.trim().toUpperCase());
    } else if (voucherType != null && !voucherType.trim().isEmpty()) {
      defaultAccounts = defaultAccountRepository.findByCompanyIdAndVoucherType(companyId, voucherType.trim());
    } else {
      defaultAccounts = defaultAccountRepository.findByCompanyId(companyId);
    }

    return defaultAccounts.stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DefaultAccountDTO> findById(Long id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    return defaultAccountRepository.findById(id)
        .filter(da -> da.getCompanyId().equals(companyId))
        .map(this::toDTO);
  }

  @Override
  public DefaultAccountDTO create(DefaultAccountCreateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    // Validate voucher type
    if (!VALID_VOUCHER_TYPES.contains(request.getVoucherType())) {
      throw new IllegalArgumentException(
          "Invalid voucher type. Must be one of: " + String.join(", ", VALID_VOUCHER_TYPES));
    }

    // Validate account defaults
    if (request.getAccountDefaults() == null || request.getAccountDefaults().isEmpty()) {
      throw new IllegalArgumentException("At least one account default is required");
    }

    // Validate all accounts exist and belong to company
    for (DefaultAccountCreateRequest.AccountDefaultRequest entry : request.getAccountDefaults()) {
      if (entry.getDefaultAccountId() != null) {
        validateAccount(entry.getDefaultAccountId(), "Account");
      }
    }

    DefaultAccount defaultAccount = new DefaultAccount();
    defaultAccount.setCompanyId(companyId);
    defaultAccount.setVoucherType(request.getVoucherType().trim());
    defaultAccount.setEntryName(request.getEntryName().trim());
    defaultAccount.setStatus("ACTIVE");

    // Save first to get the ID
    DefaultAccount saved = defaultAccountRepository.save(defaultAccount);

    // Create entries with the saved default account ID
    List<DefaultAccountEntry> entries = new ArrayList<>();
    for (int i = 0; i < request.getAccountDefaults().size(); i++) {
      DefaultAccountCreateRequest.AccountDefaultRequest entryRequest = request.getAccountDefaults().get(i);
      DefaultAccountEntry entry = new DefaultAccountEntry();
      entry.setDefaultAccountId(saved.getId());
      entry.setColumnName(entryRequest.getColumnName().trim());
      entry.setAccountId(entryRequest.getDefaultAccountId());
      entry.setOrderingPosition(i);
      entries.add(entry);
    }
    saved.setEntries(entries);
    saved = defaultAccountRepository.save(saved);

    return toDTO(saved);
  }

  @Override
  public DefaultAccountDTO update(Long id, DefaultAccountUpdateRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    DefaultAccount defaultAccount = defaultAccountRepository.findById(id)
        .filter(da -> da.getCompanyId().equals(companyId))
        .orElseThrow(() -> new IllegalArgumentException("Default account not found"));

    // Validate voucher type if provided
    if (request.getVoucherType() != null && !request.getVoucherType().trim().isEmpty()) {
      if (!VALID_VOUCHER_TYPES.contains(request.getVoucherType().trim())) {
        throw new IllegalArgumentException(
            "Invalid voucher type. Must be one of: " + String.join(", ", VALID_VOUCHER_TYPES));
      }
      defaultAccount.setVoucherType(request.getVoucherType().trim());
    }

    if (request.getEntryName() != null && !request.getEntryName().trim().isEmpty()) {
      defaultAccount.setEntryName(request.getEntryName().trim());
    }

    // Update entries if provided
    if (request.getAccountDefaults() != null && !request.getAccountDefaults().isEmpty()) {
      // Validate all accounts exist and belong to company
      for (DefaultAccountUpdateRequest.AccountDefaultRequest entry : request.getAccountDefaults()) {
        if (entry.getDefaultAccountId() != null) {
          validateAccount(entry.getDefaultAccountId(), "Account");
        }
      }

      // Delete existing entries
      defaultAccountEntryRepository.deleteByDefaultAccountId(id);

      // Create new entries
      List<DefaultAccountEntry> entries = new ArrayList<>();
      for (int i = 0; i < request.getAccountDefaults().size(); i++) {
        DefaultAccountUpdateRequest.AccountDefaultRequest entryRequest = request.getAccountDefaults().get(i);
        DefaultAccountEntry entry = new DefaultAccountEntry();
        entry.setDefaultAccountId(id);
        entry.setColumnName(entryRequest.getColumnName().trim());
        entry.setAccountId(entryRequest.getDefaultAccountId());
        entry.setOrderingPosition(i);
        entries.add(entry);
      }
      defaultAccount.setEntries(entries);
    }

    DefaultAccount saved = defaultAccountRepository.save(defaultAccount);
    return toDTO(saved);
  }

  @Override
  public void delete(Long id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("Company context is required");
    }

    DefaultAccount defaultAccount = defaultAccountRepository.findById(id)
        .filter(da -> da.getCompanyId().equals(companyId))
        .orElseThrow(() -> new IllegalArgumentException("Default account not found"));

    // Cascade delete will handle entries
    if (defaultAccount.getId() != null) {
      defaultAccountRepository.deleteById(Objects.requireNonNull(defaultAccount.getId()));
    } else {
      defaultAccountRepository.delete(defaultAccount);
    }
  }

  /**
   * Validate that account exists, belongs to company, and is active.
   */
  private void validateAccount(Long accountId, String accountLabel) {
    if (accountId == null) {
      return; // Skip validation if null (optional field)
    }

    Long companyId = CompanyContext.getCompanyId();
    Optional<ChartOfAccount> account = chartOfAccountsRepository.findById(accountId);

    if (account.isEmpty()) {
      throw new IllegalArgumentException(accountLabel + " not found");
    }

    ChartOfAccount acc = account.get();
    if (!acc.getCompanyId().equals(companyId)) {
      throw new IllegalArgumentException(accountLabel + " does not belong to current company");
    }

    if (!acc.getActive()) {
      throw new IllegalArgumentException(accountLabel + " is not active");
    }
  }

  /**
   * Convert entity to DTO.
   */
  private DefaultAccountDTO toDTO(DefaultAccount defaultAccount) {
    DefaultAccountDTO dto = new DefaultAccountDTO();
    dto.setId(defaultAccount.getId());
    dto.setCompanyId(defaultAccount.getCompanyId());
    dto.setVoucherType(defaultAccount.getVoucherType());
    dto.setEntryName(defaultAccount.getEntryName());
    dto.setStatus(defaultAccount.getStatus());
    dto.setCreatedAt(defaultAccount.getCreatedAt() != null
        ? DATE_TIME_FORMATTER.format(defaultAccount.getCreatedAt())
        : null);
    dto.setUpdatedAt(defaultAccount.getUpdatedAt() != null
        ? DATE_TIME_FORMATTER.format(defaultAccount.getUpdatedAt())
        : null);

    // Load entries with account details
    List<DefaultAccountEntry> entries =
        defaultAccountEntryRepository.findByDefaultAccountIdOrderByOrderingPosition(
            defaultAccount.getId());
    List<AccountDefaultDTO> accountDefaults = new ArrayList<>();

    for (DefaultAccountEntry entry : entries) {
      AccountDefaultDTO entryDto = new AccountDefaultDTO();
      entryDto.setColumnName(entry.getColumnName());
      entryDto.setAccountId(entry.getAccountId());

      // Load account details if account ID is provided
      if (entry.getAccountId() != null) {
        chartOfAccountsRepository.findById(entry.getAccountId())
            .ifPresent(account -> {
              entryDto.setAccountCode(account.getCode());
              entryDto.setAccountName(account.getName());
            });
      }

      accountDefaults.add(entryDto);
    }

    dto.setAccountDefaults(accountDefaults);
    return dto;
  }
}

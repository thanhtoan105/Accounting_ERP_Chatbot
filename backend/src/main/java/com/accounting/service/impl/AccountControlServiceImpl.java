package com.accounting.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.entity.AccountControl;
import com.accounting.repository.AccountControlRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AccountControlService;

/**
 * Implementation of AccountControlService.
 * Manages company-specific account control configuration for required dimensions.
 */
@Service
@Transactional
public class AccountControlServiceImpl implements AccountControlService {

  private final AccountControlRepository accountControlRepository;

  public AccountControlServiceImpl(AccountControlRepository accountControlRepository) {
    this.accountControlRepository = accountControlRepository;
  }

  @Override
  public Optional<AccountControl> getAccountControlById(UUID id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return accountControlRepository.findById(id)
        .filter(ac -> companyId.equals(ac.getCompanyId()));
  }

  @Override
  public Optional<AccountControl> getAccountControlByAccountId(Long accountId, Long companyId) {
    if (companyId == null) {
      companyId = CompanyContext.getCompanyId();
      if (companyId == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Missing company context");
      }
    }
    return accountControlRepository.findByAccountIdAndCompanyId(accountId, companyId);
  }

  @Override
  public List<AccountControl> getAllAccountControls() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }
    return accountControlRepository.findByCompanyId(companyId);
  }

  @Override
  public Optional<AccountControl> getRequiredDimensions(Long accountId, Long companyId) {
    if (companyId == null) {
      companyId = CompanyContext.getCompanyId();
      if (companyId == null) {
        return Optional.empty();
      }
    }
    return accountControlRepository.findByAccountIdAndCompanyId(accountId, companyId);
  }

  @Override
  public AccountControl saveAccountControl(AccountControl accountControl) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    // Ensure company ID is set
    accountControl.setCompanyId(companyId);

    // Set timestamps
    if (accountControl.getCreatedAt() == null) {
      accountControl.setCreatedAt(java.time.Instant.now());
    }
    accountControl.setUpdatedAt(java.time.Instant.now());

    return accountControlRepository.save(accountControl);
  }

  @Override
  public void deleteAccountControl(UUID id) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context");
    }

    Optional<AccountControl> accountControl = accountControlRepository.findById(id);
    if (accountControl.isEmpty() || !companyId.equals(accountControl.get().getCompanyId())) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Account control not found");
    }

    accountControlRepository.deleteById(id);
  }

  @Override
  public List<String> validateRequiredDimensions(
      AccountControl accountControl,
      Long customerId,
      Long supplierId,
      Long costCenterId,
      Long itemId) {

    List<String> errors = new ArrayList<>();

    if (accountControl == null) {
      return errors; // No requirements configured
    }

    if (Boolean.TRUE.equals(accountControl.getRequiresCustomer()) && customerId == null) {
      errors.add("Customer is required for this account");
    }

    if (Boolean.TRUE.equals(accountControl.getRequiresSupplier()) && supplierId == null) {
      errors.add("Supplier is required for this account");
    }

    if (Boolean.TRUE.equals(accountControl.getRequiresCostCenter()) && costCenterId == null) {
      errors.add("Cost Center is required for this account");
    }

    if (Boolean.TRUE.equals(accountControl.getRequiresItem()) && itemId == null) {
      errors.add("Item is required for this account");
    }

    return errors;
  }
}

package com.accounting.controller;

import com.accounting.dto.AccountControlCreateRequest;
import com.accounting.dto.AccountControlDTO;
import com.accounting.entity.AccountControl;
import com.accounting.entity.ChartOfAccount;
import com.accounting.repository.ChartOfAccountsRepository;
import com.accounting.service.AccountControlService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for Account Control operations.
 * Manages company-specific required dimension configuration per account.
 * Requires Chief Accountant+ role for create/update/delete operations.
 */
@RestController
@RequestMapping("/api/v1/account-controls")
public class AccountControlController {

  private final AccountControlService accountControlService;
  private final ChartOfAccountsRepository chartOfAccountsRepository;

  public AccountControlController(
      AccountControlService accountControlService,
      ChartOfAccountsRepository chartOfAccountsRepository) {
    this.accountControlService = accountControlService;
    this.chartOfAccountsRepository = chartOfAccountsRepository;
  }

  /**
   * Get all account controls for current company.
   * Requires authenticated user (any role can view).
   *
   * @return list of account controls
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getAllAccountControls() {
    List<AccountControl> accountControls = accountControlService.getAllAccountControls();
    List<AccountControlDTO> dtos = accountControls.stream()
        .map(this::toDTO)
        .collect(Collectors.toList());

    Map<String, Object> body = new HashMap<>();
    body.put("data", dtos);
    body.put("total", dtos.size());
    return ResponseEntity.ok(body);
  }

  /**
   * Get account control by ID.
   * Requires authenticated user (any role can view).
   *
   * @param id account control ID
   * @return account control DTO
   */
  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> getAccountControlById(@PathVariable UUID id) {
    return accountControlService.getAccountControlById(id)
        .map(ac -> {
          Map<String, Object> body = new HashMap<>();
          body.put("data", toDTO(ac));
          return ResponseEntity.ok(body);
        })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Create a new account control.
   * Requires Chief Accountant+ role.
   *
   * @param request account control create request
   * @return created account control DTO
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'ADMIN', 'CFO')")
  public ResponseEntity<Map<String, Object>> createAccountControl(
      @Valid @RequestBody AccountControlCreateRequest request) {
    
    // Check if account exists (company scoping is handled by AccountControlService)
    chartOfAccountsRepository.findById(request.getAccountId())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Account not found"));
    
    // Check if account control already exists for this account
    if (accountControlService.getAccountControlByAccountId(request.getAccountId(), null).isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Account control already exists for this account");
    }

    // Create account control entity
    AccountControl accountControl = new AccountControl();
    accountControl.setAccountId(request.getAccountId());
    accountControl.setRequiresCustomer(request.getRequiresCustomer() != null ? request.getRequiresCustomer() : false);
    accountControl.setRequiresSupplier(request.getRequiresSupplier() != null ? request.getRequiresSupplier() : false);
    accountControl.setRequiresCostCenter(request.getRequiresCostCenter() != null ? request.getRequiresCostCenter() : false);
    accountControl.setRequiresItem(request.getRequiresItem() != null ? request.getRequiresItem() : false);

    AccountControl saved = accountControlService.saveAccountControl(accountControl);

    Map<String, Object> body = new HashMap<>();
    body.put("data", toDTO(saved));
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * Update an existing account control.
   * Requires Chief Accountant+ role.
   *
   * @param id account control ID
   * @param request account control update request
   * @return updated account control DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'ADMIN', 'CFO')")
  public ResponseEntity<Map<String, Object>> updateAccountControl(
      @PathVariable UUID id,
      @Valid @RequestBody AccountControlCreateRequest request) {
    
    AccountControl accountControl = accountControlService.getAccountControlById(id)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Account control not found"));

    // Update fields
    accountControl.setRequiresCustomer(request.getRequiresCustomer() != null ? request.getRequiresCustomer() : false);
    accountControl.setRequiresSupplier(request.getRequiresSupplier() != null ? request.getRequiresSupplier() : false);
    accountControl.setRequiresCostCenter(request.getRequiresCostCenter() != null ? request.getRequiresCostCenter() : false);
    accountControl.setRequiresItem(request.getRequiresItem() != null ? request.getRequiresItem() : false);

    AccountControl saved = accountControlService.saveAccountControl(accountControl);

    Map<String, Object> body = new HashMap<>();
    body.put("data", toDTO(saved));
    return ResponseEntity.ok(body);
  }

  /**
   * Delete an account control.
   * Requires Chief Accountant+ role.
   *
   * @param id account control ID
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'ADMIN', 'CFO')")
  public ResponseEntity<Void> deleteAccountControl(@PathVariable UUID id) {
    accountControlService.deleteAccountControl(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Convert AccountControl entity to DTO.
   */
  private AccountControlDTO toDTO(AccountControl accountControl) {
    AccountControlDTO dto = new AccountControlDTO();
    dto.setId(accountControl.getId());
    dto.setAccountId(accountControl.getAccountId());
    dto.setCompanyId(accountControl.getCompanyId());
    dto.setRequiresCustomer(accountControl.getRequiresCustomer());
    dto.setRequiresSupplier(accountControl.getRequiresSupplier());
    dto.setRequiresCostCenter(accountControl.getRequiresCostCenter());
    dto.setRequiresItem(accountControl.getRequiresItem());
    dto.setCreatedAt(accountControl.getCreatedAt());
    dto.setUpdatedAt(accountControl.getUpdatedAt());

    // Fetch account details if available
    if (accountControl.getAccount() != null) {
      ChartOfAccount account = accountControl.getAccount();
      dto.setAccountCode(account.getCode());
      dto.setAccountName(account.getName());
    } else if (accountControl.getAccountId() != null) {
      // Fetch account if not loaded
      chartOfAccountsRepository.findById(accountControl.getAccountId())
          .ifPresent(account -> {
            dto.setAccountCode(account.getCode());
            dto.setAccountName(account.getName());
          });
    }

    return dto;
  }
}


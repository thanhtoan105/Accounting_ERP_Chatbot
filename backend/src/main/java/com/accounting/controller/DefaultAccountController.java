package com.accounting.controller;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.DefaultAccountCreateRequest;
import com.accounting.dto.DefaultAccountDTO;
import com.accounting.dto.DefaultAccountUpdateRequest;
import com.accounting.service.DefaultAccountService;

import jakarta.validation.Valid;

/**
 * REST controller for Default Account operations.
 * Requires admin or chief_accountant role.
 */
@RestController
@RequestMapping("/api/v1/default-accounts")
@PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
public class DefaultAccountController {

  private final DefaultAccountService defaultAccountService;

  public DefaultAccountController(DefaultAccountService defaultAccountService) {
    this.defaultAccountService = defaultAccountService;
  }

  /**
   * Get all default accounts with optional search, status, and voucherType filters.
   *
   * @param search search term for entry name or voucher type (optional)
   * @param status filter by status (ACTIVE/INACTIVE, optional)
   * @param voucherType filter by voucher type (optional)
   * @return list of default accounts
   */
  @GetMapping
  public ResponseEntity<Map<String, Object>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String voucherType) {
    List<DefaultAccountDTO> defaultAccounts =
        defaultAccountService.findAll(search, status, voucherType);
    Map<String, Object> body = new HashMap<>();
    body.put("data", defaultAccounts);
    body.put("total", defaultAccounts.size());
    return ResponseEntity.ok(body);
  }

  /**
   * Get default account by ID.
   *
   * @param id default account ID
   * @return default account details
   */
  @GetMapping("/{id}")
  public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
    return defaultAccountService.findById(id)
        .map(defaultAccount -> {
          Map<String, Object> body = new HashMap<>();
          body.put("data", defaultAccount);
          return ResponseEntity.ok(body);
        })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Create a new default account.
   *
   * @param request create request
   * @return created default account
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> create(
      @Valid @RequestBody DefaultAccountCreateRequest request) {
    DefaultAccountDTO defaultAccount = defaultAccountService.create(request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", defaultAccount);
    final URI location = URI.create("/api/v1/default-accounts/" + defaultAccount.getId());
    return ResponseEntity.created(Objects.requireNonNull(location)).body(body);
  }

  /**
   * Update an existing default account.
   *
   * @param id default account ID
   * @param request update request
   * @return updated default account
   */
  @PutMapping("/{id}")
  public ResponseEntity<Map<String, Object>> update(
      @PathVariable Long id, @Valid @RequestBody DefaultAccountUpdateRequest request) {
    DefaultAccountDTO defaultAccount = defaultAccountService.update(id, request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", defaultAccount);
    return ResponseEntity.ok(body);
  }

  /**
   * Delete a default account.
   *
   * @param id default account ID
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    defaultAccountService.delete(id);
    return ResponseEntity.noContent().build();
  }
}

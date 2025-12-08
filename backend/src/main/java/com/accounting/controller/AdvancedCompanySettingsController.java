package com.accounting.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.CompanySettingsDto;
import com.accounting.dto.UpdateCompanySettingsRequest;
import com.accounting.service.CompanySettingsService;

import jakarta.validation.Valid;

/**
 * REST controller for advanced CompanySettings operations.
 * Handles tax, currency, localization, numbering, and compliance settings.
 * Endpoints: /api/v1/company-settings
 */
@RestController
@RequestMapping("/api/v1/company-settings")
public class AdvancedCompanySettingsController {

  private final CompanySettingsService companySettingsService;

  public AdvancedCompanySettingsController(CompanySettingsService companySettingsService) {
    this.companySettingsService = companySettingsService;
  }

  /**
   * Get current company settings.
   * Returns advanced settings for the company in context.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   *
   * @return company settings DTO
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> getSettings() {
    CompanySettingsDto settings = companySettingsService.getCurrentCompanySettings();
    Map<String, Object> body = new HashMap<>();
    body.put("data", settings);
    return ResponseEntity.ok(body);
  }

  /**
   * Update current company settings.
   * Supports partial updates (only provided fields are updated).
   * Uses optimistic locking via updatedAt field (returns 409 Conflict on stale updates).
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   *
   * @param request update request with optional fields and updatedAt for optimistic locking
   * @return updated company settings DTO
   */
  @PutMapping
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> updateSettings(
      @Valid @RequestBody UpdateCompanySettingsRequest request) {
    CompanySettingsDto updated = companySettingsService.updateCurrentCompanySettings(request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", updated);
    return ResponseEntity.ok(body);
  }
}

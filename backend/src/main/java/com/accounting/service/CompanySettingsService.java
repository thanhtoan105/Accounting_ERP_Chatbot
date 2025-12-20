package com.accounting.service;

import com.accounting.dto.CompanySettingsDto;
import com.accounting.dto.UpdateCompanySettingsRequest;

/**
 * Service interface for CompanySettings operations.
 * Handles advanced company settings (tax, currency, localization, numbering, compliance).
 */
public interface CompanySettingsService {

  /**
   * Get current company settings for the company in context.
   *
   * @return company settings DTO
   */
  CompanySettingsDto getCurrentCompanySettings();

  /**
   * Update current company settings with optimistic locking.
   * Returns 409 Conflict if updatedAt doesn't match (stale update).
   *
   * @param request update request with optional fields and updatedAt for optimistic locking
   * @return updated company settings DTO
   */
  CompanySettingsDto updateCurrentCompanySettings(UpdateCompanySettingsRequest request);
}

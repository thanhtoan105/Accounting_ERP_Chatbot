package com.accounting.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for provisioning a new tenant (company). Used by Super Admin to create new companies
 * with initial admin user.
 */
public record TenantProvisionRequest(
    @NotBlank(message = "Company name is required")
        @Size(max = 255, message = "Company name must not exceed 255 characters")
        String companyName,
    @NotBlank(message = "Tax code is required")
        @Pattern(regexp = "^\\d{10}(\\d{4})?$", message = "Tax code must be 10 or 14 digits")
        String taxCode,
    @Size(max = 500, message = "Address must not exceed 500 characters") String address,
    @Size(max = 255, message = "Legal representative name must not exceed 255 characters")
        String legalRepresentative,
    @Pattern(
            regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$",
            message = "Fiscal year start must be MM-DD format")
        String fiscalYearStart,
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code") String currency,
    @NotBlank(message = "Admin email is required") @Email(message = "Admin email must be valid")
        String adminEmail,
    @NotBlank(message = "Admin name is required")
        @Size(max = 255, message = "Admin name must not exceed 255 characters")
        String adminName,
    @Pattern(regexp = "^(TT200|TT133)$", message = "COA preset must be TT200 or TT133")
        String coaPreset) {

  public TenantProvisionRequest {
    if (fiscalYearStart == null || fiscalYearStart.isBlank()) {
      fiscalYearStart = "01-01";
    }
    if (currency == null || currency.isBlank()) {
      currency = "VND";
    }
    if (coaPreset == null || coaPreset.isBlank()) {
      coaPreset = "TT200";
    }
  }
}

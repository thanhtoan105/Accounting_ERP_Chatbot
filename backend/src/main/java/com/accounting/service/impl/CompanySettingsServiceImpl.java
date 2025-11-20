package com.accounting.service.impl;

import com.accounting.dto.CompanySettingsDto;
import com.accounting.dto.UpdateCompanySettingsRequest;
import com.accounting.entity.CompanySettings;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.CompanySettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of CompanySettingsService for advanced company settings operations.
 * Handles tax, currency, localization, numbering, and compliance settings.
 */
@Service
@Transactional
public class CompanySettingsServiceImpl implements CompanySettingsService {

  private final CompanySettingsRepository companySettingsRepository;
  private final AuditService auditService;

  public CompanySettingsServiceImpl(
      CompanySettingsRepository companySettingsRepository,
      AuditService auditService) {
    this.companySettingsRepository = companySettingsRepository;
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
  @Transactional
  public CompanySettingsDto getCurrentCompanySettings() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ValidationException("Missing company context");
    }

    CompanySettings settings = companySettingsRepository
        .findByCompanyId(companyId)
        .orElseGet(() -> {
          // Create default settings if they don't exist
          // Note: This method is not read-only because it may create default settings
          CompanySettings defaultSettings = new CompanySettings();
          defaultSettings.setCompanyId(companyId);
          defaultSettings.setEInvoiceEnabled(false);
          defaultSettings.setBankReconciliationEnabled(false);
          return companySettingsRepository.save(defaultSettings);
        });

    return toDto(settings);
  }

  @Override
  public CompanySettingsDto updateCurrentCompanySettings(UpdateCompanySettingsRequest request) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ValidationException("Missing company context");
    }

    CompanySettings existing = companySettingsRepository
        .findByCompanyId(companyId)
        .orElseGet(() -> {
          // Create new settings if they don't exist
          CompanySettings newSettings = new CompanySettings();
          newSettings.setCompanyId(companyId);
          newSettings.setEInvoiceEnabled(false);
          newSettings.setBankReconciliationEnabled(false);
          return companySettingsRepository.save(newSettings);
        });

    // Optimistic locking: check updatedAt if provided
    if (request.getUpdatedAt() != null) {
      if (!existing.getUpdatedAt().equals(request.getUpdatedAt())) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Settings were modified by another user. Please refresh and try again.");
      }
    }

    // Capture old values for audit
    Map<String, String> oldValues = captureValues(existing);

    // Update fields (only non-null fields from request)
    if (request.getLegalName() != null) {
      existing.setLegalName(request.getLegalName());
    }
    if (request.getShortName() != null) {
      existing.setShortName(request.getShortName());
    }
    if (request.getRegistrationNumber() != null) {
      existing.setRegistrationNumber(request.getRegistrationNumber());
    }
    if (request.getDefaultFiscalYearStartMonth() != null) {
      existing.setDefaultFiscalYearStartMonth(request.getDefaultFiscalYearStartMonth());
    }
    if (request.getTimezone() != null) {
      existing.setTimezone(request.getTimezone());
    }
    if (request.getDefaultCurrency() != null) {
      existing.setDefaultCurrency(request.getDefaultCurrency());
    }
    if (request.getCurrencyFormat() != null) {
      existing.setCurrencyFormat(request.getCurrencyFormat());
    }
    if (request.getThousandSeparator() != null) {
      existing.setThousandSeparator(request.getThousandSeparator());
    }
    if (request.getDecimalSeparator() != null) {
      existing.setDecimalSeparator(request.getDecimalSeparator());
    }
    if (request.getDateFormat() != null) {
      existing.setDateFormat(request.getDateFormat());
    }
    if (request.getLanguage() != null) {
      existing.setLanguage(request.getLanguage());
    }
    if (request.getVatRegistrationNumber() != null) {
      existing.setVatRegistrationNumber(request.getVatRegistrationNumber());
    }
    if (request.getVatRatePresets() != null) {
      existing.setVatRatePresets(request.getVatRatePresets());
    }
    if (request.getInvoiceRoundingMode() != null) {
      existing.setInvoiceRoundingMode(request.getInvoiceRoundingMode());
    }
    if (request.getTaxRoundingMode() != null) {
      existing.setTaxRoundingMode(request.getTaxRoundingMode());
    }
    if (request.getEInvoiceEnabled() != null) {
      existing.setEInvoiceEnabled(request.getEInvoiceEnabled());
    }
    if (request.getAuditRetentionPeriodDays() != null) {
      existing.setAuditRetentionPeriodDays(request.getAuditRetentionPeriodDays());
    }
    if (request.getApprovalThresholdAmount() != null) {
      existing.setApprovalThresholdAmount(request.getApprovalThresholdAmount());
    }
    if (request.getNumberingConfig() != null) {
      existing.setNumberingConfig(request.getNumberingConfig());
    }
    if (request.getBankReconciliationEnabled() != null) {
      existing.setBankReconciliationEnabled(request.getBankReconciliationEnabled());
    }
    if (request.getExportFormatDefault() != null) {
      existing.setExportFormatDefault(request.getExportFormatDefault());
    }

    CompanySettings saved = companySettingsRepository.save(existing);

    // Capture new values for audit
    Map<String, String> newValues = captureValues(saved);

    // Audit log (best-effort, don't block on failures)
    try {
      auditService.logCompanySettingsUpdated(
          companyId,
          getCurrentUserId(),
          oldValues,
          newValues,
          getCurrentRequest());
    } catch (Exception ignore) {
      // Do not block on audit failures
    }

    return toDto(saved);
  }

  /**
   * Convert CompanySettings entity to DTO.
   */
  private CompanySettingsDto toDto(CompanySettings entity) {
    CompanySettingsDto dto = new CompanySettingsDto();
    dto.setId(entity.getId());
    dto.setCompanyId(entity.getCompanyId());
    dto.setLegalName(entity.getLegalName());
    dto.setShortName(entity.getShortName());
    dto.setRegistrationNumber(entity.getRegistrationNumber());
    dto.setDefaultFiscalYearStartMonth(entity.getDefaultFiscalYearStartMonth());
    dto.setTimezone(entity.getTimezone());
    dto.setDefaultCurrency(entity.getDefaultCurrency());
    dto.setCurrencyFormat(entity.getCurrencyFormat());
    dto.setThousandSeparator(entity.getThousandSeparator());
    dto.setDecimalSeparator(entity.getDecimalSeparator());
    dto.setDateFormat(entity.getDateFormat());
    dto.setLanguage(entity.getLanguage());
    dto.setVatRegistrationNumber(entity.getVatRegistrationNumber());
    dto.setVatRatePresets(entity.getVatRatePresets());
    dto.setInvoiceRoundingMode(entity.getInvoiceRoundingMode());
    dto.setTaxRoundingMode(entity.getTaxRoundingMode());
    dto.setEInvoiceEnabled(entity.getEInvoiceEnabled());
    dto.setAuditRetentionPeriodDays(entity.getAuditRetentionPeriodDays());
    dto.setApprovalThresholdAmount(entity.getApprovalThresholdAmount());
    dto.setNumberingConfig(entity.getNumberingConfig());
    dto.setBankReconciliationEnabled(entity.getBankReconciliationEnabled());
    dto.setExportFormatDefault(entity.getExportFormatDefault());
    dto.setCreatedAt(entity.getCreatedAt());
    dto.setUpdatedAt(entity.getUpdatedAt());
    return dto;
  }

  /**
   * Capture current values from entity for audit logging.
   */
  private Map<String, String> captureValues(CompanySettings entity) {
    Map<String, String> values = new HashMap<>();
    values.put("legalName", entity.getLegalName());
    values.put("shortName", entity.getShortName());
    values.put("registrationNumber", entity.getRegistrationNumber());
    values.put("defaultFiscalYearStartMonth",
        entity.getDefaultFiscalYearStartMonth() == null
            ? null
            : entity.getDefaultFiscalYearStartMonth().toString());
    values.put("timezone", entity.getTimezone());
    values.put("defaultCurrency", entity.getDefaultCurrency());
    values.put("currencyFormat", entity.getCurrencyFormat());
    values.put("thousandSeparator", entity.getThousandSeparator());
    values.put("decimalSeparator", entity.getDecimalSeparator());
    values.put("dateFormat", entity.getDateFormat());
    values.put("language", entity.getLanguage());
    values.put("vatRegistrationNumber", entity.getVatRegistrationNumber());
    values.put("vatRatePresets", entity.getVatRatePresets());
    values.put("invoiceRoundingMode", entity.getInvoiceRoundingMode());
    values.put("taxRoundingMode", entity.getTaxRoundingMode());
    values.put("eInvoiceEnabled",
        entity.getEInvoiceEnabled() == null ? null : entity.getEInvoiceEnabled().toString());
    values.put("auditRetentionPeriodDays",
        entity.getAuditRetentionPeriodDays() == null
            ? null
            : entity.getAuditRetentionPeriodDays().toString());
    values.put("numberingConfig", entity.getNumberingConfig());
    values.put("bankReconciliationEnabled",
        entity.getBankReconciliationEnabled() == null
            ? null
            : entity.getBankReconciliationEnabled().toString());
    values.put("exportFormatDefault", entity.getExportFormatDefault());
    return values;
  }
}


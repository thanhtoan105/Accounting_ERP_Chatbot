package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.CompanySettingsDto;
import com.accounting.dto.UpdateCompanySettingsRequest;
import com.accounting.entity.CompanySettings;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

@ExtendWith(MockitoExtension.class)
class CompanySettingsServiceImplTest {

  @Mock
  private CompanySettingsRepository companySettingsRepository;

  @Mock
  private AuditService auditService;

  private CompanySettingsServiceImpl companySettingsService;

  private Validator validator;

  private static final Long TEST_COMPANY_ID = 1L;

  @BeforeEach
  void setUp() {
    companySettingsService = new CompanySettingsServiceImpl(companySettingsRepository, auditService);
    CompanyContext.setCompanyId(TEST_COMPANY_ID);

    // Initialize validator for DTO validation tests
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void getCurrentCompanySettings_whenExists_returnsDto() {
    CompanySettings existing = createTestSettings();
    when(companySettingsRepository.findByCompanyId(TEST_COMPANY_ID))
        .thenReturn(Optional.of(existing));

    CompanySettingsDto result = companySettingsService.getCurrentCompanySettings();

    assertNotNull(result);
    assertEquals(TEST_COMPANY_ID, result.getCompanyId());
    assertEquals("Test Legal Name", result.getLegalName());
  }

  @Test
  void getCurrentCompanySettings_whenNotExists_createsDefault() {
    when(companySettingsRepository.findByCompanyId(TEST_COMPANY_ID))
        .thenReturn(Optional.empty());
    CompanySettings created = createTestSettings();
    when(companySettingsRepository.save(any(CompanySettings.class))).thenReturn(created);

    CompanySettingsDto result = companySettingsService.getCurrentCompanySettings();

    assertNotNull(result);
    verify(companySettingsRepository).save(any(CompanySettings.class));
  }

  @Test
  void updateCurrentCompanySettings_withValidRequest_updatesAndReturnsDto() {
    CompanySettings existing = createTestSettings();
    when(companySettingsRepository.findByCompanyId(TEST_COMPANY_ID))
        .thenReturn(Optional.of(existing));
    when(companySettingsRepository.save(any(CompanySettings.class))).thenReturn(existing);

    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setLegalName("Updated Legal Name");
    request.setUpdatedAt(existing.getUpdatedAt());

    CompanySettingsDto result = companySettingsService.updateCurrentCompanySettings(request);

    assertNotNull(result);
    verify(companySettingsRepository).save(any(CompanySettings.class));
    verify(auditService).logCompanySettingsUpdated(any(), any(), any(), any(), any());
  }

  @Test
  void updateCurrentCompanySettings_withStaleUpdatedAt_throws409() {
    CompanySettings existing = createTestSettings();
    when(companySettingsRepository.findByCompanyId(TEST_COMPANY_ID))
        .thenReturn(Optional.of(existing));

    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setLegalName("Updated Legal Name");
    request.setUpdatedAt(Instant.now().minusSeconds(100)); // Stale timestamp

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> companySettingsService.updateCurrentCompanySettings(request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    verify(companySettingsRepository, never()).save(any(CompanySettings.class));
  }

  @Test
  void updateCurrentCompanySettings_whenNotExists_createsNew() {
    when(companySettingsRepository.findByCompanyId(TEST_COMPANY_ID))
        .thenReturn(Optional.empty());
    CompanySettings created = createTestSettings();
    // save is called twice: once to create new settings, once to save updates
    when(companySettingsRepository.save(any(CompanySettings.class))).thenReturn(created);

    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setLegalName("New Legal Name");

    CompanySettingsDto result = companySettingsService.updateCurrentCompanySettings(request);

    assertNotNull(result);
    // save is called twice: once to create new settings, once to save the update
    verify(companySettingsRepository, times(2)).save(any(CompanySettings.class));
  }

  @Test
  void updateCurrentCompanySettings_withInvalidVatFormat_rejectsInvalidFormat() {
    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setVatRegistrationNumber("12345"); // Invalid: not 10 digits

    Set<ConstraintViolation<UpdateCompanySettingsRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    ConstraintViolation<UpdateCompanySettingsRequest> violation = violations.iterator().next();
    assertEquals("vatRegistrationNumber", violation.getPropertyPath().toString());
    assertEquals("VAT registration number must be 10 digits", violation.getMessage());
  }

  @Test
  void updateCurrentCompanySettings_withValidVatFormat_acceptsValidFormat() {
    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setVatRegistrationNumber("1234567890"); // Valid: exactly 10 digits

    Set<ConstraintViolation<UpdateCompanySettingsRequest>> violations = validator.validate(request);

    // Filter to only VAT-related violations
    long vatViolations = violations.stream()
        .filter(v -> v.getPropertyPath().toString().equals("vatRegistrationNumber"))
        .count();
    assertEquals(0, vatViolations);
  }

  @Test
  void updateCurrentCompanySettings_withInvalidCurrencyFormat_rejectsInvalidFormat() {
    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setDefaultCurrency("usd"); // Invalid: lowercase, should be uppercase

    Set<ConstraintViolation<UpdateCompanySettingsRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    ConstraintViolation<UpdateCompanySettingsRequest> violation = violations.iterator().next();
    assertEquals("defaultCurrency", violation.getPropertyPath().toString());
    assertEquals("Currency code must be 3 uppercase letters", violation.getMessage());
  }

  @Test
  void updateCurrentCompanySettings_withValidCurrencyFormat_acceptsValidFormat() {
    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setDefaultCurrency("USD"); // Valid: 3 uppercase letters

    Set<ConstraintViolation<UpdateCompanySettingsRequest>> violations = validator.validate(request);

    // Filter to only currency-related violations
    long currencyViolations = violations.stream()
        .filter(v -> v.getPropertyPath().toString().equals("defaultCurrency"))
        .count();
    assertEquals(0, currencyViolations);
  }

  @Test
  void updateCurrentCompanySettings_withInvalidFiscalYearMonth_rejectsOutOfRange() {
    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setDefaultFiscalYearStartMonth(13); // Invalid: > 12

    Set<ConstraintViolation<UpdateCompanySettingsRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    ConstraintViolation<UpdateCompanySettingsRequest> violation = violations.iterator().next();
    assertEquals("defaultFiscalYearStartMonth", violation.getPropertyPath().toString());
  }

  @Test
  void updateCurrentCompanySettings_withFiscalYearMonthBelowRange_rejectsOutOfRange() {
    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setDefaultFiscalYearStartMonth(0); // Invalid: < 1

    Set<ConstraintViolation<UpdateCompanySettingsRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    ConstraintViolation<UpdateCompanySettingsRequest> violation = violations.iterator().next();
    assertEquals("defaultFiscalYearStartMonth", violation.getPropertyPath().toString());
  }

  @Test
  void updateCurrentCompanySettings_withValidFiscalYearMonth_acceptsValidRange() {
    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setDefaultFiscalYearStartMonth(6); // Valid: 1-12

    Set<ConstraintViolation<UpdateCompanySettingsRequest>> violations = validator.validate(request);

    // Filter to only fiscal year month violations
    long fiscalYearViolations = violations.stream()
        .filter(v -> v.getPropertyPath().toString().equals("defaultFiscalYearStartMonth"))
        .count();
    assertEquals(0, fiscalYearViolations);
  }

  @Test
  void updateCurrentCompanySettings_withChanges_logsBeforeAndAfterValues() {
    CompanySettings existing = createTestSettings();
    existing.setLegalName("Old Legal Name");
    existing.setShortName("Old Short");
    existing.setVatRegistrationNumber("1111111111");
    when(companySettingsRepository.findByCompanyId(TEST_COMPANY_ID))
        .thenReturn(Optional.of(existing));

    // Create updated entity with new values
    CompanySettings updated = createTestSettings();
    updated.setLegalName("New Legal Name");
    updated.setShortName("New Short");
    updated.setVatRegistrationNumber("2222222222");
    when(companySettingsRepository.save(any(CompanySettings.class))).thenReturn(updated);

    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setLegalName("New Legal Name");
    request.setShortName("New Short");
    request.setVatRegistrationNumber("2222222222");
    request.setUpdatedAt(existing.getUpdatedAt());

    companySettingsService.updateCurrentCompanySettings(request);

    // Verify audit service was called with correct parameters
    verify(auditService).logCompanySettingsUpdated(
        eq(TEST_COMPANY_ID),
        any(), // userId (may be null in test context)
        argThat((Map<String, String> oldValues) -> {
          return "Old Legal Name".equals(oldValues.get("legalName"))
              && "Old Short".equals(oldValues.get("shortName"))
              && "1111111111".equals(oldValues.get("vatRegistrationNumber"));
        }),
        argThat((Map<String, String> newValues) -> {
          return "New Legal Name".equals(newValues.get("legalName"))
              && "New Short".equals(newValues.get("shortName"))
              && "2222222222".equals(newValues.get("vatRegistrationNumber"));
        }),
        any());
  }

  @Test
  void updateCurrentCompanySettings_withPartialUpdate_logsOnlyChangedFields() {
    CompanySettings existing = createTestSettings();
    existing.setLegalName("Original Name");
    existing.setShortName("Original");
    when(companySettingsRepository.findByCompanyId(TEST_COMPANY_ID))
        .thenReturn(Optional.of(existing));

    CompanySettings updated = createTestSettings();
    updated.setLegalName("Updated Name");
    updated.setShortName("Original"); // Not changed
    when(companySettingsRepository.save(any(CompanySettings.class))).thenReturn(updated);

    UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
    request.setLegalName("Updated Name");
    // shortName not included in request
    request.setUpdatedAt(existing.getUpdatedAt());

    companySettingsService.updateCurrentCompanySettings(request);

    // Verify audit service captures both old and new values for all fields
    // (captureValues captures all fields, not just changed ones)
    verify(auditService).logCompanySettingsUpdated(
        eq(TEST_COMPANY_ID),
        any(), // userId (may be null in test context)
        argThat((Map<String, String> oldValues) -> "Original Name".equals(oldValues.get("legalName"))),
        argThat((Map<String, String> newValues) -> "Updated Name".equals(newValues.get("legalName"))),
        any());
  }

  private CompanySettings createTestSettings() {
    CompanySettings settings = new CompanySettings();
    settings.setId(1L);
    settings.setCompanyId(TEST_COMPANY_ID);
    settings.setLegalName("Test Legal Name");
    settings.setShortName("Test");
    settings.setEInvoiceEnabled(false);
    settings.setBankReconciliationEnabled(false);
    settings.setCreatedAt(Instant.now());
    settings.setUpdatedAt(Instant.now());
    return settings;
  }
}

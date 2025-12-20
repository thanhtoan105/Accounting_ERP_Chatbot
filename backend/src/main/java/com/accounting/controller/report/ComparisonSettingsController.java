package com.accounting.controller.report;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.report.ComparisonSettingsDTO;
import com.accounting.entity.CompanySettings;
import com.accounting.repository.CompanySettingsRepository;
import com.accounting.security.CompanyContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * REST controller for comparison settings management.
 * Allows admins to configure materiality thresholds and display preferences.
 */
@RestController
@RequestMapping("/api/settings/comparison")
@Tag(name = "Comparison Settings", description = "Configure multi-period comparison thresholds and preferences")
public class ComparisonSettingsController {

  private final CompanySettingsRepository companySettingsRepository;
  private final ObjectMapper objectMapper;

  public ComparisonSettingsController(
      CompanySettingsRepository companySettingsRepository,
      ObjectMapper objectMapper) {
    this.companySettingsRepository = companySettingsRepository;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'AUDITOR', 'ACCOUNTANT')")
  @Operation(summary = "Get comparison settings",
      description = "Get the current company's comparison settings for multi-period reports")
  public ResponseEntity<ComparisonSettingsDTO> getSettings() {
    Long companyId = requireCompanyId();

    ComparisonSettingsDTO settings = companySettingsRepository
        .findByCompanyId(companyId)
        .map(this::parseSettings)
        .orElse(ComparisonSettingsDTO.defaults());

    return ResponseEntity.ok(settings);
  }

  @PutMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Update comparison settings",
      description = "Update the company's comparison settings (Admin only)")
  public ResponseEntity<ComparisonSettingsDTO> updateSettings(
      @Valid @RequestBody ComparisonSettingsDTO settings) {
    Long companyId = requireCompanyId();

    CompanySettings companySettings = companySettingsRepository
        .findByCompanyId(companyId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Company settings not found"));

    try {
      String json = objectMapper.writeValueAsString(settings);
      companySettings.setComparisonSettings(json);
      companySettingsRepository.save(companySettings);
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize settings");
    }

    return ResponseEntity.ok(settings);
  }

  private Long requireCompanyId() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company context required");
    }
    return companyId;
  }

  private ComparisonSettingsDTO parseSettings(CompanySettings settings) {
    String json = settings.getComparisonSettings();
    if (json == null || json.isBlank()) {
      return ComparisonSettingsDTO.defaults();
    }
    try {
      return objectMapper.readValue(json, ComparisonSettingsDTO.class);
    } catch (JsonProcessingException e) {
      return ComparisonSettingsDTO.defaults();
    }
  }
}

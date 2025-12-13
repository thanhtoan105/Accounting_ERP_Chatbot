package com.accounting.controller.report;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.report.PeriodSummaryDTO;
import com.accounting.service.ComparisonPresetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for comparison period presets.
 * Provides endpoints for suggesting comparison periods based on mode (YoY, MoM, Quarterly).
 */
@RestController
@RequestMapping("/api/reports/comparison-presets")
@Tag(name = "Comparison Presets", description = "Period suggestion presets for multi-period comparison")
public class ComparisonPresetController {

  private final ComparisonPresetService comparisonPresetService;

  public ComparisonPresetController(ComparisonPresetService comparisonPresetService) {
    this.comparisonPresetService = comparisonPresetService;
  }

  @GetMapping("/suggested")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'AUDITOR', 'ACCOUNTANT')")
  @Operation(summary = "Get suggested comparison periods",
      description = "Returns suggested period IDs based on comparison mode (YOY, MOM, QUARTERLY)")
  public ResponseEntity<List<UUID>> getSuggestedPeriods(
      @Parameter(description = "Base period ID to compare from", required = true)
      @RequestParam UUID basePeriodId,
      @Parameter(description = "Comparison mode: YOY, MOM, QUARTERLY, CUSTOM", required = true)
      @RequestParam String mode,
      @Parameter(description = "Maximum number of periods to suggest (default: 4)")
      @RequestParam(defaultValue = "4") int maxPeriods) {

    List<UUID> periodIds = comparisonPresetService.getSuggestedPeriodIds(basePeriodId, mode, maxPeriods);
    return ResponseEntity.ok(periodIds);
  }

  @GetMapping("/available-periods")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO', 'AUDITOR', 'ACCOUNTANT')")
  @Operation(summary = "Get available periods for comparison",
      description = "Returns all available periods for the current company, sorted by start date descending")
  public ResponseEntity<List<PeriodSummaryDTO>> getAvailablePeriods() {
    List<PeriodSummaryDTO> periods = comparisonPresetService.getAvailablePeriods();
    return ResponseEntity.ok(periods);
  }
}

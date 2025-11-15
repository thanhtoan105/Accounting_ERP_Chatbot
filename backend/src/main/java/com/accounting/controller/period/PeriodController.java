package com.accounting.controller.period;

import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.dto.PeriodCloseRequest;
import com.accounting.dto.PeriodReopenRequest;
import com.accounting.dto.PeriodSummaryDTO;
import com.accounting.entity.AccountingPeriod;
import com.accounting.service.PeriodManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for Accounting Period operations.
 * Provides endpoints for period management, validation, and reporting.
 */
@RestController
@RequestMapping("/api/v1/periods")
@Tag(name = "Period Management", description = "APIs for managing accounting periods")
@SecurityRequirement(name = "bearerAuth")
public class PeriodController {

  private final PeriodManagementService periodManagementService;

  public PeriodController(PeriodManagementService periodManagementService) {
    this.periodManagementService = periodManagementService;
  }

  @GetMapping("/current")
  @Operation(summary = "Get current period", description = "Retrieves the current accounting period for the company")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Current period retrieved successfully",
          content = @Content(schema = @Schema(implementation = AccountingPeriodDTO.class))),
      @ApiResponse(responseCode = "404", description = "No current period found")
  })
  @PreAuthorize("hasAnyRole('ACCOUNTANT','CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> getCurrentPeriod() {
    return periodManagementService.getCurrentPeriod()
        .map(period -> ResponseEntity.ok(Map.of("data", period, "meta", Map.of("timestamp", System.currentTimeMillis()))))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No current period found"));
  }

  @GetMapping("/open")
  @Operation(summary = "Get open periods", description = "Retrieves open periods (current + 3 prior/next) for the period selector")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Open periods retrieved successfully",
          content = @Content(schema = @Schema(implementation = AccountingPeriodDTO.class)))
  })
  @PreAuthorize("hasAnyRole('ACCOUNTANT','CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> getOpenPeriods() {
    List<AccountingPeriodDTO> periods = periodManagementService.getOpenPeriods();
    return ResponseEntity.ok(Map.of(
        "data", periods,
        "meta", Map.of("count", periods.size(), "timestamp", System.currentTimeMillis())
    ));
  }

  @GetMapping("/{periodId}")
  @Operation(summary = "Get period by ID", description = "Retrieves specific period details")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Period retrieved successfully"),
      @ApiResponse(responseCode = "404", description = "Period not found")
  })
  @PreAuthorize("hasAnyRole('ACCOUNTANT','CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> getPeriodById(
      @Parameter(description = "Period ID") @PathVariable UUID periodId) {
    AccountingPeriodDTO period = periodManagementService.getPeriodById(periodId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Period not found"));

    return ResponseEntity.ok(Map.of("data", period, "meta", Map.of("timestamp", System.currentTimeMillis())));
  }

  @GetMapping("/{periodId}/summary")
  @Operation(summary = "Get period summary", description = "Retrieves period summary information for dashboard badges")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Period summary retrieved successfully"),
      @ApiResponse(responseCode = "404", description = "Period not found")
  })
  @PreAuthorize("hasAnyRole('ACCOUNTANT','CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> getPeriodSummary(
      @Parameter(description = "Period ID") @PathVariable UUID periodId) {
    PeriodSummaryDTO summary = periodManagementService.getPeriodSummary(periodId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Period not found"));

    return ResponseEntity.ok(Map.of("data", summary, "meta", Map.of("timestamp", System.currentTimeMillis())));
  }

  @GetMapping("/find-by-date")
  @Operation(summary = "Find period by date", description = "Retrieves period that contains the specified date")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Period found successfully"),
      @ApiResponse(responseCode = "404", description = "No period found for specified date")
  })
  @PreAuthorize("hasAnyRole('ACCOUNTANT','CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> findPeriodByDate(
      @Parameter(description = "Date to search for (yyyy-MM-dd)") @RequestParam LocalDate date) {
    AccountingPeriodDTO period = periodManagementService.findPeriodByDate(date)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No period found for specified date"));

    return ResponseEntity.ok(Map.of("data", period, "meta", Map.of("timestamp", System.currentTimeMillis())));
  }

  @GetMapping("/{periodId}/check-open")
  @Operation(summary = "Check if period is open", description = "Validates if a period is open for voucher operations")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Period status checked successfully"),
      @ApiResponse(responseCode = "404", description = "Period not found")
  })
  @PreAuthorize("hasAnyRole('ACCOUNTANT','CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> checkPeriodOpen(
      @Parameter(description = "Period ID") @PathVariable UUID periodId) {
    boolean isOpen = periodManagementService.isPeriodOpen(periodId);

    return ResponseEntity.ok(Map.of(
        "data", Map.of("isOpen", isOpen, "periodId", periodId),
        "meta", Map.of("timestamp", System.currentTimeMillis())
    ));
  }

  @GetMapping("/check-date-open")
  @Operation(summary = "Check if date is in open period", description = "Validates if a date falls within an open period")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Date validation completed successfully")
  })
  @PreAuthorize("hasAnyRole('ACCOUNTANT','CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> checkDateInOpenPeriod(
      @Parameter(description = "Date to validate (yyyy-MM-dd)") @RequestParam LocalDate date) {
    boolean isOpen = periodManagementService.isDateInOpenPeriod(date);

    return ResponseEntity.ok(Map.of(
        "data", Map.of("isDateInOpenPeriod", isOpen, "date", date),
        "meta", Map.of("timestamp", System.currentTimeMillis())
    ));
  }

  @PostMapping("/{periodId}/close")
  @Operation(summary = "Close period", description = "Closes an accounting period and locks all vouchers within it")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Period closed successfully"),
      @ApiResponse(responseCode = "400", description = "Cannot close period (drafts exist, unbalanced vouchers, etc.)"),
      @ApiResponse(responseCode = "404", description = "Period not found"),
      @ApiResponse(responseCode = "409", description = "Period already closed")
  })
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> closePeriod(
      @Parameter(description = "Period ID") @PathVariable UUID periodId,
      @Valid @RequestBody PeriodCloseRequest request) {
    AccountingPeriodDTO period = periodManagementService.closePeriod(periodId, request);

    return ResponseEntity.ok(Map.of(
        "data", period,
        "meta", Map.of("timestamp", System.currentTimeMillis(), "message", "Period closed successfully")
    ));
  }

  @PostMapping("/{periodId}/reopen")
  @Operation(summary = "Reopen period", description = "Reopens a closed accounting period and unlocks vouchers")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Period reopened successfully"),
      @ApiResponse(responseCode = "400", description = "Cannot reopen period (period not closed, missing approval)"),
      @ApiResponse(responseCode = "404", description = "Period not found")
  })
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> reopenPeriod(
      @Parameter(description = "Period ID") @PathVariable UUID periodId,
      @Valid @RequestBody PeriodReopenRequest request) {
    AccountingPeriodDTO period = periodManagementService.reopenPeriod(periodId, request);

    return ResponseEntity.ok(Map.of(
        "data", period,
        "meta", Map.of("timestamp", System.currentTimeMillis(), "message", "Period reopened successfully")
    ));
  }

  @GetMapping
  @Operation(summary = "Get all periods", description = "Retrieves all accounting periods for the company")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Periods retrieved successfully")
  })
  @PreAuthorize("hasAnyRole('ACCOUNTANT','CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> getAllPeriods() {
    List<AccountingPeriodDTO> periods = periodManagementService.getAllPeriods();
    return ResponseEntity.ok(Map.of(
        "data", periods,
        "meta", Map.of("count", periods.size(), "timestamp", System.currentTimeMillis())
    ));
  }

  @GetMapping("/fiscal-year/{fiscalYear}")
  @Operation(summary = "Get periods by fiscal year", description = "Retrieves periods for a specific fiscal year")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Periods retrieved successfully")
  })
  @PreAuthorize("hasAnyRole('ACCOUNTANT','CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> getPeriodsByFiscalYear(
      @Parameter(description = "Fiscal year") @PathVariable Integer fiscalYear) {
    List<AccountingPeriodDTO> periods = periodManagementService.getPeriodsByFiscalYear(fiscalYear);
    return ResponseEntity.ok(Map.of(
        "data", periods,
        "meta", Map.of("count", periods.size(), "fiscalYear", fiscalYear, "timestamp", System.currentTimeMillis())
    ));
  }

  @PostMapping
  @Operation(summary = "Create period", description = "Creates a new accounting period")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "201", description = "Period created successfully"),
      @ApiResponse(responseCode = "400", description = "Cannot create period (overlapping dates, invalid data)")
  })
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> createPeriod(@Valid @RequestBody AccountingPeriod period) {
    AccountingPeriodDTO createdPeriod = periodManagementService.createPeriod(period);

    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "data", createdPeriod,
        "meta", Map.of("timestamp", System.currentTimeMillis(), "message", "Period created successfully")
    ));
  }

  @PutMapping("/{periodId}")
  @Operation(summary = "Update period", description = "Updates an existing accounting period")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Period updated successfully"),
      @ApiResponse(responseCode = "400", description = "Cannot update period (period closed, overlapping dates)"),
      @ApiResponse(responseCode = "404", description = "Period not found")
  })
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Map<String, Object>> updatePeriod(
      @Parameter(description = "Period ID") @PathVariable UUID periodId,
      @Valid @RequestBody AccountingPeriod period) {
    AccountingPeriodDTO updatedPeriod = periodManagementService.updatePeriod(periodId, period);

    return ResponseEntity.ok(Map.of(
        "data", updatedPeriod,
        "meta", Map.of("timestamp", System.currentTimeMillis(), "message", "Period updated successfully")
    ));
  }

  @DeleteMapping("/{periodId}")
  @Operation(summary = "Delete period", description = "Deletes an accounting period (only if no vouchers exist)")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Period deleted successfully"),
      @ApiResponse(responseCode = "400", description = "Cannot delete period (period closed, contains vouchers)"),
      @ApiResponse(responseCode = "404", description = "Period not found")
  })
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT','ADMIN','CFO')")
  public ResponseEntity<Void> deletePeriod(
      @Parameter(description = "Period ID") @PathVariable UUID periodId) {
    periodManagementService.deletePeriod(periodId);
    return ResponseEntity.noContent().build();
  }
}
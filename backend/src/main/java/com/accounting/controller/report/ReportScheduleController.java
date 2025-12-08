package com.accounting.controller.report;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import com.accounting.dto.report.CreateReportScheduleRequest;
import com.accounting.dto.report.ReportScheduleDTO;
import com.accounting.dto.report.RunNowRequest;
import com.accounting.dto.report.RunNowResponse;
import com.accounting.dto.report.ScheduleRunDTO;
import com.accounting.dto.report.UpdateReportScheduleRequest;
import com.accounting.service.ReportSchedulerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reports/schedules")
@Tag(name = "Report Schedules", description = "Report scheduling and management endpoints")
public class ReportScheduleController {

  private final ReportSchedulerService reportSchedulerService;

  public ReportScheduleController(ReportSchedulerService reportSchedulerService) {
    this.reportSchedulerService = reportSchedulerService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('CFO','CHIEF_ACCOUNTANT','AUDITOR')")
  @Operation(summary = "List all schedules", description = "Get paginated list of report schedules")
  public ResponseEntity<Page<ReportScheduleDTO>> getSchedules(Pageable pageable) {
    return ResponseEntity.ok(reportSchedulerService.getSchedules(pageable));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('CFO','CHIEF_ACCOUNTANT')")
  @Operation(summary = "Create new schedule", description = "Create a new report schedule")
  public ResponseEntity<ReportScheduleDTO> createSchedule(
      @Valid @RequestBody CreateReportScheduleRequest request) {
    ReportScheduleDTO created = reportSchedulerService.createSchedule(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('CFO','CHIEF_ACCOUNTANT','AUDITOR')")
  @Operation(summary = "Get schedule details", description = "Get details of a specific schedule")
  public ResponseEntity<ReportScheduleDTO> getSchedule(@PathVariable UUID id) {
    return ResponseEntity.ok(reportSchedulerService.getSchedule(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('CFO','CHIEF_ACCOUNTANT')")
  @Operation(summary = "Update schedule", description = "Update an existing report schedule")
  public ResponseEntity<ReportScheduleDTO> updateSchedule(
      @PathVariable UUID id, @Valid @RequestBody UpdateReportScheduleRequest request) {
    return ResponseEntity.ok(reportSchedulerService.updateSchedule(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('CFO','CHIEF_ACCOUNTANT')")
  @Operation(summary = "Cancel schedule", description = "Disable a report schedule")
  public ResponseEntity<Map<String, Object>> cancelSchedule(@PathVariable UUID id) {
    reportSchedulerService.cancelSchedule(id);
    return ResponseEntity.ok(
        Map.of("id", id, "isActive", false, "message", "Schedule has been disabled"));
  }

  @PostMapping("/{id}/run-now")
  @PreAuthorize("hasAnyRole('CFO','CHIEF_ACCOUNTANT')")
  @Operation(summary = "Trigger immediate run", description = "Execute schedule immediately")
  public ResponseEntity<RunNowResponse> runNow(
      @PathVariable UUID id, @RequestBody(required = false) RunNowRequest request) {
    RunNowResponse response =
        reportSchedulerService.runNow(id, request != null ? request : new RunNowRequest());
    return ResponseEntity.accepted().body(response);
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("hasAnyRole('CFO','CHIEF_ACCOUNTANT','AUDITOR')")
  @Operation(summary = "Get schedule run history", description = "Get run history for a schedule")
  public ResponseEntity<Page<ScheduleRunDTO>> getScheduleHistory(
      @PathVariable UUID id, Pageable pageable) {
    return ResponseEntity.ok(reportSchedulerService.getScheduleHistory(id, pageable));
  }

  @GetMapping("/report-center/upcoming")
  @PreAuthorize("hasAnyRole('CFO','CHIEF_ACCOUNTANT','AUDITOR')")
  @Operation(
      summary = "Get upcoming runs",
      description = "Get upcoming scheduled runs across all schedules")
  public ResponseEntity<List<ScheduleRunDTO>> getUpcomingRuns() {
    return ResponseEntity.ok(reportSchedulerService.getUpcomingRuns());
  }

  @GetMapping("/report-center/history")
  @PreAuthorize("hasAnyRole('CFO','CHIEF_ACCOUNTANT','AUDITOR')")
  @Operation(
      summary = "Get all run history",
      description = "Get historical runs across all schedules")
  public ResponseEntity<Page<ScheduleRunDTO>> getAllHistory(Pageable pageable) {
    return ResponseEntity.ok(reportSchedulerService.getAllRunHistory(pageable));
  }
}

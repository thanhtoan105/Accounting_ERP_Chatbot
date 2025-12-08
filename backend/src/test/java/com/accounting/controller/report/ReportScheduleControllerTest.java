package com.accounting.controller.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.accounting.dto.report.CreateReportScheduleRequest;
import com.accounting.dto.report.ReportScheduleDTO;
import com.accounting.dto.report.RunNowRequest;
import com.accounting.dto.report.RunNowResponse;
import com.accounting.dto.report.ScheduleRunDTO;
import com.accounting.dto.report.UpdateReportScheduleRequest;
import com.accounting.security.CompanyContext;
import com.accounting.service.ReportSchedulerService;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class ReportScheduleControllerTest {

  private static final Long COMPANY_ID = 1L;

  @Mock private ReportSchedulerService reportSchedulerService;

  @InjectMocks private ReportScheduleController controller;

  @BeforeEach
  void setUp() {
    CompanyContext.setCompanyId(COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Nested
  @DisplayName("GET /schedules - List Schedules")
  class GetSchedulesTests {

    @Test
    @DisplayName("Should return paginated list of schedules")
    void getSchedules_shouldReturnOk() {
      ReportScheduleDTO scheduleDTO = createTestScheduleDTO();
      Page<ReportScheduleDTO> page = new PageImpl<>(List.of(scheduleDTO), PageRequest.of(0, 20), 1);
      Pageable pageable = PageRequest.of(0, 20);

      when(reportSchedulerService.getSchedules(any(Pageable.class))).thenReturn(page);

      ResponseEntity<Page<ReportScheduleDTO>> response = controller.getSchedules(pageable);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent()).hasSize(1);
      assertThat(response.getBody().getContent().get(0).getName()).isEqualTo("Monthly B01 Report");
    }

    @Test
    @DisplayName("Should return empty page when no schedules exist")
    void getSchedules_empty_shouldReturnEmptyPage() {
      Page<ReportScheduleDTO> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
      Pageable pageable = PageRequest.of(0, 20);

      when(reportSchedulerService.getSchedules(any(Pageable.class))).thenReturn(emptyPage);

      ResponseEntity<Page<ReportScheduleDTO>> response = controller.getSchedules(pageable);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getContent()).isEmpty();
    }
  }

  @Nested
  @DisplayName("POST /schedules - Create Schedule")
  class CreateScheduleTests {

    @Test
    @DisplayName("Should create schedule and return 201")
    void createSchedule_shouldReturn201() {
      CreateReportScheduleRequest request = CreateReportScheduleRequest.builder()
          .name("Monthly B01 Report")
          .reportType("B01")
          .cronExpression("0 0 8 5 * ?")
          .periodRule("LAST_CLOSED")
          .exportFormats(List.of("PDF", "EXCEL"))
          .recipients(List.of("cfo@example.com"))
          .build();

      ReportScheduleDTO responseDTO = createTestScheduleDTO();
      when(reportSchedulerService.createSchedule(any(CreateReportScheduleRequest.class))).thenReturn(responseDTO);

      ResponseEntity<ReportScheduleDTO> response = controller.createSchedule(request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo("Monthly B01 Report");
      assertThat(response.getBody().getReportType()).isEqualTo("B01");
      assertThat(response.getBody().isActive()).isTrue();

      verify(reportSchedulerService).createSchedule(any(CreateReportScheduleRequest.class));
    }

    @Test
    @DisplayName("Should throw exception for invalid cron expression")
    void createSchedule_withInvalidCron_shouldThrowException() {
      CreateReportScheduleRequest request = CreateReportScheduleRequest.builder()
          .name("Test Schedule")
          .reportType("B01")
          .cronExpression("invalid-cron")
          .periodRule("CURRENT")
          .exportFormats(List.of("PDF"))
          .recipients(List.of("test@example.com"))
          .build();

      when(reportSchedulerService.createSchedule(any()))
          .thenThrow(new IllegalArgumentException("Invalid cron expression"));

      try {
        controller.createSchedule(request);
        assertThat(false).isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).contains("Invalid cron expression");
      }
    }

    @Test
    @DisplayName("Should throw exception for duplicate schedule name")
    void createSchedule_withDuplicateName_shouldThrowException() {
      CreateReportScheduleRequest request = CreateReportScheduleRequest.builder()
          .name("Existing Schedule")
          .reportType("B01")
          .cronExpression("0 0 8 1 * ?")
          .periodRule("CURRENT")
          .exportFormats(List.of("PDF"))
          .recipients(List.of("test@example.com"))
          .build();

      when(reportSchedulerService.createSchedule(any()))
          .thenThrow(new IllegalArgumentException("Schedule with name 'Existing Schedule' already exists"));

      try {
        controller.createSchedule(request);
        assertThat(false).isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).contains("already exists");
      }
    }
  }

  @Nested
  @DisplayName("GET /schedules/{id} - Get Schedule Details")
  class GetScheduleTests {

    @Test
    @DisplayName("Should return schedule details")
    void getSchedule_shouldReturnOk() {
      UUID scheduleId = UUID.randomUUID();
      ReportScheduleDTO scheduleDTO = createTestScheduleDTO();
      scheduleDTO.setId(scheduleId);

      when(reportSchedulerService.getSchedule(scheduleId)).thenReturn(scheduleDTO);

      ResponseEntity<ReportScheduleDTO> response = controller.getSchedule(scheduleId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getId()).isEqualTo(scheduleId);
      assertThat(response.getBody().getName()).isEqualTo("Monthly B01 Report");
    }

    @Test
    @DisplayName("Should throw exception for non-existent schedule")
    void getSchedule_nonExistent_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();

      when(reportSchedulerService.getSchedule(scheduleId))
          .thenThrow(new EntityNotFoundException("Schedule not found"));

      try {
        controller.getSchedule(scheduleId);
        assertThat(false).isTrue();
      } catch (EntityNotFoundException e) {
        assertThat(e.getMessage()).contains("Schedule not found");
      }
    }
  }

  @Nested
  @DisplayName("PUT /schedules/{id} - Update Schedule")
  class UpdateScheduleTests {

    @Test
    @DisplayName("Should update schedule and return OK")
    void updateSchedule_shouldReturnOk() {
      UUID scheduleId = UUID.randomUUID();
      UpdateReportScheduleRequest request = UpdateReportScheduleRequest.builder()
          .name("Updated Schedule Name")
          .cronExpression("0 0 7 1 * ?")
          .build();

      ReportScheduleDTO responseDTO = createTestScheduleDTO();
      responseDTO.setId(scheduleId);
      responseDTO.setName("Updated Schedule Name");

      when(reportSchedulerService.updateSchedule(eq(scheduleId), any(UpdateReportScheduleRequest.class)))
          .thenReturn(responseDTO);

      ResponseEntity<ReportScheduleDTO> response = controller.updateSchedule(scheduleId, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getName()).isEqualTo("Updated Schedule Name");
    }

    @Test
    @DisplayName("Should throw exception for non-existent schedule")
    void updateSchedule_nonExistent_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();
      UpdateReportScheduleRequest request = UpdateReportScheduleRequest.builder()
          .name("New Name")
          .build();

      when(reportSchedulerService.updateSchedule(eq(scheduleId), any()))
          .thenThrow(new EntityNotFoundException("Schedule not found"));

      try {
        controller.updateSchedule(scheduleId, request);
        assertThat(false).isTrue();
      } catch (EntityNotFoundException e) {
        assertThat(e.getMessage()).contains("Schedule not found");
      }
    }

    @Test
    @DisplayName("Should throw exception for invalid cron expression")
    void updateSchedule_withInvalidCron_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();
      UpdateReportScheduleRequest request = UpdateReportScheduleRequest.builder()
          .cronExpression("bad-cron")
          .build();

      when(reportSchedulerService.updateSchedule(eq(scheduleId), any()))
          .thenThrow(new IllegalArgumentException("Invalid cron expression"));

      try {
        controller.updateSchedule(scheduleId, request);
        assertThat(false).isTrue();
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage()).contains("Invalid cron expression");
      }
    }
  }

  @Nested
  @DisplayName("DELETE /schedules/{id} - Cancel Schedule")
  class CancelScheduleTests {

    @Test
    @DisplayName("Should cancel schedule and return OK")
    void cancelSchedule_shouldReturnOk() {
      UUID scheduleId = UUID.randomUUID();

      doNothing().when(reportSchedulerService).cancelSchedule(scheduleId);

      ResponseEntity<Map<String, Object>> response = controller.cancelSchedule(scheduleId);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().get("id")).isEqualTo(scheduleId);
      assertThat(response.getBody().get("isActive")).isEqualTo(false);
      assertThat(response.getBody().get("message")).isEqualTo("Schedule has been disabled");

      verify(reportSchedulerService).cancelSchedule(scheduleId);
    }

    @Test
    @DisplayName("Should throw exception for non-existent schedule")
    void cancelSchedule_nonExistent_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();

      doThrow(new EntityNotFoundException("Schedule not found"))
          .when(reportSchedulerService).cancelSchedule(scheduleId);

      try {
        controller.cancelSchedule(scheduleId);
        assertThat(false).isTrue();
      } catch (EntityNotFoundException e) {
        assertThat(e.getMessage()).contains("Schedule not found");
      }
    }
  }

  @Nested
  @DisplayName("POST /schedules/{id}/run-now - Run Now")
  class RunNowTests {

    @Test
    @DisplayName("Should trigger run-now and return 202")
    void runNow_shouldReturn202() {
      UUID scheduleId = UUID.randomUUID();
      UUID periodId = UUID.randomUUID();

      RunNowResponse expectedResponse = RunNowResponse.builder()
          .runId(UUID.randomUUID())
          .scheduleId(scheduleId)
          .status("PENDING")
          .periodId(periodId)
          .periodLabel("Tháng 11/2025")
          .queuedAt(Instant.now())
          .build();

      when(reportSchedulerService.runNow(eq(scheduleId), any(RunNowRequest.class))).thenReturn(expectedResponse);

      ResponseEntity<RunNowResponse> response = controller.runNow(scheduleId, new RunNowRequest());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
      assertThat(response.getBody().getPeriodLabel()).isEqualTo("Tháng 11/2025");
    }

    @Test
    @DisplayName("Should handle run-now with null request body")
    void runNow_withNullRequest_shouldWork() {
      UUID scheduleId = UUID.randomUUID();

      RunNowResponse expectedResponse = RunNowResponse.builder()
          .runId(UUID.randomUUID())
          .scheduleId(scheduleId)
          .status("PENDING")
          .periodId(UUID.randomUUID())
          .periodLabel("Test Period")
          .queuedAt(Instant.now())
          .build();

      when(reportSchedulerService.runNow(eq(scheduleId), any(RunNowRequest.class))).thenReturn(expectedResponse);

      ResponseEntity<RunNowResponse> response = controller.runNow(scheduleId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Should handle run-now with override period")
    void runNow_withOverridePeriod_shouldWork() {
      UUID scheduleId = UUID.randomUUID();
      UUID overridePeriodId = UUID.randomUUID();

      RunNowRequest request = new RunNowRequest();
      request.setOverridePeriodId(overridePeriodId);

      RunNowResponse expectedResponse = RunNowResponse.builder()
          .runId(UUID.randomUUID())
          .scheduleId(scheduleId)
          .status("PENDING")
          .periodId(overridePeriodId)
          .periodLabel("Override Period")
          .queuedAt(Instant.now())
          .build();

      when(reportSchedulerService.runNow(eq(scheduleId), any(RunNowRequest.class))).thenReturn(expectedResponse);

      ResponseEntity<RunNowResponse> response = controller.runNow(scheduleId, request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getBody().getPeriodId()).isEqualTo(overridePeriodId);
    }

    @Test
    @DisplayName("Should throw exception for duplicate run")
    void runNow_duplicateRun_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();

      when(reportSchedulerService.runNow(eq(scheduleId), any(RunNowRequest.class)))
          .thenThrow(new IllegalStateException("A run for this schedule and period already exists"));

      try {
        controller.runNow(scheduleId, new RunNowRequest());
        assertThat(false).isTrue();
      } catch (IllegalStateException e) {
        assertThat(e.getMessage()).contains("already exists");
      }
    }

    @Test
    @DisplayName("Should throw exception for non-existent schedule")
    void runNow_nonExistentSchedule_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();

      when(reportSchedulerService.runNow(eq(scheduleId), any(RunNowRequest.class)))
          .thenThrow(new EntityNotFoundException("Schedule not found"));

      try {
        controller.runNow(scheduleId, new RunNowRequest());
        assertThat(false).isTrue();
      } catch (EntityNotFoundException e) {
        assertThat(e.getMessage()).contains("Schedule not found");
      }
    }
  }

  @Nested
  @DisplayName("GET /schedules/{id}/history - Schedule History")
  class GetScheduleHistoryTests {

    @Test
    @DisplayName("Should return paginated run history")
    void getScheduleHistory_shouldReturnOk() {
      UUID scheduleId = UUID.randomUUID();
      Pageable pageable = PageRequest.of(0, 20);

      ScheduleRunDTO runDTO = ScheduleRunDTO.builder()
          .id(UUID.randomUUID())
          .scheduleId(scheduleId)
          .scheduleName("Test Schedule")
          .status("SUCCESS")
          .triggerType("SCHEDULED")
          .periodLabel("Tháng 11/2025")
          .queuedAt(Instant.now())
          .build();

      Page<ScheduleRunDTO> page = new PageImpl<>(List.of(runDTO), pageable, 1);
      when(reportSchedulerService.getScheduleHistory(eq(scheduleId), any(Pageable.class))).thenReturn(page);

      ResponseEntity<Page<ScheduleRunDTO>> response = controller.getScheduleHistory(scheduleId, pageable);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent()).hasSize(1);
      assertThat(response.getBody().getContent().get(0).getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should return empty page when no history exists")
    void getScheduleHistory_empty_shouldReturnEmptyPage() {
      UUID scheduleId = UUID.randomUUID();
      Pageable pageable = PageRequest.of(0, 20);

      Page<ScheduleRunDTO> emptyPage = new PageImpl<>(List.of(), pageable, 0);
      when(reportSchedulerService.getScheduleHistory(eq(scheduleId), any(Pageable.class))).thenReturn(emptyPage);

      ResponseEntity<Page<ScheduleRunDTO>> response = controller.getScheduleHistory(scheduleId, pageable);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getContent()).isEmpty();
    }

    @Test
    @DisplayName("Should throw exception for non-existent schedule")
    void getScheduleHistory_nonExistent_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();
      Pageable pageable = PageRequest.of(0, 20);

      when(reportSchedulerService.getScheduleHistory(eq(scheduleId), any(Pageable.class)))
          .thenThrow(new EntityNotFoundException("Schedule not found"));

      try {
        controller.getScheduleHistory(scheduleId, pageable);
        assertThat(false).isTrue();
      } catch (EntityNotFoundException e) {
        assertThat(e.getMessage()).contains("Schedule not found");
      }
    }
  }

  @Nested
  @DisplayName("Report Center Endpoints")
  class ReportCenterTests {

    @Test
    @DisplayName("Should return upcoming runs")
    void getUpcomingRuns_shouldReturnOk() {
      ScheduleRunDTO upcomingRun = ScheduleRunDTO.builder()
          .scheduleId(UUID.randomUUID())
          .scheduleName("Monthly Report")
          .status("UPCOMING")
          .triggerType("SCHEDULED")
          .queuedAt(Instant.now().plusSeconds(3600))
          .build();

      when(reportSchedulerService.getUpcomingRuns()).thenReturn(List.of(upcomingRun));

      ResponseEntity<List<ScheduleRunDTO>> response = controller.getUpcomingRuns();

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody()).hasSize(1);
      assertThat(response.getBody().get(0).getStatus()).isEqualTo("UPCOMING");
    }

    @Test
    @DisplayName("Should return empty list when no upcoming runs")
    void getUpcomingRuns_empty_shouldReturnEmptyList() {
      when(reportSchedulerService.getUpcomingRuns()).thenReturn(List.of());

      ResponseEntity<List<ScheduleRunDTO>> response = controller.getUpcomingRuns();

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("Should return all run history")
    void getAllHistory_shouldReturnOk() {
      Pageable pageable = PageRequest.of(0, 20);

      ScheduleRunDTO runDTO = ScheduleRunDTO.builder()
          .id(UUID.randomUUID())
          .scheduleId(UUID.randomUUID())
          .scheduleName("Test Schedule")
          .status("SUCCESS")
          .triggerType("MANUAL")
          .queuedAt(Instant.now())
          .build();

      Page<ScheduleRunDTO> page = new PageImpl<>(List.of(runDTO), pageable, 1);
      when(reportSchedulerService.getAllRunHistory(any(Pageable.class))).thenReturn(page);

      ResponseEntity<Page<ScheduleRunDTO>> response = controller.getAllHistory(pageable);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent()).hasSize(1);
    }
  }

  private ReportScheduleDTO createTestScheduleDTO() {
    return ReportScheduleDTO.builder()
        .id(UUID.randomUUID())
        .name("Monthly B01 Report")
        .reportType("B01")
        .cronExpression("0 0 8 5 * ?")
        .periodRule("LAST_CLOSED")
        .exportFormats(List.of("PDF", "EXCEL"))
        .recipients(List.of("cfo@example.com"))
        .recipientCount(1)
        .ownerId(100L)
        .ownerName("Test User")
        .isActive(true)
        .nextRunAt(Instant.now().plusSeconds(86400))
        .createdAt(Instant.now())
        .build();
  }
}

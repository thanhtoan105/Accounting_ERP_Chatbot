package com.accounting.service.impl.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.accounting.dto.report.CreateReportScheduleRequest;
import com.accounting.dto.report.ReportScheduleDTO;
import com.accounting.dto.report.RunNowRequest;
import com.accounting.dto.report.RunNowResponse;
import com.accounting.dto.report.UpdateReportScheduleRequest;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.User;
import com.accounting.entity.report.PeriodRule;
import com.accounting.entity.report.ReportSchedule;
import com.accounting.entity.report.ReportScheduleRun;
import com.accounting.entity.report.RunStatus;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.report.ReportScheduleRepository;
import com.accounting.repository.report.ReportScheduleRunRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.ReportDistributionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportSchedulerServiceImplTest {

  private static final Long COMPANY_ID = 1L;
  private static final Long USER_ID = 100L;

  @Mock private ReportScheduleRepository scheduleRepository;
  @Mock private ReportScheduleRunRepository runRepository;
  @Mock private AccountingPeriodRepository periodRepository;
  @Mock private UserRepository userRepository;
  @Mock private AuditService auditService;
  @Mock private ReportDistributionService reportDistributionService;

  @InjectMocks private ReportSchedulerServiceImpl service;

  @BeforeEach
  void setUp() {
    CompanyContext.setCompanyId(COMPANY_ID);
    setupSecurityContext();
    service = new ReportSchedulerServiceImpl(
        scheduleRepository, runRepository, periodRepository, userRepository, auditService, reportDistributionService, new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    SecurityContextHolder.clearContext();
  }

  private void setupSecurityContext() {
    SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    Authentication authentication = Mockito.mock(Authentication.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getPrincipal()).thenReturn(String.valueOf(USER_ID));
    SecurityContextHolder.setContext(securityContext);
  }

  @Nested
  @DisplayName("Create Schedule Tests")
  class CreateScheduleTests {

    @Test
    @DisplayName("createSchedule with valid request should create and return DTO")
    void createSchedule_withValidRequest_shouldCreateAndReturnDTO() {
      CreateReportScheduleRequest request = CreateReportScheduleRequest.builder()
          .name("Monthly B01 Report")
          .reportType("B01")
          .cronExpression("0 0 8 5 * ?")
          .periodRule("LAST_CLOSED")
          .exportFormats(List.of("PDF", "EXCEL"))
          .recipients(List.of("cfo@example.com"))
          .build();

      User owner = new User();
      owner.setId(USER_ID);
      owner.setFullName("Test User");

      when(scheduleRepository.existsByCompanyIdAndName(COMPANY_ID, request.getName())).thenReturn(false);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
      when(scheduleRepository.save(any(ReportSchedule.class))).thenAnswer(inv -> {
        ReportSchedule schedule = inv.getArgument(0);
        schedule.setId(UUID.randomUUID());
        schedule.setCreatedAt(Instant.now());
        return schedule;
      });

      ReportScheduleDTO result = service.createSchedule(request);

      assertThat(result.getName()).isEqualTo("Monthly B01 Report");
      assertThat(result.getReportType()).isEqualTo("B01");
      assertThat(result.getCronExpression()).isEqualTo("0 0 8 5 * ?");
      assertThat(result.getPeriodRule()).isEqualTo("LAST_CLOSED");
      assertThat(result.getExportFormats()).containsExactly("PDF", "EXCEL");
      assertThat(result.getRecipients()).containsExactly("cfo@example.com");
      assertThat(result.isActive()).isTrue();
      assertThat(result.getNextRunAt()).isNotNull();

      verify(scheduleRepository).save(any(ReportSchedule.class));
      verify(auditService).logReportScheduleEvent(eq("REPORT_SCHEDULE_CREATED"), any(), eq("Monthly B01 Report"));
    }

    @Test
    @DisplayName("createSchedule with invalid cron should throw exception")
    void createSchedule_withInvalidCron_shouldThrowException() {
      CreateReportScheduleRequest request = CreateReportScheduleRequest.builder()
          .name("Invalid Schedule")
          .reportType("B01")
          .cronExpression("invalid-cron")
          .periodRule("LAST_CLOSED")
          .exportFormats(List.of("PDF"))
          .recipients(List.of("user@example.com"))
          .build();

      assertThatThrownBy(() -> service.createSchedule(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid cron expression");

      verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSchedule with duplicate name should throw exception")
    void createSchedule_withDuplicateName_shouldThrowException() {
      CreateReportScheduleRequest request = CreateReportScheduleRequest.builder()
          .name("Existing Schedule")
          .reportType("B01")
          .cronExpression("0 0 8 1 * ?")
          .periodRule("CURRENT")
          .exportFormats(List.of("PDF"))
          .recipients(List.of("user@example.com"))
          .build();

      when(scheduleRepository.existsByCompanyIdAndName(COMPANY_ID, "Existing Schedule")).thenReturn(true);

      assertThatThrownBy(() -> service.createSchedule(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("createSchedule with SPECIFIC period rule should store periodId in parameters")
    void createSchedule_withSpecificPeriodRule_shouldStorePeriodId() {
      UUID periodId = UUID.randomUUID();
      CreateReportScheduleRequest request = CreateReportScheduleRequest.builder()
          .name("Specific Period Report")
          .reportType("B02")
          .cronExpression("0 0 9 1 * ?")
          .periodRule("SPECIFIC")
          .periodId(periodId)
          .exportFormats(List.of("EXCEL"))
          .recipients(List.of("accountant@example.com"))
          .build();

      User owner = new User();
      owner.setId(USER_ID);
      owner.setFullName("Test User");

      when(scheduleRepository.existsByCompanyIdAndName(COMPANY_ID, request.getName())).thenReturn(false);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));

      ArgumentCaptor<ReportSchedule> scheduleCaptor = ArgumentCaptor.forClass(ReportSchedule.class);
      when(scheduleRepository.save(scheduleCaptor.capture())).thenAnswer(inv -> {
        ReportSchedule schedule = inv.getArgument(0);
        schedule.setId(UUID.randomUUID());
        schedule.setCreatedAt(Instant.now());
        return schedule;
      });

      service.createSchedule(request);

      ReportSchedule captured = scheduleCaptor.getValue();
      assertThat(captured.getParameters()).contains(periodId.toString());
    }
  }

  @Nested
  @DisplayName("Update Schedule Tests")
  class UpdateScheduleTests {

    @Test
    @DisplayName("updateSchedule with valid request should update and return DTO")
    void updateSchedule_withValidRequest_shouldUpdateAndReturnDTO() {
      UUID scheduleId = UUID.randomUUID();
      ReportSchedule existingSchedule = createTestSchedule(scheduleId, "Old Name");

      UpdateReportScheduleRequest request = UpdateReportScheduleRequest.builder()
          .name("Updated Name")
          .cronExpression("0 0 7 1 * ?")
          .build();

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.of(existingSchedule));
      when(scheduleRepository.existsByCompanyIdAndName(COMPANY_ID, "Updated Name")).thenReturn(false);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
      when(scheduleRepository.save(any(ReportSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

      ReportScheduleDTO result = service.updateSchedule(scheduleId, request);

      assertThat(result.getName()).isEqualTo("Updated Name");
      assertThat(result.getCronExpression()).isEqualTo("0 0 7 1 * ?");
      verify(auditService).logReportScheduleEvent(eq("REPORT_SCHEDULE_UPDATED"), eq(scheduleId), eq("Updated Name"));
    }

    @Test
    @DisplayName("updateSchedule with non-existent schedule should throw EntityNotFoundException")
    void updateSchedule_withNonExistentSchedule_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();
      UpdateReportScheduleRequest request = UpdateReportScheduleRequest.builder()
          .name("New Name")
          .build();

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.updateSchedule(scheduleId, request))
          .isInstanceOf(EntityNotFoundException.class)
          .hasMessageContaining("Schedule not found");
    }

    @Test
    @DisplayName("updateSchedule with invalid cron should throw exception")
    void updateSchedule_withInvalidCron_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();
      ReportSchedule existingSchedule = createTestSchedule(scheduleId, "Test Schedule");

      UpdateReportScheduleRequest request = UpdateReportScheduleRequest.builder()
          .cronExpression("bad-cron-format")
          .build();

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.of(existingSchedule));

      assertThatThrownBy(() -> service.updateSchedule(scheduleId, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid cron expression");
    }
  }

  @Nested
  @DisplayName("Cancel Schedule Tests")
  class CancelScheduleTests {

    @Test
    @DisplayName("cancelSchedule should set inactive and log audit")
    void cancelSchedule_shouldSetInactiveAndLogAudit() {
      UUID scheduleId = UUID.randomUUID();
      ReportSchedule schedule = createTestSchedule(scheduleId, "Schedule to Cancel");
      schedule.setIsActive(true);

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.of(schedule));
      when(scheduleRepository.save(any(ReportSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

      service.cancelSchedule(scheduleId);

      ArgumentCaptor<ReportSchedule> captor = ArgumentCaptor.forClass(ReportSchedule.class);
      verify(scheduleRepository).save(captor.capture());
      assertThat(captor.getValue().getIsActive()).isFalse();
      verify(auditService).logReportScheduleEvent(eq("REPORT_SCHEDULE_CANCELLED"), eq(scheduleId), eq("Schedule to Cancel"));
    }

    @Test
    @DisplayName("cancelSchedule with non-existent schedule should throw exception")
    void cancelSchedule_withNonExistentSchedule_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.cancelSchedule(scheduleId))
          .isInstanceOf(EntityNotFoundException.class)
          .hasMessageContaining("Schedule not found");
    }
  }

  @Nested
  @DisplayName("Run Now Tests")
  class RunNowTests {

    @Test
    @DisplayName("runNow with valid schedule should create pending run")
    void runNow_withValidSchedule_shouldCreatePendingRun() {
      UUID scheduleId = UUID.randomUUID();
      UUID periodId = UUID.randomUUID();
      ReportSchedule schedule = createTestSchedule(scheduleId, "Test Schedule");

      AccountingPeriod period = new AccountingPeriod();
      period.setId(periodId);
      period.setPeriodName("Tháng 11/2025");

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.of(schedule));
      when(periodRepository.findByCompanyIdAndStatusOrderByStartDate(COMPANY_ID, PeriodStatus.CLOSED))
          .thenReturn(List.of(period));
      when(runRepository.findExistingRun(eq(COMPANY_ID), eq(scheduleId), eq(periodId), any())).thenReturn(Optional.empty());
      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, periodId)).thenReturn(Optional.of(period));
      when(runRepository.save(any(ReportScheduleRun.class))).thenAnswer(inv -> {
        ReportScheduleRun run = inv.getArgument(0);
        run.setId(UUID.randomUUID());
        return run;
      });

      RunNowResponse response = service.runNow(scheduleId, new RunNowRequest());

      assertThat(response.getScheduleId()).isEqualTo(scheduleId);
      assertThat(response.getStatus()).isEqualTo("PENDING");
      assertThat(response.getPeriodLabel()).isEqualTo("Tháng 11/2025");
      verify(runRepository).save(any(ReportScheduleRun.class));
    }

    @Test
    @DisplayName("runNow with existing success run should return skipped duplicate")
    void runNow_withExistingSuccessRun_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();
      UUID periodId = UUID.randomUUID();
      ReportSchedule schedule = createTestSchedule(scheduleId, "Test Schedule");

      AccountingPeriod period = new AccountingPeriod();
      period.setId(periodId);

      ReportScheduleRun existingRun = new ReportScheduleRun();
      existingRun.setId(UUID.randomUUID());
      existingRun.setStatus(RunStatus.SUCCESS);

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.of(schedule));
      when(periodRepository.findByCompanyIdAndStatusOrderByStartDate(COMPANY_ID, PeriodStatus.CLOSED))
          .thenReturn(List.of(period));
      when(runRepository.findExistingRun(eq(COMPANY_ID), eq(scheduleId), eq(periodId), any()))
          .thenReturn(Optional.of(existingRun));

      assertThatThrownBy(() -> service.runNow(scheduleId, new RunNowRequest()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already exists or is in progress");
    }

    @Test
    @DisplayName("runNow with override period should use provided period")
    void runNow_withOverridePeriod_shouldUseProvidedPeriod() {
      UUID scheduleId = UUID.randomUUID();
      UUID overridePeriodId = UUID.randomUUID();
      ReportSchedule schedule = createTestSchedule(scheduleId, "Test Schedule");

      AccountingPeriod period = new AccountingPeriod();
      period.setId(overridePeriodId);
      period.setPeriodName("Override Period");

      RunNowRequest request = new RunNowRequest();
      request.setOverridePeriodId(overridePeriodId);

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.of(schedule));
      when(runRepository.findExistingRun(eq(COMPANY_ID), eq(scheduleId), eq(overridePeriodId), any()))
          .thenReturn(Optional.empty());
      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, overridePeriodId)).thenReturn(Optional.of(period));
      when(runRepository.save(any(ReportScheduleRun.class))).thenAnswer(inv -> {
        ReportScheduleRun run = inv.getArgument(0);
        run.setId(UUID.randomUUID());
        return run;
      });

      RunNowResponse response = service.runNow(scheduleId, request);

      assertThat(response.getPeriodId()).isEqualTo(overridePeriodId);
      assertThat(response.getPeriodLabel()).isEqualTo("Override Period");
    }
  }

  @Nested
  @DisplayName("Execute Due Schedules Tests")
  class ExecuteDueSchedulesTests {

    @Test
    @DisplayName("executeDueSchedules should process only due schedules")
    void executeDueSchedules_shouldProcessOnlyDueSchedules() {
      UUID scheduleId = UUID.randomUUID();
      UUID periodId = UUID.randomUUID();
      ReportSchedule dueSchedule = createTestSchedule(scheduleId, "Due Schedule");
      dueSchedule.setNextRunAt(Instant.now().minusSeconds(60));

      AccountingPeriod period = new AccountingPeriod();
      period.setId(periodId);
      period.setPeriodName("Test Period");

      when(scheduleRepository.findDueSchedules(eq(COMPANY_ID), any(Instant.class)))
          .thenReturn(List.of(dueSchedule));
      when(periodRepository.findByCompanyIdAndStatusOrderByStartDate(COMPANY_ID, PeriodStatus.CLOSED))
          .thenReturn(List.of(period));
      when(runRepository.findExistingRun(eq(COMPANY_ID), eq(scheduleId), eq(periodId), any()))
          .thenReturn(Optional.empty());
      when(periodRepository.findByCompanyIdAndId(COMPANY_ID, periodId)).thenReturn(Optional.of(period));
      when(runRepository.save(any(ReportScheduleRun.class))).thenAnswer(inv -> {
        ReportScheduleRun run = inv.getArgument(0);
        run.setId(UUID.randomUUID());
        return run;
      });
      when(scheduleRepository.save(any(ReportSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

      service.executeDueSchedules();

      verify(runRepository).save(any(ReportScheduleRun.class));
      verify(scheduleRepository).save(any(ReportSchedule.class));
    }

    @Test
    @DisplayName("executeDueSchedules without CompanyContext should not process")
    void executeDueSchedules_withoutCompanyContext_shouldNotProcess() {
      CompanyContext.clear();

      service.executeDueSchedules();

      verify(scheduleRepository, never()).findDueSchedules(any(), any());
    }

    @Test
    @DisplayName("executeDueSchedules should skip duplicate runs")
    void executeDueSchedules_shouldSkipDuplicateRuns() {
      UUID scheduleId = UUID.randomUUID();
      UUID periodId = UUID.randomUUID();
      ReportSchedule dueSchedule = createTestSchedule(scheduleId, "Due Schedule");
      dueSchedule.setNextRunAt(Instant.now().minusSeconds(60));

      AccountingPeriod period = new AccountingPeriod();
      period.setId(periodId);

      ReportScheduleRun existingRun = new ReportScheduleRun();
      existingRun.setStatus(RunStatus.SUCCESS);

      when(scheduleRepository.findDueSchedules(eq(COMPANY_ID), any(Instant.class)))
          .thenReturn(List.of(dueSchedule));
      when(periodRepository.findByCompanyIdAndStatusOrderByStartDate(COMPANY_ID, PeriodStatus.CLOSED))
          .thenReturn(List.of(period));
      when(runRepository.findExistingRun(eq(COMPANY_ID), eq(scheduleId), eq(periodId), any()))
          .thenReturn(Optional.of(existingRun));

      service.executeDueSchedules();

      verify(runRepository, never()).save(any(ReportScheduleRun.class));
    }
  }

  @Nested
  @DisplayName("Get Schedules Tests")
  class GetSchedulesTests {

    @Test
    @DisplayName("getSchedules should return paginated results")
    void getSchedules_shouldReturnPaginatedResults() {
      UUID scheduleId = UUID.randomUUID();
      ReportSchedule schedule = createTestSchedule(scheduleId, "Test Schedule");
      Page<ReportSchedule> page = new PageImpl<>(List.of(schedule));
      Pageable pageable = PageRequest.of(0, 10);

      when(scheduleRepository.findByCompanyId(COMPANY_ID, pageable)).thenReturn(page);
      when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));

      Page<ReportScheduleDTO> result = service.getSchedules(pageable);

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).getName()).isEqualTo("Test Schedule");
    }
  }

  @Nested
  @DisplayName("Get Schedule History Tests")
  class GetScheduleHistoryTests {

    @Test
    @DisplayName("getScheduleHistory should return run history")
    void getScheduleHistory_shouldReturnRunHistory() {
      UUID scheduleId = UUID.randomUUID();
      ReportSchedule schedule = createTestSchedule(scheduleId, "Test Schedule");
      
      ReportScheduleRun run = new ReportScheduleRun();
      run.setId(UUID.randomUUID());
      run.setScheduleId(scheduleId);
      run.setCompanyId(COMPANY_ID);
      run.setStatus(RunStatus.SUCCESS);
      run.setTriggerType(com.accounting.entity.report.TriggerType.SCHEDULED);
      run.setPeriodLabel("Test Period");
      run.setQueuedAt(Instant.now());

      Page<ReportScheduleRun> runPage = new PageImpl<>(List.of(run));
      Pageable pageable = PageRequest.of(0, 10);

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.of(schedule));
      when(runRepository.findByScheduleIdAndCompanyIdOrderByQueuedAtDesc(scheduleId, COMPANY_ID, pageable))
          .thenReturn(runPage);
      when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

      var result = service.getScheduleHistory(scheduleId, pageable);

      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("getScheduleHistory with non-existent schedule should throw exception")
    void getScheduleHistory_withNonExistentSchedule_shouldThrowException() {
      UUID scheduleId = UUID.randomUUID();
      Pageable pageable = PageRequest.of(0, 10);

      when(scheduleRepository.findByIdAndCompanyId(scheduleId, COMPANY_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getScheduleHistory(scheduleId, pageable))
          .isInstanceOf(EntityNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Idempotency Tests")
  class IdempotencyTests {

    @Test
    @DisplayName("canCreateRun should return true when no existing run")
    void canCreateRun_withNoExistingRun_shouldReturnTrue() {
      UUID scheduleId = UUID.randomUUID();
      UUID periodId = UUID.randomUUID();

      when(runRepository.findExistingRun(eq(COMPANY_ID), eq(scheduleId), eq(periodId), any()))
          .thenReturn(Optional.empty());

      boolean result = service.canCreateRun(scheduleId, periodId);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canCreateRun should return false when existing run exists")
    void canCreateRun_withExistingRun_shouldReturnFalse() {
      UUID scheduleId = UUID.randomUUID();
      UUID periodId = UUID.randomUUID();

      ReportScheduleRun existingRun = new ReportScheduleRun();
      existingRun.setStatus(RunStatus.SUCCESS);

      when(runRepository.findExistingRun(eq(COMPANY_ID), eq(scheduleId), eq(periodId), any()))
          .thenReturn(Optional.of(existingRun));

      boolean result = service.canCreateRun(scheduleId, periodId);

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("Company Context Validation Tests")
  class CompanyContextTests {

    @Test
    @DisplayName("createSchedule without CompanyContext should throw exception")
    void createSchedule_withoutCompanyContext_shouldThrowException() {
      CompanyContext.clear();

      CreateReportScheduleRequest request = CreateReportScheduleRequest.builder()
          .name("Test")
          .reportType("B01")
          .cronExpression("0 0 8 1 * ?")
          .periodRule("CURRENT")
          .exportFormats(List.of("PDF"))
          .recipients(List.of("test@example.com"))
          .build();

      assertThatThrownBy(() -> service.createSchedule(request))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("CompanyContext is not set");
    }
  }

  private ReportSchedule createTestSchedule(UUID id, String name) {
    ReportSchedule schedule = new ReportSchedule();
    schedule.setId(id);
    schedule.setCompanyId(COMPANY_ID);
    schedule.setName(name);
    schedule.setReportType("B01");
    schedule.setCronExpression("0 0 8 1 * ?");
    schedule.setPeriodRule(PeriodRule.LAST_CLOSED);
    schedule.setExportFormats(List.of("PDF"));
    schedule.setRecipients(List.of("test@example.com"));
    schedule.setOwnerId(USER_ID);
    schedule.setIsActive(true);
    schedule.setCreatedAt(Instant.now());
    schedule.setNextRunAt(Instant.now().plusSeconds(86400));
    return schedule;
  }
}

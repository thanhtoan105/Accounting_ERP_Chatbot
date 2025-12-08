package com.accounting.service.impl.report;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.report.CreateReportScheduleRequest;
import com.accounting.dto.report.ReportScheduleDTO;
import com.accounting.dto.report.RunNowRequest;
import com.accounting.dto.report.RunNowResponse;
import com.accounting.dto.report.ScheduleRunDTO;
import com.accounting.dto.report.UpdateReportScheduleRequest;
import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;
import com.accounting.entity.User;
import com.accounting.entity.report.PeriodRule;
import com.accounting.entity.report.ReportSchedule;
import com.accounting.entity.report.ReportScheduleRun;
import com.accounting.entity.report.RunStatus;
import com.accounting.entity.report.TriggerType;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.report.ReportScheduleRepository;
import com.accounting.repository.report.ReportScheduleRunRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.ReportDistributionService;
import com.accounting.service.ReportSchedulerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional
public class ReportSchedulerServiceImpl implements ReportSchedulerService {

  private static final Logger logger = LoggerFactory.getLogger(ReportSchedulerServiceImpl.class);

  private final ReportScheduleRepository scheduleRepository;
  private final ReportScheduleRunRepository runRepository;
  private final AccountingPeriodRepository periodRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final ReportDistributionService reportDistributionService;
  private final ObjectMapper objectMapper;

  public ReportSchedulerServiceImpl(
      ReportScheduleRepository scheduleRepository,
      ReportScheduleRunRepository runRepository,
      AccountingPeriodRepository periodRepository,
      UserRepository userRepository,
      AuditService auditService,
      ReportDistributionService reportDistributionService,
      ObjectMapper objectMapper) {
    this.scheduleRepository = scheduleRepository;
    this.runRepository = runRepository;
    this.periodRepository = periodRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.reportDistributionService = reportDistributionService;
    this.objectMapper = objectMapper;
  }

  @Override
  public ReportScheduleDTO createSchedule(CreateReportScheduleRequest request) {
    Long companyId = requireCompanyContext();
    Long currentUserId = SecurityUtils.getCurrentUserId();

    validateCronExpression(request.getCronExpression());

    if (scheduleRepository.existsByCompanyIdAndName(companyId, request.getName())) {
      throw new IllegalArgumentException("Schedule with name '" + request.getName() + "' already exists");
    }

    ReportSchedule schedule = new ReportSchedule();
    schedule.setCompanyId(companyId);
    schedule.setName(request.getName());
    schedule.setReportType(request.getReportType());
    schedule.setCronExpression(request.getCronExpression());
    schedule.setPeriodRule(PeriodRule.valueOf(request.getPeriodRule()));
    schedule.setExportFormats(request.getExportFormats());
    schedule.setRecipients(request.getRecipients());
    schedule.setOwnerId(currentUserId);
    schedule.setIsActive(true);

    if (request.getPeriodId() != null) {
      Map<String, Object> params = new HashMap<>();
      params.put("periodId", request.getPeriodId().toString());
      schedule.setParameters(toJson(params));
    }

    schedule.setNextRunAt(calculateNextRunAt(request.getCronExpression()));

    ReportSchedule saved = scheduleRepository.save(schedule);

    logScheduleAudit("REPORT_SCHEDULE_CREATED", saved.getId(), saved.getName());

    return toDTO(saved);
  }

  @Override
  public ReportScheduleDTO updateSchedule(UUID id, UpdateReportScheduleRequest request) {
    Long companyId = requireCompanyContext();

    ReportSchedule schedule = scheduleRepository.findByIdAndCompanyId(id, companyId)
        .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + id));

    if (request.getName() != null && !request.getName().equals(schedule.getName())) {
      if (scheduleRepository.existsByCompanyIdAndName(companyId, request.getName())) {
        throw new IllegalArgumentException("Schedule with name '" + request.getName() + "' already exists");
      }
      schedule.setName(request.getName());
    }

    if (request.getReportType() != null) {
      schedule.setReportType(request.getReportType());
    }

    if (request.getCronExpression() != null) {
      validateCronExpression(request.getCronExpression());
      schedule.setCronExpression(request.getCronExpression());
      schedule.setNextRunAt(calculateNextRunAt(request.getCronExpression()));
    }

    if (request.getPeriodRule() != null) {
      schedule.setPeriodRule(PeriodRule.valueOf(request.getPeriodRule()));
    }

    if (request.getPeriodId() != null) {
      Map<String, Object> params = new HashMap<>();
      params.put("periodId", request.getPeriodId().toString());
      schedule.setParameters(toJson(params));
    }

    if (request.getExportFormats() != null) {
      schedule.setExportFormats(request.getExportFormats());
    }

    if (request.getRecipients() != null) {
      schedule.setRecipients(request.getRecipients());
    }

    if (request.getIsActive() != null) {
      schedule.setIsActive(request.getIsActive());
    }

    ReportSchedule saved = scheduleRepository.save(schedule);

    logScheduleAudit("REPORT_SCHEDULE_UPDATED", saved.getId(), saved.getName());

    return toDTO(saved);
  }

  @Override
  public void cancelSchedule(UUID id) {
    Long companyId = requireCompanyContext();

    ReportSchedule schedule = scheduleRepository.findByIdAndCompanyId(id, companyId)
        .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + id));

    schedule.setIsActive(false);
    scheduleRepository.save(schedule);

    logScheduleAudit("REPORT_SCHEDULE_CANCELLED", schedule.getId(), schedule.getName());
  }

  @Override
  @Transactional(readOnly = true)
  public ReportScheduleDTO getSchedule(UUID id) {
    Long companyId = requireCompanyContext();

    ReportSchedule schedule = scheduleRepository.findByIdAndCompanyId(id, companyId)
        .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + id));

    return toDTO(schedule);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ReportScheduleDTO> getSchedules(Pageable pageable) {
    Long companyId = requireCompanyContext();
    return scheduleRepository.findByCompanyId(companyId, pageable).map(this::toDTO);
  }

  @Override
  public RunNowResponse runNow(UUID scheduleId, RunNowRequest request) {
    Long companyId = requireCompanyContext();
    Long currentUserId = SecurityUtils.getCurrentUserId();

    ReportSchedule schedule = scheduleRepository.findByIdAndCompanyId(scheduleId, companyId)
        .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + scheduleId));

    UUID periodId = request != null && request.getOverridePeriodId() != null
        ? request.getOverridePeriodId()
        : resolvePeriodId(schedule.getPeriodRule(), schedule.getParameters());

    if (!canCreateRun(scheduleId, periodId)) {
      throw new IllegalStateException("A run for this schedule and period already exists or is in progress");
    }

    AccountingPeriod period = periodRepository.findByCompanyIdAndId(companyId, periodId)
        .orElseThrow(() -> new EntityNotFoundException("Period not found: " + periodId));

    ReportScheduleRun run = new ReportScheduleRun();
    run.setCompanyId(companyId);
    run.setScheduleId(scheduleId);
    run.setPeriodId(periodId);
    run.setPeriodLabel(period.getPeriodName());
    run.setStatus(RunStatus.PENDING);
    run.setTriggerType(TriggerType.MANUAL);
    run.setTriggeredById(currentUserId);
    run.setQueuedAt(Instant.now());

    ReportScheduleRun saved = runRepository.save(run);

    logRunAudit("REPORT_SCHEDULE_RUN_QUEUED", saved.getId(), schedule.getName(), TriggerType.MANUAL);

    return RunNowResponse.builder()
        .runId(saved.getId())
        .scheduleId(scheduleId)
        .status(saved.getStatus().name())
        .periodId(periodId)
        .periodLabel(period.getPeriodName())
        .queuedAt(saved.getQueuedAt())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ScheduleRunDTO> getScheduleHistory(UUID scheduleId, Pageable pageable) {
    Long companyId = requireCompanyContext();

    if (!scheduleRepository.findByIdAndCompanyId(scheduleId, companyId).isPresent()) {
      throw new EntityNotFoundException("Schedule not found: " + scheduleId);
    }

    return runRepository.findByScheduleIdAndCompanyIdOrderByQueuedAtDesc(scheduleId, companyId, pageable)
        .map(this::toRunDTO);
  }

  @Override
  public void executeDueSchedules() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      logger.warn("Cannot execute due schedules: CompanyContext not set");
      return;
    }

    Instant now = Instant.now();
    List<ReportSchedule> dueSchedules = scheduleRepository.findDueSchedules(companyId, now);

    for (ReportSchedule schedule : dueSchedules) {
      try {
        executeSingleSchedule(schedule);
      } catch (Exception e) {
        logger.error("Failed to execute schedule {}: {}", schedule.getId(), e.getMessage(), e);
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean canCreateRun(UUID scheduleId, UUID periodId) {
    Long companyId = requireCompanyContext();

    Optional<ReportScheduleRun> existingRun = runRepository.findExistingRun(
        companyId,
        scheduleId,
        periodId,
        List.of(RunStatus.PENDING, RunStatus.RUNNING, RunStatus.SUCCESS));

    return existingRun.isEmpty();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ScheduleRunDTO> getUpcomingRuns() {
    Long companyId = requireCompanyContext();
    Instant now = Instant.now();
    List<ReportSchedule> activeSchedules = scheduleRepository.findByCompanyIdAndIsActiveTrue(companyId);

    return activeSchedules.stream()
        .filter(s -> s.getNextRunAt() != null && s.getNextRunAt().isAfter(now))
        .sorted((a, b) -> a.getNextRunAt().compareTo(b.getNextRunAt()))
        .limit(20)
        .map(schedule -> ScheduleRunDTO.builder()
            .scheduleId(schedule.getId())
            .scheduleName(schedule.getName())
            .status("UPCOMING")
            .triggerType(TriggerType.SCHEDULED.name())
            .queuedAt(schedule.getNextRunAt())
            .build())
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ScheduleRunDTO> getAllRunHistory(Pageable pageable) {
    Long companyId = requireCompanyContext();
    return runRepository.findByCompanyIdOrderByQueuedAtDesc(companyId, pageable)
        .map(this::toRunDTO);
  }

  private void executeSingleSchedule(ReportSchedule schedule) {
    Long companyId = schedule.getCompanyId();

    UUID periodId = resolvePeriodId(schedule.getPeriodRule(), schedule.getParameters());
    if (periodId == null) {
      logger.warn("Cannot resolve period for schedule {}", schedule.getId());
      createFailedRun(schedule, null, "PERIOD_RESOLUTION_FAILED", "Cannot resolve period for schedule");
      return;
    }

    if (!canCreateRunInternal(companyId, schedule.getId(), periodId)) {
      logger.info("Skipping duplicate run for schedule {} period {}", schedule.getId(), periodId);
      return;
    }

    AccountingPeriod period = periodRepository.findByCompanyIdAndId(companyId, periodId).orElse(null);
    if (period == null) {
      logger.warn("Period not found for schedule {}: {}", schedule.getId(), periodId);
      createFailedRun(schedule, periodId, "PERIOD_NOT_FOUND", "Period not found: " + periodId);
      return;
    }

    ReportScheduleRun run = new ReportScheduleRun();
    run.setCompanyId(companyId);
    run.setScheduleId(schedule.getId());
    run.setPeriodId(periodId);
    run.setPeriodLabel(period.getPeriodName());
    run.setStatus(RunStatus.PENDING);
    run.setTriggerType(TriggerType.SCHEDULED);
    run.setQueuedAt(Instant.now());

    runRepository.save(run);

    schedule.setLastRunAt(Instant.now());
    schedule.setNextRunAt(calculateNextRunAt(schedule.getCronExpression()));
    scheduleRepository.save(schedule);

    logRunAudit("REPORT_SCHEDULE_RUN_QUEUED", run.getId(), schedule.getName(), TriggerType.SCHEDULED);
  }

  private void createFailedRun(ReportSchedule schedule, UUID periodId, String errorCode, String errorMessage) {
    ReportScheduleRun run = new ReportScheduleRun();
    run.setCompanyId(schedule.getCompanyId());
    run.setScheduleId(schedule.getId());
    run.setPeriodId(periodId);
    run.setPeriodLabel(periodId != null ? "Period " + periodId : "N/A");
    run.setStatus(RunStatus.FAILED);
    run.setTriggerType(TriggerType.SCHEDULED);
    run.setQueuedAt(Instant.now());
    run.setStartedAt(Instant.now());
    run.setFinishedAt(Instant.now());
    run.setErrorCode(errorCode);
    run.setErrorMessage(errorMessage);

    runRepository.save(run);

    try {
      reportDistributionService.sendFailureNotification(schedule, run);
    } catch (Exception e) {
      logger.error("Failed to send failure notification for schedule {}", schedule.getId(), e);
    }

    logRunAudit("REPORT_SCHEDULE_RUN_FAILED", run.getId(), schedule.getName(), TriggerType.SCHEDULED);
  }

  private boolean canCreateRunInternal(Long companyId, UUID scheduleId, UUID periodId) {
    Optional<ReportScheduleRun> existingRun = runRepository.findExistingRun(
        companyId,
        scheduleId,
        periodId,
        List.of(RunStatus.PENDING, RunStatus.RUNNING, RunStatus.SUCCESS));

    return existingRun.isEmpty();
  }

  private UUID resolvePeriodId(PeriodRule rule, String parameters) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("CompanyContext is not set");
    }

    return switch (rule) {
      case LAST_CLOSED -> {
        List<AccountingPeriod> closedPeriods = periodRepository.findByCompanyIdAndStatusOrderByStartDate(
            companyId, PeriodStatus.CLOSED);
        yield closedPeriods.isEmpty() ? null : closedPeriods.get(closedPeriods.size() - 1).getId();
      }
      case CURRENT -> {
        Optional<AccountingPeriod> current = periodRepository.findCurrentPeriodByCompanyId(companyId);
        yield current.map(AccountingPeriod::getId).orElse(null);
      }
      case SPECIFIC -> parseParametersPeriodId(parameters);
    };
  }

  private UUID parseParametersPeriodId(String parameters) {
    if (parameters == null || parameters.isBlank()) {
      return null;
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> params = objectMapper.readValue(parameters, Map.class);
      Object periodIdObj = params.get("periodId");
      if (periodIdObj instanceof String) {
        return UUID.fromString((String) periodIdObj);
      }
      return null;
    } catch (Exception e) {
      logger.warn("Failed to parse parameters for periodId: {}", e.getMessage());
      return null;
    }
  }

  private void validateCronExpression(String cronExpression) {
    try {
      CronExpression.parse(cronExpression);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid cron expression: " + cronExpression, e);
    }
  }

  private Instant calculateNextRunAt(String cronExpression) {
    try {
      CronExpression cron = CronExpression.parse(cronExpression);
      ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
      ZonedDateTime next = cron.next(now);
      return next != null ? next.toInstant() : null;
    } catch (Exception e) {
      logger.warn("Failed to calculate next run time for cron '{}': {}", cronExpression, e.getMessage());
      return null;
    }
  }

  private Long requireCompanyContext() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new IllegalStateException("CompanyContext is not set");
    }
    return companyId;
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      logger.warn("Failed to serialize to JSON: {}", e.getMessage());
      return null;
    }
  }

  private ReportScheduleDTO toDTO(ReportSchedule schedule) {
    String ownerName = null;
    if (schedule.getOwnerId() != null) {
      ownerName = userRepository.findById(schedule.getOwnerId())
          .map(User::getFullName)
          .orElse(null);
    }

    return ReportScheduleDTO.builder()
        .id(schedule.getId())
        .name(schedule.getName())
        .reportType(schedule.getReportType())
        .cronExpression(schedule.getCronExpression())
        .periodRule(schedule.getPeriodRule().name())
        .exportFormats(schedule.getExportFormats())
        .recipients(schedule.getRecipients())
        .recipientCount(schedule.getRecipients() != null ? schedule.getRecipients().size() : 0)
        .ownerId(schedule.getOwnerId())
        .ownerName(ownerName)
        .isActive(schedule.getIsActive())
        .lastRunAt(schedule.getLastRunAt())
        .nextRunAt(schedule.getNextRunAt())
        .createdAt(schedule.getCreatedAt())
        .build();
  }

  private ScheduleRunDTO toRunDTO(ReportScheduleRun run) {
    String triggeredByName = null;
    if (run.getTriggeredById() != null) {
      triggeredByName = userRepository.findById(run.getTriggeredById())
          .map(User::getFullName)
          .orElse(null);
    }

    String scheduleName = null;
    if (run.getScheduleId() != null) {
      scheduleName = scheduleRepository.findById(run.getScheduleId())
          .map(ReportSchedule::getName)
          .orElse(null);
    }

    return ScheduleRunDTO.builder()
        .id(run.getId())
        .scheduleId(run.getScheduleId())
        .scheduleName(scheduleName)
        .periodId(run.getPeriodId())
        .periodLabel(run.getPeriodLabel())
        .status(run.getStatus().name())
        .triggerType(run.getTriggerType().name())
        .triggeredById(run.getTriggeredById())
        .triggeredByName(triggeredByName)
        .snapshotId(run.getSnapshotId())
        .rerunOfRunId(run.getRerunOfRunId())
        .attempt(run.getAttempt())
        .queuedAt(run.getQueuedAt())
        .startedAt(run.getStartedAt())
        .finishedAt(run.getFinishedAt())
        .durationMs(run.getDurationMs())
        .errorCode(run.getErrorCode())
        .errorMessage(run.getErrorMessage())
        .slaDeadline(run.getSlaDeadline())
        .exceededSla(run.getExceededSla() != null && run.getExceededSla())
        .build();
  }

  private void logScheduleAudit(String action, UUID scheduleId, String scheduleName) {
    try {
      auditService.logReportScheduleEvent(action, scheduleId, scheduleName);
    } catch (Exception e) {
      logger.warn("Failed to log audit event for schedule {}: {}", scheduleId, e.getMessage());
    }
  }

  private void logRunAudit(String action, UUID runId, String scheduleName, TriggerType triggerType) {
    try {
      auditService.logReportScheduleRunEvent(action, runId, scheduleName, triggerType.name());
    } catch (Exception e) {
      logger.warn("Failed to log audit event for run {}: {}", runId, e.getMessage());
    }
  }
}

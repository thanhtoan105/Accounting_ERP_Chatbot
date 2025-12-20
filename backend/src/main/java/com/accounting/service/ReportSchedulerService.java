package com.accounting.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.report.CreateReportScheduleRequest;
import com.accounting.dto.report.ReportScheduleDTO;
import com.accounting.dto.report.RunNowRequest;
import com.accounting.dto.report.RunNowResponse;
import com.accounting.dto.report.ScheduleRunDTO;
import com.accounting.dto.report.UpdateReportScheduleRequest;

public interface ReportSchedulerService {

  ReportScheduleDTO createSchedule(CreateReportScheduleRequest request);

  ReportScheduleDTO updateSchedule(UUID id, UpdateReportScheduleRequest request);

  void cancelSchedule(UUID id);

  ReportScheduleDTO getSchedule(UUID id);

  Page<ReportScheduleDTO> getSchedules(Pageable pageable);

  RunNowResponse runNow(UUID scheduleId, RunNowRequest request);

  Page<ScheduleRunDTO> getScheduleHistory(UUID scheduleId, Pageable pageable);

  List<ScheduleRunDTO> getUpcomingRuns();

  Page<ScheduleRunDTO> getAllRunHistory(Pageable pageable);

  void executeDueSchedules();

  boolean canCreateRun(UUID scheduleId, UUID periodId);
}

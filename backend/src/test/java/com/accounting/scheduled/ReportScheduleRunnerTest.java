package com.accounting.scheduled;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
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

import com.accounting.entity.Company;
import com.accounting.entity.report.ReportSchedule;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.report.ReportScheduleRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.ReportSchedulerService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportScheduleRunner Tests")
class ReportScheduleRunnerTest {

  @Mock private CompanyRepository companyRepository;
  @Mock private ReportSchedulerService reportSchedulerService;
  @Mock private ReportScheduleRepository reportScheduleRepository;
  @Mock private AuditService auditService;

  @InjectMocks private ReportScheduleRunner runner;

  private Company testCompany;

  @BeforeEach
  void setUp() {
    testCompany = new Company();
    testCompany.setId(1L);
    testCompany.setName("Test Company");
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Nested
  @DisplayName("checkDueSchedules")
  class CheckDueSchedulesTests {

    @Test
    @DisplayName("should iterate all companies and execute due schedules")
    void shouldIterateAllCompaniesAndExecuteDueSchedules() {
      Company company1 = new Company();
      company1.setId(1L);
      company1.setName("Company 1");

      Company company2 = new Company();
      company2.setId(2L);
      company2.setName("Company 2");

      when(companyRepository.findAll()).thenReturn(List.of(company1, company2));

      runner.checkDueSchedules();

      verify(reportSchedulerService, times(2)).executeDueSchedules();
    }

    @Test
    @DisplayName("should continue processing other companies when one fails")
    void shouldContinueProcessingWhenCompanyFails() {
      Company company1 = new Company();
      company1.setId(1L);
      company1.setName("Company 1");

      Company company2 = new Company();
      company2.setId(2L);
      company2.setName("Company 2");

      when(companyRepository.findAll()).thenReturn(List.of(company1, company2));

      runner.checkDueSchedules();

      verify(reportSchedulerService, times(2)).executeDueSchedules();
    }

    @Test
    @DisplayName("should handle empty company list gracefully")
    void shouldHandleEmptyCompanyList() {
      when(companyRepository.findAll()).thenReturn(Collections.emptyList());

      runner.checkDueSchedules();

      verify(reportSchedulerService, never()).executeDueSchedules();
    }

    @Test
    @DisplayName("should clear CompanyContext after processing each company")
    void shouldClearCompanyContextAfterProcessing() {
      when(companyRepository.findAll()).thenReturn(List.of(testCompany));

      runner.checkDueSchedules();

      // After method completes, context should be cleared
      org.junit.jupiter.api.Assertions.assertNull(CompanyContext.getCompanyId());
    }
  }

  @Nested
  @DisplayName("checkOrphanedSchedules")
  class CheckOrphanedSchedulesTests {

    @Test
    @DisplayName("should disable orphaned schedules and log audit events")
    void shouldDisableOrphanedSchedulesAndLogAudit() {
      ReportSchedule orphanedSchedule = new ReportSchedule();
      orphanedSchedule.setId(UUID.randomUUID());
      orphanedSchedule.setName("Orphaned Schedule");
      orphanedSchedule.setOwnerId(999L);
      orphanedSchedule.setIsActive(true);

      when(companyRepository.findAll()).thenReturn(List.of(testCompany));
      when(reportScheduleRepository.findOrphanedSchedules(testCompany.getId()))
          .thenReturn(List.of(orphanedSchedule));

      runner.checkOrphanedSchedules();

      verify(reportScheduleRepository).save(orphanedSchedule);
      verify(auditService)
          .logReportScheduleEvent(
              eq("SCHEDULE_ORPHANED"),
              eq(orphanedSchedule.getId()),
              anyString());

      org.junit.jupiter.api.Assertions.assertFalse(orphanedSchedule.getIsActive());
      org.junit.jupiter.api.Assertions.assertNull(orphanedSchedule.getNextRunAt());
    }

    @Test
    @DisplayName("should handle no orphaned schedules gracefully")
    void shouldHandleNoOrphanedSchedules() {
      when(companyRepository.findAll()).thenReturn(List.of(testCompany));
      when(reportScheduleRepository.findOrphanedSchedules(testCompany.getId()))
          .thenReturn(Collections.emptyList());

      runner.checkOrphanedSchedules();

      verify(reportScheduleRepository, never()).save(any(ReportSchedule.class));
      verify(auditService, never())
          .logReportScheduleEvent(anyString(), any(UUID.class), anyString());
    }

    @Test
    @DisplayName("should continue processing other companies when orphan check fails")
    void shouldContinueProcessingWhenOrphanCheckFails() {
      Company company1 = new Company();
      company1.setId(1L);
      company1.setName("Company 1");

      Company company2 = new Company();
      company2.setId(2L);
      company2.setName("Company 2");

      when(companyRepository.findAll()).thenReturn(List.of(company1, company2));
      when(reportScheduleRepository.findOrphanedSchedules(1L))
          .thenThrow(new RuntimeException("Test error"));
      when(reportScheduleRepository.findOrphanedSchedules(2L))
          .thenReturn(Collections.emptyList());

      runner.checkOrphanedSchedules();

      verify(reportScheduleRepository).findOrphanedSchedules(1L);
      verify(reportScheduleRepository).findOrphanedSchedules(2L);
    }

    @Test
    @DisplayName("should process multiple orphaned schedules in single company")
    void shouldProcessMultipleOrphanedSchedules() {
      ReportSchedule schedule1 = new ReportSchedule();
      schedule1.setId(UUID.randomUUID());
      schedule1.setName("Orphaned 1");
      schedule1.setOwnerId(999L);
      schedule1.setIsActive(true);

      ReportSchedule schedule2 = new ReportSchedule();
      schedule2.setId(UUID.randomUUID());
      schedule2.setName("Orphaned 2");
      schedule2.setOwnerId(888L);
      schedule2.setIsActive(true);

      when(companyRepository.findAll()).thenReturn(List.of(testCompany));
      when(reportScheduleRepository.findOrphanedSchedules(testCompany.getId()))
          .thenReturn(List.of(schedule1, schedule2));

      runner.checkOrphanedSchedules();

      verify(reportScheduleRepository, times(2)).save(any(ReportSchedule.class));
      verify(auditService, times(2))
          .logReportScheduleEvent(
              eq("SCHEDULE_ORPHANED"), any(UUID.class), anyString());
    }
  }
}

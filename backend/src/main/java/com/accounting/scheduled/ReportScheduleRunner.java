package com.accounting.scheduled;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accounting.entity.Company;
import com.accounting.entity.report.ReportSchedule;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.report.ReportScheduleRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.ReportSchedulerService;

/**
 * Scheduled job that executes due report schedules.
 *
 * <p>Scheduled jobs:
 * <ul>
 *   <li>Due schedule check: Every 5 minutes</li>
 *   <li>Orphaned schedule check: Daily at 1 AM</li>
 * </ul>
 *
 * @see com.accounting.service.ReportSchedulerService
 */
@Component
public class ReportScheduleRunner {

  private static final Logger logger = LoggerFactory.getLogger(ReportScheduleRunner.class);

  private final CompanyRepository companyRepository;
  private final ReportSchedulerService reportSchedulerService;
  private final ReportScheduleRepository reportScheduleRepository;
  private final AuditService auditService;

  public ReportScheduleRunner(
      CompanyRepository companyRepository,
      ReportSchedulerService reportSchedulerService,
      ReportScheduleRepository reportScheduleRepository,
      AuditService auditService) {
    this.companyRepository = companyRepository;
    this.reportSchedulerService = reportSchedulerService;
    this.reportScheduleRepository = reportScheduleRepository;
    this.auditService = auditService;
  }

  /**
   * Check for due schedules every 5 minutes. Pattern: Iterate all companies, set CompanyContext,
   * execute due schedules.
   */
  @Scheduled(cron = "${reporting.schedule.check.cron:0 */5 * * * *}")
  public void checkDueSchedules() {
    logger.info("Starting scheduled report check");

    List<Company> companies = companyRepository.findAll();
    int totalProcessed = 0;
    int totalFailed = 0;

    for (Company company : companies) {
      try {
        CompanyContext.setCompanyId(company.getId());

        logger.debug(
            "Checking due schedules for company: {} ({})", company.getName(), company.getId());

        reportSchedulerService.executeDueSchedules();

        totalProcessed++;

      } catch (Exception e) {
        totalFailed++;
        logger.error("Error processing schedules for company: {}", company.getId(), e);
      } finally {
        CompanyContext.clear();
      }
    }

    logger.info(
        "Completed scheduled report check: {} companies processed, {} failed",
        totalProcessed,
        totalFailed);
  }

  /** Daily orphaned schedule check at 1 AM. Disables schedules whose owners are inactive. */
  @Scheduled(cron = "${reporting.orphan.check.cron:0 0 1 * * *}")
  public void checkOrphanedSchedules() {
    logger.info("Starting orphaned schedule check");

    List<Company> companies = companyRepository.findAll();
    int orphansDisabled = 0;

    for (Company company : companies) {
      try {
        CompanyContext.setCompanyId(company.getId());

        List<ReportSchedule> orphanedSchedules = 
            reportScheduleRepository.findOrphanedSchedules(company.getId());

        for (ReportSchedule schedule : orphanedSchedules) {
          schedule.setIsActive(false);
          schedule.setNextRunAt(null);
          reportScheduleRepository.save(schedule);

          auditService.logReportScheduleEvent(
              "SCHEDULE_ORPHANED",
              schedule.getId(),
              schedule.getName() + " - owner inactive");

          orphansDisabled++;
          logger.info(
              "Disabled orphaned schedule: {} (owner: {})",
              schedule.getName(),
              schedule.getOwnerId());
        }

      } catch (Exception e) {
        logger.error(
            "Error checking orphaned schedules for company: {}", company.getId(), e);
      } finally {
        CompanyContext.clear();
      }
    }

    logger.info("Orphaned schedule check completed: {} schedules disabled", orphansDisabled);
  }
}

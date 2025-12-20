package com.accounting.scheduled;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accounting.dto.audit.IntegrityCheckResultDTO;
import com.accounting.dto.audit.IntegrityIssueDTO;
import com.accounting.entity.Company;
import com.accounting.repository.CompanyRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.IntegrityCheckService;

/**
 * Scheduler for Cash & Bank audit operations.
 *
 * <p>Scheduled jobs:
 * <ul>
 *   <li>Daily integrity check: 2 AM daily</li>
 *   <li>Hourly anomaly detection: Every hour</li>
 *   <li>Weekly backup export: 3 AM Sunday (handled by ComplianceExportService)</li>
 * </ul>
 *
 * @see com.accounting.service.IntegrityCheckService
 */
@Component
public class CashBankAuditScheduler {

  private static final Logger logger = LoggerFactory.getLogger(CashBankAuditScheduler.class);

  private final CompanyRepository companyRepository;
  private final IntegrityCheckService integrityCheckService;

  public CashBankAuditScheduler(
      CompanyRepository companyRepository,
      IntegrityCheckService integrityCheckService) {
    this.companyRepository = companyRepository;
    this.integrityCheckService = integrityCheckService;
  }

  /**
   * Run daily integrity check at 2 AM.
   *
   * <p>Performs:
   * <ul>
   *   <li>Dr/Cr parity verification</li>
   *   <li>Duplicate reference detection</li>
   *   <li>Sequence gap detection</li>
   *   <li>Unusual amount detection</li>
   * </ul>
   */
  @Scheduled(cron = "0 0 2 * * *")
  public void runDailyIntegrityCheck() {
    logger.info("Starting scheduled daily integrity check");

    List<Company> companies = companyRepository.findAll();
    int successCount = 0;
    int failureCount = 0;

    for (Company company : companies) {
      try {
        CompanyContext.setCompanyId(company.getId());
        logger.info("Running daily integrity check for company: {} ({})",
            company.getName(), company.getId());

        IntegrityCheckResultDTO result = integrityCheckService.runDailyIntegrityCheck(
            company.getId());

        if (result.isPassed()) {
          successCount++;
          logger.info("Daily integrity check PASSED for company {}", company.getId());
        } else {
          failureCount++;
          logger.warn("Daily integrity check FAILED for company {} - {} issues found",
              company.getId(), result.getIssueCount());
          // FIXME(story-6-6): Integrate with AuditAlertService for failed integrity checks (AC6.6-06)
        }
      } catch (Exception e) {
        failureCount++;
        logger.error("Daily integrity check ERROR for company {}", company.getId(), e);
      } finally {
        CompanyContext.clear();
      }
    }

    logger.info("Completed scheduled daily integrity check: {} passed, {} failed",
        successCount, failureCount);
  }

  /**
   * Check for repeated blocked attempts every hour.
   *
   * <p>Alert threshold: ≥3 blocked attempts per user per hour
   */
  @Scheduled(cron = "0 0 * * * *")
  public void checkHourlyBlockedAttempts() {
    logger.info("Starting hourly blocked attempts check");

    List<Company> companies = companyRepository.findAll();
    int alertCount = 0;

    for (Company company : companies) {
      try {
        CompanyContext.setCompanyId(company.getId());

        List<IntegrityIssueDTO> issues = integrityCheckService.checkRepeatedBlockedAttempts(
            company.getId());

        if (!issues.isEmpty()) {
          alertCount += issues.size();
          logger.warn("Company {} has {} users with repeated blocked attempts",
              company.getId(), issues.size());
          // FIXME(story-6-6): Integrate with AuditAlertService for repeated blocked attempts (AC6.6-06)
          for (IntegrityIssueDTO issue : issues) {
            logger.warn("User {} has {} blocked attempts in the last hour",
                issue.getEntityId(), issue.getDetails());
          }
        }
      } catch (Exception e) {
        logger.error("Hourly blocked attempts check ERROR for company {}", company.getId(), e);
      } finally {
        CompanyContext.clear();
      }
    }

    logger.info("Completed hourly blocked attempts check: {} alerts generated", alertCount);
  }

  /**
   * Run weekly backup export at 3 AM Sunday.
   *
   * <p>Exports:
   * <ul>
   *   <li>Bank accounts master data</li>
   *   <li>Cash book entries</li>
   *   <li>Reconciliation sessions</li>
   *   <li>Audit logs</li>
   * </ul>
   *
   * <p>Note: Actual export handled by ComplianceExportService when implemented.
   */
  @Scheduled(cron = "0 0 3 * * SUN")
  public void runWeeklyBackupExport() {
    logger.info("Starting scheduled weekly backup export");

    List<Company> companies = companyRepository.findAll();

    for (Company company : companies) {
      try {
        CompanyContext.setCompanyId(company.getId());
        logger.info("Processing weekly backup for company: {} ({})",
            company.getName(), company.getId());

        // FIXME(story-6-6): Implement ComplianceExportService.createWeeklyBackup() (AC6.6-02)
        // Should export: bank accounts, cash book entries, reconciliation sessions, audit logs
        logger.info("Weekly backup export placeholder for company {}", company.getId());

      } catch (Exception e) {
        logger.error("Weekly backup export ERROR for company {}", company.getId(), e);
      } finally {
        CompanyContext.clear();
      }
    }

    logger.info("Completed scheduled weekly backup export");
  }
}

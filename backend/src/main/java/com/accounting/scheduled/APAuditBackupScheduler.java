package com.accounting.scheduled;

import com.accounting.entity.Company;
import com.accounting.repository.CompanyRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.APAuditBackupService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class APAuditBackupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(APAuditBackupScheduler.class);

    private final CompanyRepository companyRepository;
    private final APAuditBackupService backupService;

    public APAuditBackupScheduler(
            CompanyRepository companyRepository,
            APAuditBackupService backupService) {
        this.companyRepository = companyRepository;
        this.backupService = backupService;
    }

    /**
     * Run weekly backup every Sunday at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void scheduleWeeklyBackup() {
        logger.info("Starting scheduled weekly AP audit backup");
        
        List<Company> companies = companyRepository.findAll();
        for (Company company : companies) {
            try {
                CompanyContext.setCompanyId(company.getId());
                logger.info("Processing backup for company: {}", company.getName());
                backupService.createBackup(company.getId());
            } catch (Exception e) {
                logger.error("Failed to create scheduled backup for company {}", company.getId(), e);
            } finally {
                CompanyContext.clear();
            }
        }
        
        logger.info("Completed scheduled weekly AP audit backup");
    }
}

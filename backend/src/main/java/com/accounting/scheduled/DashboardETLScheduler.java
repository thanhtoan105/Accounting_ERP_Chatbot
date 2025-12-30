package com.accounting.scheduled;

import java.time.LocalTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accounting.entity.Company;
import com.accounting.entity.dashboard.DashboardETLRun;
import com.accounting.entity.dashboard.ETLJobStatus;
import com.accounting.repository.CompanyRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.analytics.ETLPipelineService;

@Component
public class DashboardETLScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DashboardETLScheduler.class);

    private final CompanyRepository companyRepository;
    private final ETLPipelineService etlPipelineService;

    @Value("${dashboard.etl.enabled:true}")
    private boolean etlEnabled;

    @Value("${dashboard.etl.business-hours.start:8}")
    private int businessHoursStart;

    @Value("${dashboard.etl.business-hours.end:18}")
    private int businessHoursEnd;

    @Value("${dashboard.etl.business-hours.enabled:false}")
    private boolean businessHoursRestriction;

    public DashboardETLScheduler(
            CompanyRepository companyRepository,
            ETLPipelineService etlPipelineService) {
        this.companyRepository = companyRepository;
        this.etlPipelineService = etlPipelineService;
    }

    @Scheduled(cron = "${dashboard.etl.cron:0 */5 * * * *}")
    public void refreshDashboardData() {
        if (!etlEnabled) {
            logger.debug("Dashboard ETL is disabled");
            return;
        }

        if (businessHoursRestriction && !isWithinBusinessHours()) {
            logger.debug("Outside business hours, skipping ETL refresh");
            return;
        }

        logger.info("Starting scheduled dashboard ETL refresh");

        List<Company> companies = companyRepository.findAll();
        String instanceId = getInstanceId();

        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        for (Company company : companies) {
            try {
                if (!etlPipelineService.acquireLock(company.getId(), instanceId)) {
                    logger.debug("Company {} ETL already in progress, skipping", company.getId());
                    skippedCount++;
                    continue;
                }

                try {
                    // Set company context for scheduled job (no HTTP request context)
                    CompanyContext.setCompanyId(company.getId());
                    
                    DashboardETLRun result = etlPipelineService.refreshMaterializedViews(company.getId());

                    if (result.getStatus() == ETLJobStatus.COMPLETED) {
                        successCount++;
                    } else {
                        failedCount++;
                    }
                } finally {
                    CompanyContext.clear();
                    etlPipelineService.releaseLock(company.getId(), instanceId);
                }

            } catch (Exception e) {
                logger.error("ETL refresh failed for company {}: {}", company.getId(), e.getMessage());
                failedCount++;
            }
        }

        logger.info("Dashboard ETL refresh completed: {} success, {} failed, {} skipped",
                successCount, failedCount, skippedCount);
    }

    private boolean isWithinBusinessHours() {
        LocalTime now = LocalTime.now();
        return now.getHour() >= businessHoursStart && now.getHour() < businessHoursEnd;
    }

    private String getInstanceId() {
        return System.getenv().getOrDefault("HOSTNAME", "local-" + ProcessHandle.current().pid());
    }
}

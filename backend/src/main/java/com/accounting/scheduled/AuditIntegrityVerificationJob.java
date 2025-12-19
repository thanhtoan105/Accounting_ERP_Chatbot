package com.accounting.scheduled;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accounting.entity.Company;
import com.accounting.repository.CompanyRepository;
import com.accounting.service.AuditAlertService;
import com.accounting.service.audit.AuditHashChainService;
import com.accounting.service.audit.dto.AuditChainVerificationResult;

@Component
public class AuditIntegrityVerificationJob {

    private static final Logger logger = LoggerFactory.getLogger(AuditIntegrityVerificationJob.class);
    private static final String LOCK_KEY_PREFIX = "audit-integrity:";
    private static final int LOCK_TTL_SECONDS = 300;

    private final CompanyRepository companyRepository;
    private final AuditHashChainService auditHashChainService;
    private final AuditAlertService auditAlertService;
    private final StringRedisTemplate redisTemplate;

    @Value("${audit.integrity.daily.enabled:true}")
    private boolean enabled;

    @Value("${audit.integrity.daily.days-back:1}")
    private int daysBack;

    public AuditIntegrityVerificationJob(
            CompanyRepository companyRepository,
            AuditHashChainService auditHashChainService,
            AuditAlertService auditAlertService,
            StringRedisTemplate redisTemplate) {
        this.companyRepository = companyRepository;
        this.auditHashChainService = auditHashChainService;
        this.auditAlertService = auditAlertService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(cron = "${audit.integrity.daily.cron:0 30 2 * * *}")
    public void verifyDailyChains() {
        if (!enabled) {
            logger.debug("Audit integrity verification is disabled");
            return;
        }

        LocalDate dateToVerify = LocalDate.now().minusDays(daysBack);
        logger.info("Starting daily audit chain verification for date {}", dateToVerify);

        List<Company> companies = companyRepository.findAll();
        String instanceId = getInstanceId();

        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        for (Company company : companies) {
            String lockKey = buildLockKey(company.getId(), dateToVerify);

            try {
                if (!acquireLock(lockKey, instanceId)) {
                    logger.debug("Company {} verification already in progress, skipping", company.getId());
                    skippedCount++;
                    continue;
                }

                try {
                    AuditChainVerificationResult result = auditHashChainService.verifyDailyChain(
                            company.getId(), dateToVerify);

                    if (result.isVerified()) {
                        successCount++;
                        logger.debug("Company {} chain verified successfully for date {}",
                                company.getId(), dateToVerify);

                        auditHashChainService.recomputeAndStoreDailyCheckpoint(
                                company.getId(), dateToVerify);
                    } else {
                        failedCount++;
                        logger.warn("Company {} chain verification FAILED for date {}: {}",
                                company.getId(), dateToVerify, result.getMismatchReason());
                        auditAlertService.notifyHashMismatch(result);
                    }
                } finally {
                    releaseLock(lockKey, instanceId);
                }
            } catch (Exception e) {
                errorCount++;
                logger.error("Verification error for company {} on date {}: {}",
                        company.getId(), dateToVerify, e.getMessage(), e);
                auditAlertService.notifyVerificationError(company.getId(), dateToVerify, e);
            }
        }

        logger.info("Daily audit chain verification completed for date {}: {} success, {} failed, {} skipped, {} errors",
                dateToVerify, successCount, failedCount, skippedCount, errorCount);
    }

    private String buildLockKey(Long companyId, LocalDate date) {
        return LOCK_KEY_PREFIX + companyId + ":" + date;
    }

    private boolean acquireLock(String lockKey, String instanceId) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, instanceId, Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (Boolean.TRUE.equals(acquired)) {
            logger.debug("Acquired lock {} by instance {}", lockKey, instanceId);
            return true;
        }

        String currentHolder = redisTemplate.opsForValue().get(lockKey);
        logger.debug("Lock {} held by {}", lockKey, currentHolder);
        return false;
    }

    private void releaseLock(String lockKey, String instanceId) {
        String currentHolder = redisTemplate.opsForValue().get(lockKey);

        if (instanceId.equals(currentHolder)) {
            redisTemplate.delete(lockKey);
            logger.debug("Released lock {} by instance {}", lockKey, instanceId);
        }
    }

    private String getInstanceId() {
        return System.getenv().getOrDefault("HOSTNAME", "local-" + ProcessHandle.current().pid());
    }
}

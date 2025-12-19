package com.accounting.service.dashboard;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.accounting.entity.dashboard.DashboardFreshness;
import com.accounting.entity.dashboard.FreshnessLevel;
import com.accounting.repository.dashboard.DashboardFreshnessRepository;
import com.accounting.service.dashboard.dto.FreshnessStatus;

@Service
public class DashboardFreshnessServiceImpl implements DashboardFreshnessService {

    private static final long GREEN_THRESHOLD_MINUTES = 5;
    private static final long YELLOW_THRESHOLD_MINUTES = 30;

    private final DashboardFreshnessRepository freshnessRepository;

    public DashboardFreshnessServiceImpl(DashboardFreshnessRepository freshnessRepository) {
        this.freshnessRepository = freshnessRepository;
    }

    @Override
    public FreshnessStatus getFreshness(Long companyId) {
        Optional<DashboardFreshness> freshnessOpt = freshnessRepository.findByCompanyId(companyId);

        if (freshnessOpt.isEmpty()) {
            return FreshnessStatus.noData();
        }

        DashboardFreshness freshness = freshnessOpt.get();
        FreshnessLevel level = calculateFreshnessLevel(freshness.getLastSuccessfulRefresh());

        return FreshnessStatus.of(
                level,
                freshness.getLastSuccessfulRefresh(),
                freshness.getConsecutiveFailures(),
                null);
    }

    @Override
    public FreshnessStatus getFreshnessForDataset(Long companyId, String datasetType) {
        Optional<DashboardFreshness> freshnessOpt = freshnessRepository.findByCompanyId(companyId);

        if (freshnessOpt.isEmpty()) {
            return FreshnessStatus.noDataForDataset(datasetType);
        }

        DashboardFreshness freshness = freshnessOpt.get();
        FreshnessLevel level = calculateFreshnessLevel(freshness.getLastSuccessfulRefresh());

        return FreshnessStatus.of(
                level,
                freshness.getLastSuccessfulRefresh(),
                freshness.getConsecutiveFailures(),
                datasetType);
    }

    @Override
    public Duration getTimeSinceLastRefresh(Long companyId) {
        Optional<DashboardFreshness> freshnessOpt = freshnessRepository.findByCompanyId(companyId);

        if (freshnessOpt.isEmpty() || freshnessOpt.get().getLastSuccessfulRefresh() == null) {
            return null;
        }

        return Duration.between(freshnessOpt.get().getLastSuccessfulRefresh(), Instant.now());
    }

    private FreshnessLevel calculateFreshnessLevel(Instant lastSuccessfulRefresh) {
        if (lastSuccessfulRefresh == null) {
            return FreshnessLevel.RED;
        }

        long minutesSinceRefresh = Duration.between(lastSuccessfulRefresh, Instant.now()).toMinutes();

        if (minutesSinceRefresh < GREEN_THRESHOLD_MINUTES) {
            return FreshnessLevel.GREEN;
        } else if (minutesSinceRefresh <= YELLOW_THRESHOLD_MINUTES) {
            return FreshnessLevel.YELLOW;
        } else {
            return FreshnessLevel.RED;
        }
    }
}

package com.accounting.controller.dashboard;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.entity.dashboard.ETLJobStatus;
import com.accounting.integration.metabase.MetabaseApiClient;
import com.accounting.repository.dashboard.DashboardETLRunRepository;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardHealthController {

    private static final Logger log = LoggerFactory.getLogger(DashboardHealthController.class);

    private final RedisConnectionFactory redisConnectionFactory;
    private final MetabaseApiClient metabaseApiClient;
    private final DashboardETLRunRepository etlRunRepository;

    public DashboardHealthController(
            RedisConnectionFactory redisConnectionFactory,
            MetabaseApiClient metabaseApiClient,
            DashboardETLRunRepository etlRunRepository) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.metabaseApiClient = metabaseApiClient;
        this.etlRunRepository = etlRunRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<DashboardHealthResponse> getDashboardHealth() {
        ComponentHealth metabaseHealth = checkMetabase();
        ComponentHealth redisHealth = checkRedis();
        ComponentHealth etlHealth = checkETL();

        String overallStatus = determineOverallStatus(metabaseHealth, redisHealth, etlHealth);

        Map<String, ComponentHealth> components = new LinkedHashMap<>();
        components.put("metabase", metabaseHealth);
        components.put("redis", redisHealth);
        components.put("etl", etlHealth);

        return ResponseEntity.ok(new DashboardHealthResponse(overallStatus, components));
    }

    private ComponentHealth checkMetabase() {
        try {
            metabaseApiClient.getDatabases();
            return new ComponentHealth("UP", null);
        } catch (Exception e) {
            log.warn("Metabase health check failed: {}", e.getMessage());
            return new ComponentHealth("DOWN", e.getMessage());
        }
    }

    private ComponentHealth checkRedis() {
        try {
            redisConnectionFactory.getConnection().ping();
            return new ComponentHealth("UP", null);
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return new ComponentHealth("DOWN", e.getMessage());
        }
    }

    private ComponentHealth checkETL() {
        try {
            Instant staleThreshold = Instant.now().minus(1, ChronoUnit.HOURS);
            var staleJobs = etlRunRepository.findStaleRunningJobs(staleThreshold);

            if (!staleJobs.isEmpty()) {
                return new ComponentHealth("DEGRADED", "Found " + staleJobs.size() + " stale running ETL jobs");
            }

            Instant recentWindow = Instant.now().minus(24, ChronoUnit.HOURS);
            var recentRuns = etlRunRepository.findByCompanyIdSince(null, recentWindow);
            long failedCount = recentRuns.stream()
                    .filter(run -> run.getStatus() == ETLJobStatus.FAILED)
                    .count();

            if (failedCount > 10) {
                return new ComponentHealth("DEGRADED", "High ETL failure rate: " + failedCount + " failures in last 24h");
            }

            return new ComponentHealth("UP", null);
        } catch (Exception e) {
            log.warn("ETL health check failed: {}", e.getMessage());
            return new ComponentHealth("DOWN", e.getMessage());
        }
    }

    private String determineOverallStatus(ComponentHealth... components) {
        boolean hasDown = false;
        boolean hasDegraded = false;

        for (ComponentHealth component : components) {
            if ("DOWN".equals(component.status())) {
                hasDown = true;
            } else if ("DEGRADED".equals(component.status())) {
                hasDegraded = true;
            }
        }

        if (hasDown) {
            return "DEGRADED";
        }
        if (hasDegraded) {
            return "DEGRADED";
        }
        return "UP";
    }

    public record DashboardHealthResponse(
            String status,
            Map<String, ComponentHealth> components) {}

    public record ComponentHealth(
            String status,
            String error) {}
}

package com.accounting.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.accounting.entity.dashboard.DashboardETLRun;
import com.accounting.entity.dashboard.DashboardFreshness;
import com.accounting.entity.dashboard.ETLJobStatus;
import com.accounting.entity.dashboard.ETLTriggerType;
import com.accounting.entity.dashboard.FreshnessLevel;
import com.accounting.repository.AccountingPeriodRepository;
import com.accounting.repository.dashboard.DashboardETLRunRepository;
import com.accounting.repository.dashboard.DashboardFreshnessRepository;
import com.accounting.service.analytics.ETLPipelineService.IntegrityCheckResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ETLPipelineServiceImplTest {

    private static final Long COMPANY_ID = 42L;
    private static final Long USER_ID = 100L;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private DashboardETLRunRepository etlRunRepository;

    @Mock
    private DashboardFreshnessRepository freshnessRepository;

    @Mock
    private AccountingPeriodRepository periodRepository;

    @Mock
    private AnalyticsCacheService analyticsCacheService;

    @Mock
    private ETLAlertService etlAlertService;

    @Mock
    private MaterializedViewRefreshService materializedViewRefreshService;

    @Mock
    private DashboardReconciliationService reconciliationService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AnalyticsCacheKeyGenerator cacheKeyGenerator;

    private ETLPipelineServiceImpl service;

    @BeforeEach
    void setUp() {
        when(cacheKeyGenerator.etlLock(any(Long.class))).thenAnswer(inv ->
            "etl:lock:company:" + inv.getArgument(0));
        
        service = new ETLPipelineServiceImpl(
                jdbcTemplate,
                redisTemplate,
                etlRunRepository,
                freshnessRepository,
                periodRepository,
                analyticsCacheService,
                etlAlertService,
                materializedViewRefreshService,
                reconciliationService,
                objectMapper,
                cacheKeyGenerator);
        ReflectionTestUtils.setField(service, "lockTtlSeconds", 300);
    }

    private void setupSuccessfulRefreshMocks() {
        when(etlRunRepository.save(any(DashboardETLRun.class))).thenAnswer(inv -> {
            DashboardETLRun run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            return run;
        });
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq(COMPANY_ID))).thenReturn(true);
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(COMPANY_ID))).thenReturn(0);
        when(materializedViewRefreshService.refreshAllMaterializedViews(COMPANY_ID)).thenReturn(500);
        when(freshnessRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(new DashboardFreshness()));
    }

    @Nested
    @DisplayName("refreshMaterializedViews tests")
    class RefreshMaterializedViewsTests {

        @Test
        @DisplayName("should execute all 5 REFRESH MATERIALIZED VIEW statements")
        void refreshMaterializedViews_success_executesAllRefreshStatements() {
            setupSuccessfulRefreshMocks();

            service.refreshMaterializedViews(COMPANY_ID);

            verify(materializedViewRefreshService).refreshAllMaterializedViews(COMPANY_ID);
        }

        @Test
        @DisplayName("should record DashboardETLRun entity with correct status")
        void refreshMaterializedViews_success_recordsETLRun() {
            setupSuccessfulRefreshMocks();

            DashboardETLRun result = service.refreshMaterializedViews(COMPANY_ID);

            assertThat(result.getStatus()).isEqualTo(ETLJobStatus.COMPLETED);
            assertThat(result.getCompanyId()).isEqualTo(COMPANY_ID);
            assertThat(result.getJobName()).isEqualTo("DASHBOARD_MV_REFRESH");
            assertThat(result.getRowsProcessed()).isEqualTo(500);
            verify(etlRunRepository, atLeastOnce()).save(any(DashboardETLRun.class));
        }
    }

    @Nested
    @DisplayName("refreshMaterializedViewsManual tests")
    class RefreshMaterializedViewsManualTests {

        @Test
        @DisplayName("should set triggerType to MANUAL")
        void refreshMaterializedViewsManual_setsManualTriggerType() {
            setupSuccessfulRefreshMocks();
            ArgumentCaptor<DashboardETLRun> captor = ArgumentCaptor.forClass(DashboardETLRun.class);

            service.refreshMaterializedViewsManual(COMPANY_ID, USER_ID);

            verify(etlRunRepository, atLeastOnce()).save(captor.capture());
            DashboardETLRun createdRun = captor.getAllValues().get(0);
            assertThat(createdRun.getTriggeredBy()).isEqualTo(ETLTriggerType.MANUAL);
            assertThat(createdRun.getTriggeredByUserId()).isEqualTo(USER_ID);
        }
    }

    @Nested
    @DisplayName("runIntegrityChecks tests")
    class RunIntegrityChecksTests {

        @Test
        @DisplayName("should return success when debit equals credit")
        void runIntegrityChecks_balanced_returnsSuccess() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(COMPANY_ID))).thenReturn(true);
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(COMPANY_ID))).thenReturn(0);

            IntegrityCheckResult result = service.runIntegrityChecks(COMPANY_ID);

            assertThat(result.passed()).isTrue();
            assertThat(result.debitCreditBalanced()).isTrue();
            assertThat(result.orphanCount()).isZero();
        }

        @Test
        @DisplayName("should return failure when debit/credit imbalanced")
        void runIntegrityChecks_imbalanced_returnsFailure() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(COMPANY_ID))).thenReturn(false);

            IntegrityCheckResult result = service.runIntegrityChecks(COMPANY_ID);

            assertThat(result.passed()).isFalse();
            assertThat(result.errorMessage()).isEqualTo("Debit/Credit imbalance detected");
        }

        @Test
        @DisplayName("should return warning when orphan entries exist")
        void runIntegrityChecks_orphans_returnsWarning() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(COMPANY_ID))).thenReturn(true);
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(COMPANY_ID))).thenReturn(5);

            IntegrityCheckResult result = service.runIntegrityChecks(COMPANY_ID);

            assertThat(result.passed()).isFalse();
            assertThat(result.orphanCount()).isEqualTo(5);
            assertThat(result.errorMessage()).contains("orphaned");
        }
    }

    @Nested
    @DisplayName("MaterializedViewRefreshService integration tests")
    class MaterializedViewRefreshServiceTests {

        @Test
        @DisplayName("should succeed when materialized view refresh succeeds")
        void refreshMaterializedViews_success_completesSuccessfully() {
            when(etlRunRepository.save(any(DashboardETLRun.class))).thenAnswer(inv -> {
                DashboardETLRun run = inv.getArgument(0);
                if (run.getId() == null) {
                    run.setId(UUID.randomUUID());
                }
                return run;
            });
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(COMPANY_ID))).thenReturn(true);
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(COMPANY_ID))).thenReturn(0);
            when(materializedViewRefreshService.refreshAllMaterializedViews(COMPANY_ID)).thenReturn(100);
            when(freshnessRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(new DashboardFreshness()));

            DashboardETLRun result = service.refreshMaterializedViews(COMPANY_ID);

            assertThat(result.getStatus()).isEqualTo(ETLJobStatus.COMPLETED);
            verify(etlAlertService, never()).alertOnJobFailure(any());
        }

        @Test
        @DisplayName("should alert service when refresh service throws after retries exhausted")
        void refreshMaterializedViews_failsAfterRetries_alertsService() {
            when(etlRunRepository.save(any(DashboardETLRun.class))).thenAnswer(inv -> {
                DashboardETLRun run = inv.getArgument(0);
                if (run.getId() == null) {
                    run.setId(UUID.randomUUID());
                }
                return run;
            });
            when(freshnessRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(new DashboardFreshness()));
            when(materializedViewRefreshService.refreshAllMaterializedViews(COMPANY_ID))
                    .thenThrow(new RuntimeException("Persistent failure"));

            DashboardETLRun result = service.refreshMaterializedViews(COMPANY_ID);

            assertThat(result.getStatus()).isEqualTo(ETLJobStatus.FAILED);
            assertThat(result.getErrorMessage()).contains("Persistent failure");
            verify(etlAlertService).alertOnJobFailure(any(DashboardETLRun.class));
        }
    }

    @Nested
    @DisplayName("updateFreshnessStatus tests")
    class UpdateFreshnessStatusTests {

        @Test
        @DisplayName("should set GREEN freshness level on success")
        void updateFreshnessStatus_success_setsGreenLevel() {
            ArgumentCaptor<DashboardFreshness> captor = ArgumentCaptor.forClass(DashboardFreshness.class);
            DashboardFreshness freshness = new DashboardFreshness();
            freshness.setCompanyId(COMPANY_ID);
            when(freshnessRepository.findByCompanyId(COMPANY_ID)).thenReturn(Optional.of(freshness));
            when(freshnessRepository.save(any(DashboardFreshness.class))).thenAnswer(inv -> inv.getArgument(0));

            UUID etlRunId = UUID.randomUUID();
            UUID lastPostedVoucherId = UUID.randomUUID();
            service.updateFreshnessStatus(COMPANY_ID, etlRunId, true, lastPostedVoucherId);

            verify(freshnessRepository).save(captor.capture());
            DashboardFreshness saved = captor.getValue();
            assertThat(saved.getFreshnessLevel()).isEqualTo(FreshnessLevel.GREEN);
            assertThat(saved.getLastRefreshStatus()).isEqualTo("COMPLETED");
            assertThat(saved.getConsecutiveFailures()).isZero();
        }
    }

    @Nested
    @DisplayName("acquireLock tests")
    class AcquireLockTests {

        @Test
        @DisplayName("should return true when lock acquired")
        void acquireLock_success_returnsTrue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(
                    eq("etl:lock:company:" + COMPANY_ID),
                    anyString(),
                    any(Duration.class))).thenReturn(true);

            boolean result = service.acquireLock(COMPANY_ID, "instance-1");

            assertThat(result).isTrue();
            verify(valueOperations).setIfAbsent(
                    eq("etl:lock:company:" + COMPANY_ID),
                    eq("instance-1"),
                    eq(Duration.ofSeconds(300)));
        }

        @Test
        @DisplayName("should return false when lock held by another")
        void acquireLock_heldByAnother_returnsFalse() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
            when(valueOperations.get("etl:lock:company:" + COMPANY_ID)).thenReturn("other-instance");

            boolean result = service.acquireLock(COMPANY_ID, "instance-1");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("releaseLock tests")
    class ReleaseLockTests {

        @Test
        @DisplayName("should release lock only if owner matches")
        void releaseLock_releasesOnlyIfOwner() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("etl:lock:company:" + COMPANY_ID)).thenReturn("instance-1");

            service.releaseLock(COMPANY_ID, "instance-1");

            verify(redisTemplate).delete("etl:lock:company:" + COMPANY_ID);
        }

        @Test
        @DisplayName("should not release lock if owner does not match")
        void releaseLock_doesNotReleaseIfNotOwner() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("etl:lock:company:" + COMPANY_ID)).thenReturn("other-instance");

            service.releaseLock(COMPANY_ID, "instance-1");

            verify(redisTemplate, never()).delete(anyString());
        }
    }
}

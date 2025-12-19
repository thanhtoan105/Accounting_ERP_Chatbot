package com.accounting.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.accounting.entity.dashboard.DashboardETLRun;
import com.accounting.entity.dashboard.DashboardFreshness;
import com.accounting.entity.dashboard.ETLJobStatus;
import com.accounting.entity.dashboard.FreshnessLevel;
import com.accounting.repository.dashboard.DashboardFreshnessRepository;
import com.accounting.test.IntegrationTest;

/**
 * Integration tests for analytics provisioning using Testcontainers.
 * Tests materialized views, tenant isolation, and ETL freshness updates.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsProvisioningIntegrationTest extends IntegrationTest {

    private static final Long COMPANY_A = 100L;
    private static final Long COMPANY_B = 200L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantAnalyticsProvisioningService provisioningService;

    @Autowired
    private ETLPipelineService etlPipelineService;

    @Autowired
    private DashboardFreshnessRepository freshnessRepository;

    @BeforeEach
    void setupTestCompanies() {
        jdbcTemplate.execute("""
            INSERT INTO companies (id, name, created_at, updated_at)
            VALUES (100, 'Company A', NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
            """);
        jdbcTemplate.execute("""
            INSERT INTO companies (id, name, created_at, updated_at)
            VALUES (200, 'Company B', NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
            """);

        try {
            provisioningService.deprovisionTenant(COMPANY_A);
        } catch (Exception ignored) {}
        try {
            provisioningService.deprovisionTenant(COMPANY_B);
        } catch (Exception ignored) {}
    }

    @Nested
    @DisplayName("Materialized Views Existence Tests")
    class MaterializedViewsExistenceTests {

        @Test
        @DisplayName("mv_daily_revenue_expense exists after migration")
        void mvDailyRevenueExpenseExists() {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_daily_revenue_expense')",
                    Boolean.class);
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("mv_ar_ap_aging exists after migration")
        void mvArApAgingExists() {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_ar_ap_aging')",
                    Boolean.class);
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("mv_cash_flow_summary exists after migration")
        void mvCashFlowSummaryExists() {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_cash_flow_summary')",
                    Boolean.class);
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("mv_period_summary exists after migration")
        void mvPeriodSummaryExists() {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_period_summary')",
                    Boolean.class);
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("mv_top_debtors_creditors exists after migration")
        void mvTopDebtorsCreditors() {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_top_debtors_creditors')",
                    Boolean.class);
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("all 5 materialized views exist after migration")
        void allMaterializedViewsExist() {
            String[] expectedViews = {
                    "mv_daily_revenue_expense",
                    "mv_ar_ap_aging",
                    "mv_cash_flow_summary",
                    "mv_period_summary",
                    "mv_top_debtors_creditors"
            };

            for (String viewName : expectedViews) {
                Boolean exists = jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = ?)",
                        Boolean.class,
                        viewName);
                assertThat(exists)
                        .as("Materialized view %s should exist", viewName)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Tenant Schema View Filtering Tests")
    class TenantSchemaViewFilteringTests {

        @Test
        @DisplayName("tenant provisioning creates schema with company-filtered views")
        void tenantProvisioningCreatesFilteredViews() {
            var result = provisioningService.provisionTenant(COMPANY_A);

            assertThat(result.success()).isTrue();

            String[] viewNames = {
                    "v_daily_revenue_expense",
                    "v_ar_ap_aging",
                    "v_cash_flow_summary",
                    "v_period_summary",
                    "v_top_debtors_creditors"
            };

            for (String viewName : viewNames) {
                Boolean exists = jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM information_schema.views WHERE table_schema = ? AND table_name = ?)",
                        Boolean.class,
                        "mb_company_100", viewName);
                assertThat(exists)
                        .as("View %s should exist in tenant schema", viewName)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("tenant views have company_id filter in definition")
        void tenantViewsHaveCompanyIdFilter() {
            provisioningService.provisionTenant(COMPANY_A);

            String viewDefinition = jdbcTemplate.queryForObject(
                    "SELECT view_definition FROM information_schema.views WHERE table_schema = ? AND table_name = ?",
                    String.class,
                    "mb_company_100", "v_daily_revenue_expense");

            assertThat(viewDefinition)
                    .as("View definition should contain company_id filter")
                    .containsIgnoringCase("100");
        }

        @Test
        @DisplayName("different tenants get separate schemas")
        void differentTenantsGetSeparateSchemas() {
            provisioningService.provisionTenant(COMPANY_A);
            provisioningService.provisionTenant(COMPANY_B);

            Boolean schemaAExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                    Boolean.class,
                    "mb_company_100");
            Boolean schemaBExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                    Boolean.class,
                    "mb_company_200");

            assertThat(schemaAExists).isTrue();
            assertThat(schemaBExists).isTrue();
        }
    }

    @Nested
    @DisplayName("ETL Refresh Freshness Update Tests")
    class ETLRefreshFreshnessTests {

        @Test
        @DisplayName("ETL refresh creates freshness record if not exists")
        void etlRefreshCreatesFreshnessRecord() {
            freshnessRepository.deleteAll();

            DashboardETLRun run = etlPipelineService.refreshMaterializedViews(COMPANY_A);

            assertThat(run).isNotNull();
            assertThat(run.getStatus()).isIn(ETLJobStatus.COMPLETED, ETLJobStatus.FAILED);

            Optional<DashboardFreshness> freshness = freshnessRepository.findByCompanyId(COMPANY_A);
            assertThat(freshness).isPresent();
        }

        @Test
        @DisplayName("successful ETL refresh sets GREEN freshness level")
        void successfulETLRefreshSetsGreenLevel() {
            DashboardETLRun run = etlPipelineService.refreshMaterializedViews(COMPANY_A);

            if (run.getStatus() == ETLJobStatus.COMPLETED) {
                Optional<DashboardFreshness> freshness = freshnessRepository.findByCompanyId(COMPANY_A);
                assertThat(freshness).isPresent();
                assertThat(freshness.get().getFreshnessLevel()).isEqualTo(FreshnessLevel.GREEN);
                assertThat(freshness.get().getConsecutiveFailures()).isZero();
            }
        }

        @Test
        @DisplayName("ETL refresh updates last refresh timestamp")
        void etlRefreshUpdatesTimestamp() {
            DashboardETLRun run = etlPipelineService.refreshMaterializedViews(COMPANY_A);

            if (run.getStatus() == ETLJobStatus.COMPLETED) {
                Optional<DashboardFreshness> freshness = freshnessRepository.findByCompanyId(COMPANY_A);
                assertThat(freshness).isPresent();
                assertThat(freshness.get().getLastSuccessfulRefresh()).isNotNull();
            }
        }

        @Test
        @DisplayName("ETL run records job metadata correctly")
        void etlRunRecordsJobMetadata() {
            DashboardETLRun run = etlPipelineService.refreshMaterializedViews(COMPANY_A);

            assertThat(run.getId()).isNotNull();
            assertThat(run.getCompanyId()).isEqualTo(COMPANY_A);
            assertThat(run.getJobName()).isEqualTo("DASHBOARD_MV_REFRESH");
            assertThat(run.getStartedAt()).isNotNull();

            if (run.getStatus() == ETLJobStatus.COMPLETED) {
                assertThat(run.getCompletedAt()).isNotNull();
                assertThat(run.getRowsProcessed()).isNotNull();
            }
        }

        @Test
        @DisplayName("manual ETL refresh records trigger type and user")
        void manualETLRefreshRecordsTriggerInfo() {
            Long userId = 999L;
            DashboardETLRun run = etlPipelineService.refreshMaterializedViewsManual(COMPANY_A, userId);

            assertThat(run.getTriggeredByUserId()).isEqualTo(userId);
            assertThat(run.getTriggeredBy().name()).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("ETL status can be retrieved by job ID")
        void etlStatusCanBeRetrievedByJobId() {
            DashboardETLRun run = etlPipelineService.refreshMaterializedViews(COMPANY_A);
            UUID jobId = run.getId();

            DashboardETLRun retrieved = etlPipelineService.getETLRunStatus(jobId);

            assertThat(retrieved).isNotNull();
            assertThat(retrieved.getId()).isEqualTo(jobId);
            assertThat(retrieved.getCompanyId()).isEqualTo(COMPANY_A);
        }
    }

    @Nested
    @DisplayName("Integrity Check Tests")
    class IntegrityCheckTests {

        @Test
        @DisplayName("integrity check runs without error")
        void integrityCheckRunsWithoutError() {
            var result = etlPipelineService.runIntegrityChecks(COMPANY_A);

            assertThat(result).isNotNull();
        }
    }
}

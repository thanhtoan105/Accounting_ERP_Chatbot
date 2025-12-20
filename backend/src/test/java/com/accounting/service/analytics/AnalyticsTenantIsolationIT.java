package com.accounting.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.accounting.service.analytics.TenantAnalyticsProvisioningService.TenantCredentials;
import com.accounting.test.IntegrationTest;

/**
 * Security integration tests for tenant isolation in analytics (AC 8.0.3).
 * Tests PostgreSQL role/schema/view creation and data isolation.
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsTenantIsolationIT extends IntegrationTest {

    private static final Long COMPANY_A = 100L;
    private static final Long COMPANY_B = 200L;

    @Autowired
    private TenantAnalyticsProvisioningService provisioningService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupTestData() {
        cleanupTenants();
        setupMaterializedViewsWithTestData();
    }

    private void cleanupTenants() {
        try {
            provisioningService.deprovisionTenant(COMPANY_A);
        } catch (Exception ignored) {}
        try {
            provisioningService.deprovisionTenant(COMPANY_B);
        } catch (Exception ignored) {}
    }

    private void setupMaterializedViewsWithTestData() {
        insertTestDataForMaterializedViews();
        refreshMaterializedViews();
    }

    private void insertTestDataForMaterializedViews() {
        jdbcTemplate.execute("""
            INSERT INTO companies (id, code, name, tax_code, address, created_at, updated_at)
            VALUES (100, 'COMPA', 'Company A', '1000000100', 'Address A', NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
            """);
        jdbcTemplate.execute("""
            INSERT INTO companies (id, code, name, tax_code, address, created_at, updated_at)
            VALUES (200, 'COMPB', 'Company B', '2000000200', 'Address B', NOW(), NOW())
            ON CONFLICT (id) DO NOTHING
            """);
    }

    private void refreshMaterializedViews() {
        try {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_daily_revenue_expense");
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_ar_ap_aging");
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_cash_flow_summary");
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_period_summary");
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_top_debtors_creditors");
        } catch (Exception e) {
        }
    }

    @Nested
    @DisplayName("Tenant SQL Role/Schema/Views Creation Tests")
    class TenantProvisioningTests {

        @Test
        @DisplayName("provisionTenant creates PostgreSQL role with correct naming")
        void provisionTenant_createsRole() {
            var result = provisioningService.provisionTenant(COMPANY_A);

            assertThat(result.success()).isTrue();
            assertThat(result.roleName()).isEqualTo("mb_company_100_ro");

            Boolean roleExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)",
                    Boolean.class,
                    "mb_company_100_ro");
            assertThat(roleExists).isTrue();
        }

        @Test
        @DisplayName("provisionTenant creates schema with correct naming")
        void provisionTenant_createsSchema() {
            var result = provisioningService.provisionTenant(COMPANY_A);

            assertThat(result.success()).isTrue();
            assertThat(result.schemaName()).isEqualTo("mb_company_100");

            Boolean schemaExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                    Boolean.class,
                    "mb_company_100");
            assertThat(schemaExists).isTrue();
        }

        @Test
        @DisplayName("provisionTenant creates security views in tenant schema")
        void provisionTenant_createsSecurityViews() {
            provisioningService.provisionTenant(COMPANY_A);

            String[] expectedViews = {
                    "v_daily_revenue_expense",
                    "v_ar_ap_aging",
                    "v_cash_flow_summary",
                    "v_period_summary",
                    "v_top_debtors_creditors"
            };

            for (String viewName : expectedViews) {
                Boolean viewExists = jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM information_schema.views WHERE table_schema = ? AND table_name = ?)",
                        Boolean.class,
                        "mb_company_100", viewName);
                assertThat(viewExists)
                        .as("View %s should exist in schema mb_company_100", viewName)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("provisionTenant is idempotent - safe to call multiple times")
        void provisionTenant_idempotent() {
            var result1 = provisioningService.provisionTenant(COMPANY_A);
            var result2 = provisioningService.provisionTenant(COMPANY_A);

            assertThat(result1.success()).isTrue();
            assertThat(result2.success()).isTrue();
            assertThat(result2.message()).contains("already");
        }

        @Test
        @DisplayName("getTenantCredentials returns valid connection info")
        void getTenantCredentials_returnsValidInfo() {
            provisioningService.provisionTenant(COMPANY_A);

            Optional<TenantCredentials> credentials = provisioningService.getTenantCredentials(COMPANY_A);

            assertThat(credentials).isPresent();
            TenantCredentials creds = credentials.get();
            assertThat(creds.roleName()).isEqualTo("mb_company_100_ro");
            assertThat(creds.schemaName()).isEqualTo("mb_company_100");
            assertThat(creds.password()).isNotBlank();
            assertThat(creds.jdbcUrl()).contains("mb_company_100");
        }
    }

    @Nested
    @DisplayName("Tenant Role Cannot Read Other Company's Data Tests")
    class TenantDataIsolationTests {

        @Test
        @DisplayName("tenant role can connect to database")
        void tenantRole_canConnect() throws SQLException {
            provisioningService.provisionTenant(COMPANY_A);
            TenantCredentials creds = provisioningService.getTenantCredentials(COMPANY_A).orElseThrow();

            String jdbcUrl = extractBaseUrl();
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl, creds.roleName(), creds.password())) {
                assertThat(conn.isValid(5)).isTrue();
            }
        }

        @Test
        @DisplayName("tenant role cannot access public schema")
        void tenantRole_cannotAccessPublicSchema() throws SQLException {
            provisioningService.provisionTenant(COMPANY_A);
            TenantCredentials creds = provisioningService.getTenantCredentials(COMPANY_A).orElseThrow();

            String jdbcUrl = extractBaseUrl();
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl, creds.roleName(), creds.password())) {

                assertThatThrownBy(() -> {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "SELECT * FROM public.mv_daily_revenue_expense LIMIT 1")) {
                        stmt.executeQuery();
                    }
                }).isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }

        @Test
        @DisplayName("tenant role can only see their own company's data through views")
        void tenantRole_canOnlySeeOwnData() throws SQLException {
            provisioningService.provisionTenant(COMPANY_A);
            provisioningService.provisionTenant(COMPANY_B);

            TenantCredentials credsA = provisioningService.getTenantCredentials(COMPANY_A).orElseThrow();

            String jdbcUrl = extractBaseUrl();
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl, credsA.roleName(), credsA.password())) {

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM mb_company_100.v_daily_revenue_expense")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        rs.next();
                    }
                }

                assertThatThrownBy(() -> {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "SELECT * FROM mb_company_200.v_daily_revenue_expense LIMIT 1")) {
                        stmt.executeQuery();
                    }
                }).isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }

        @Test
        @DisplayName("tenant A cannot query tenant B's schema")
        void tenantA_cannotQueryTenantBSchema() throws SQLException {
            provisioningService.provisionTenant(COMPANY_A);
            provisioningService.provisionTenant(COMPANY_B);

            TenantCredentials credsA = provisioningService.getTenantCredentials(COMPANY_A).orElseThrow();

            String jdbcUrl = extractBaseUrl();
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl, credsA.roleName(), credsA.password())) {

                String[] viewsToTest = {
                        "v_daily_revenue_expense",
                        "v_ar_ap_aging",
                        "v_cash_flow_summary",
                        "v_period_summary",
                        "v_top_debtors_creditors"
                };

                for (String view : viewsToTest) {
                    String sql = "SELECT * FROM mb_company_200." + view + " LIMIT 1";
                    assertThatThrownBy(() -> {
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.executeQuery();
                        }
                    }).as("Tenant A should not access tenant B's view: %s", view)
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("permission denied");
                }
            }
        }

        private String extractBaseUrl() {
            return POSTGRES.getJdbcUrl();
        }
    }

    @Nested
    @DisplayName("Deprovision Tenant Tests")
    class DeprovisionTests {

        @Test
        @DisplayName("deprovisionTenant removes role and schema")
        void deprovisionTenant_removesRoleAndSchema() {
            provisioningService.provisionTenant(COMPANY_A);
            assertThat(provisioningService.isTenantProvisioned(COMPANY_A)).isTrue();

            var result = provisioningService.deprovisionTenant(COMPANY_A);

            assertThat(result.success()).isTrue();

            Boolean roleExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)",
                    Boolean.class,
                    "mb_company_100_ro");
            assertThat(roleExists).isFalse();

            Boolean schemaExists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                    Boolean.class,
                    "mb_company_100");
            assertThat(schemaExists).isFalse();
        }

        @Test
        @DisplayName("deprovisionTenant is idempotent")
        void deprovisionTenant_idempotent() {
            var result1 = provisioningService.deprovisionTenant(COMPANY_A);
            var result2 = provisioningService.deprovisionTenant(COMPANY_A);

            assertThat(result1.success()).isTrue();
            assertThat(result2.success()).isTrue();
        }
    }
}

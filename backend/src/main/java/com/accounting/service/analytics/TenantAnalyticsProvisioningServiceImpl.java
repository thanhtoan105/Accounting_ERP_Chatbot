package com.accounting.service.analytics;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAnalyticsProvisioningServiceImpl implements TenantAnalyticsProvisioningService {

    private static final Logger logger = LoggerFactory.getLogger(TenantAnalyticsProvisioningServiceImpl.class);
    private static final String ROLE_PREFIX = "mb_company_";
    private static final String ROLE_SUFFIX = "_ro";
    private static final String SCHEMA_PREFIX = "mb_company_";
    private static final String CREDENTIALS_KEY_PREFIX = "tenant:analytics:credentials:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public TenantAnalyticsProvisioningServiceImpl(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public ProvisioningResult provisionTenant(Long companyId) {
        String roleName = getRoleName(companyId);
        String schemaName = getSchemaName(companyId);

        logger.info("Provisioning analytics tenant for company {}: role={}, schema={}", 
                companyId, roleName, schemaName);

        try {
            if (isTenantProvisioned(companyId)) {
                logger.info("Tenant {} already provisioned, skipping", companyId);
                return ProvisioningResult.alreadyExists(roleName, schemaName);
            }

            String password = generateSecurePassword();

            createRole(roleName, password);
            createSchema(schemaName, roleName);
            createSecurityViews(companyId, schemaName);
            grantPermissions(schemaName, roleName);
            revokePublicAccess(roleName);
            setSearchPath(roleName, schemaName);

            storeCredentials(companyId, roleName, schemaName, password);

            logger.info("Successfully provisioned analytics tenant for company {}", companyId);
            return ProvisioningResult.success(roleName, schemaName);

        } catch (DataAccessException e) {
            logger.error("Failed to provision tenant {}: {}", companyId, e.getMessage(), e);
            return ProvisioningResult.failure("Database error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error provisioning tenant {}: {}", companyId, e.getMessage(), e);
            return ProvisioningResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ProvisioningResult deprovisionTenant(Long companyId) {
        String roleName = getRoleName(companyId);
        String schemaName = getSchemaName(companyId);

        logger.info("Deprovisioning analytics tenant for company {}", companyId);

        try {
            dropSchema(schemaName);
            dropRole(roleName);
            removeCredentials(companyId);

            logger.info("Successfully deprovisioned analytics tenant for company {}", companyId);
            return ProvisioningResult.success(roleName, schemaName);

        } catch (DataAccessException e) {
            logger.error("Failed to deprovision tenant {}: {}", companyId, e.getMessage(), e);
            return ProvisioningResult.failure("Database error: " + e.getMessage());
        }
    }

    @Override
    public boolean isTenantProvisioned(Long companyId) {
        String roleName = getRoleName(companyId);
        String schemaName = getSchemaName(companyId);

        Boolean roleExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)",
                Boolean.class,
                roleName);

        Boolean schemaExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                Boolean.class,
                schemaName);

        return Boolean.TRUE.equals(roleExists) && Boolean.TRUE.equals(schemaExists);
    }

    @Override
    public Optional<TenantCredentials> getTenantCredentials(Long companyId) {
        String key = CREDENTIALS_KEY_PREFIX + companyId;
        String storedPassword = redisTemplate.opsForValue().get(key);

        if (storedPassword == null) {
            return Optional.empty();
        }

        String roleName = getRoleName(companyId);
        String schemaName = getSchemaName(companyId);
        String jdbcUrl = buildJdbcUrl(roleName, storedPassword, schemaName);

        return Optional.of(new TenantCredentials(roleName, schemaName, storedPassword, jdbcUrl));
    }

    private void createRole(String roleName, String password) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)",
                Boolean.class,
                roleName);

        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute(String.format(
                    "ALTER ROLE %s WITH PASSWORD '%s'", 
                    sanitizeIdentifier(roleName), 
                    sanitizePassword(password)));
            logger.debug("Updated password for existing role: {}", roleName);
        } else {
            jdbcTemplate.execute(String.format(
                    "CREATE ROLE %s WITH LOGIN PASSWORD '%s' NOSUPERUSER NOCREATEDB NOCREATEROLE",
                    sanitizeIdentifier(roleName),
                    sanitizePassword(password)));
            logger.debug("Created role: {}", roleName);
        }

        String currentDb = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        jdbcTemplate.execute(String.format(
                "GRANT CONNECT ON DATABASE %s TO %s",
                sanitizeIdentifier(currentDb),
                sanitizeIdentifier(roleName)));
    }

    private void createSchema(String schemaName, String roleName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                Boolean.class,
                schemaName);

        if (!Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute(String.format(
                    "CREATE SCHEMA %s AUTHORIZATION %s",
                    sanitizeIdentifier(schemaName),
                    sanitizeIdentifier(roleName)));
            logger.debug("Created schema: {}", schemaName);
        }

        jdbcTemplate.execute(String.format(
                "GRANT USAGE ON SCHEMA %s TO %s",
                sanitizeIdentifier(schemaName),
                sanitizeIdentifier(roleName)));
    }

    private void createSecurityViews(Long companyId, String schemaName) {
        String schema = sanitizeIdentifier(schemaName);

        jdbcTemplate.execute(String.format("""
            CREATE OR REPLACE VIEW %s.v_daily_revenue_expense AS
            SELECT transaction_date, revenue, expense, net_income, voucher_count, refreshed_at
            FROM public.mv_daily_revenue_expense
            WHERE company_id = %d
            """, schema, companyId));

        jdbcTemplate.execute(String.format("""
            CREATE OR REPLACE VIEW %s.v_ar_ap_aging AS
            SELECT balance_type, customer_id, supplier_id, 
                   bucket_current, bucket_1_30, bucket_31_60, bucket_61_90, bucket_over_90,
                   total_outstanding, transaction_count, refreshed_at
            FROM public.mv_ar_ap_aging
            WHERE company_id = %d
            """, schema, companyId));

        jdbcTemplate.execute(String.format("""
            CREATE OR REPLACE VIEW %s.v_cash_flow_summary AS
            SELECT transaction_date, account_id, account_code, account_name,
                   cash_in, cash_out, net_flow, transaction_count, refreshed_at
            FROM public.mv_cash_flow_summary
            WHERE company_id = %d
            """, schema, companyId));

        jdbcTemplate.execute(String.format("""
            CREATE OR REPLACE VIEW %s.v_period_summary AS
            SELECT period_id, total_revenue, total_expense, 
                   (total_revenue - total_expense) AS net_profit,
                   ar_balance, ap_balance, cash_balance,
                   voucher_count, period_start, period_end, refreshed_at
            FROM public.mv_period_summary
            WHERE company_id = %d
            """, schema, companyId));

        jdbcTemplate.execute(String.format("""
            CREATE OR REPLACE VIEW %s.v_top_debtors_creditors AS
            SELECT entity_type, entity_id, entity_name, entity_code, balance, rank, refreshed_at
            FROM public.mv_top_debtors_creditors
            WHERE company_id = %d
            """, schema, companyId));

        logger.debug("Created security views in schema: {}", schemaName);
    }

    private void grantPermissions(String schemaName, String roleName) {
        String schema = sanitizeIdentifier(schemaName);
        String role = sanitizeIdentifier(roleName);

        jdbcTemplate.execute(String.format(
                "GRANT SELECT ON ALL TABLES IN SCHEMA %s TO %s", schema, role));

        jdbcTemplate.execute(String.format(
                "ALTER DEFAULT PRIVILEGES IN SCHEMA %s GRANT SELECT ON TABLES TO %s", schema, role));

        logger.debug("Granted SELECT permissions to {} on schema {}", roleName, schemaName);
    }

    private void revokePublicAccess(String roleName) {
        String role = sanitizeIdentifier(roleName);

        jdbcTemplate.execute(String.format(
                "REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %s", role));

        jdbcTemplate.execute(String.format(
                "REVOKE USAGE ON SCHEMA public FROM %s", role));

        logger.debug("Revoked public schema access from {}", roleName);
    }

    private void setSearchPath(String roleName, String schemaName) {
        jdbcTemplate.execute(String.format(
                "ALTER ROLE %s SET search_path TO %s",
                sanitizeIdentifier(roleName),
                sanitizeIdentifier(schemaName)));

        logger.debug("Set search_path for {} to {}", roleName, schemaName);
    }

    private void dropSchema(String schemaName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                Boolean.class,
                schemaName);

        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute(String.format(
                    "DROP SCHEMA %s CASCADE", sanitizeIdentifier(schemaName)));
            logger.debug("Dropped schema: {}", schemaName);
        }
    }

    private void dropRole(String roleName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)",
                Boolean.class,
                roleName);

        if (Boolean.TRUE.equals(exists)) {
            String currentDb = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
            jdbcTemplate.execute(String.format(
                    "REVOKE ALL PRIVILEGES ON DATABASE %s FROM %s",
                    sanitizeIdentifier(currentDb),
                    sanitizeIdentifier(roleName)));

            jdbcTemplate.execute(String.format(
                    "DROP ROLE %s", sanitizeIdentifier(roleName)));
            logger.debug("Dropped role: {}", roleName);
        }
    }

    private void storeCredentials(Long companyId, String roleName, String schemaName, String password) {
        String key = CREDENTIALS_KEY_PREFIX + companyId;
        redisTemplate.opsForValue().set(key, password);
        logger.debug("Stored credentials for company {} in Redis", companyId);
    }

    private void removeCredentials(Long companyId) {
        String key = CREDENTIALS_KEY_PREFIX + companyId;
        redisTemplate.delete(key);
        logger.debug("Removed credentials for company {} from Redis", companyId);
    }

    private String generateSecurePassword() {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String buildJdbcUrl(String roleName, String password, String schemaName) {
        String baseUrl = datasourceUrl.split("\\?")[0];
        return String.format("%s?user=%s&password=%s&currentSchema=%s",
                baseUrl, roleName, password, schemaName);
    }

    private String getRoleName(Long companyId) {
        return ROLE_PREFIX + companyId + ROLE_SUFFIX;
    }

    private String getSchemaName(Long companyId) {
        return SCHEMA_PREFIX + companyId;
    }

    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return identifier;
    }

    private String sanitizePassword(String password) {
        return password.replace("'", "''");
    }
}

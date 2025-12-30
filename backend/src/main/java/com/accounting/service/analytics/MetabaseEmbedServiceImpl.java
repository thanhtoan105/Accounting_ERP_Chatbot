package com.accounting.service.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.accounting.entity.User;
import com.accounting.entity.dashboard.DashboardAuditLog;
import com.accounting.repository.dashboard.DashboardAuditLogRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class MetabaseEmbedServiceImpl implements MetabaseEmbedService {

    private static final Logger log = LoggerFactory.getLogger(MetabaseEmbedServiceImpl.class);

    private static final int TOKEN_EXPIRY_MINUTES = 60;
    private static final String TENANT_GROUP_PREFIX = "company_";

    private static final Map<String, String> ROLE_TO_METABASE_GROUP = Map.of(
            "ADMIN", "Analytics Admins",
            "CHIEF_ACCOUNTANT", "Analytics Power Users",
            "CFO", "Analytics Viewers",
            "ACCOUNTANT", "Analytics Basic",
            "ACCOUNTANT_GENERAL", "Analytics Basic",
            "ACCOUNTANT_AR", "Analytics AR",
            "ACCOUNTANT_AP", "Analytics AP",
            "CASHIER", "Analytics Cashier",
            "FINANCE", "Analytics Basic"
    );

    @Value("${metabase.site-url}")
    private String metabaseSiteUrl;

    @Value("${metabase.embedding-secret}")
    private String jwtSecret;

    private final DashboardAuditLogRepository auditLogRepository;

    public MetabaseEmbedServiceImpl(DashboardAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public MetabaseEmbedConfig generateEmbedConfig(String dashboardKey, User user, Long companyId) {
        log.info("Generating embed config for user {} (company {}), dashboard: {}",
                user.getEmail(), companyId, dashboardKey);

        logAuditEvent(companyId, user.getId(), "EMBED_CONFIG_REQUEST", "DASHBOARD", dashboardKey);

        return buildEmbedConfig(companyId);
    }

    @Override
    public MetabaseEmbedConfig refreshEmbedConfig(User user, Long companyId) {
        log.debug("Refreshing embed config for user {} (company {})", user.getEmail(), companyId);

        logAuditEvent(companyId, user.getId(), "EMBED_CONFIG_REFRESH", "DASHBOARD", null);

        return buildEmbedConfig(companyId);
    }

    public String generateJwtToken(User user, Long companyId) {
        log.debug("Generating JWT token for user {} with locked company_id {}", user.getEmail(), companyId);

        String[] names = splitFullName(user.getFullName());
        List<String> groups = buildGroups(user.getRole(), companyId);

        Instant expiration = Instant.now().plus(TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES);

        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        String token = Jwts.builder()
                .claim("email", user.getEmail())
                .claim("first_name", names[0])
                .claim("last_name", names[1])
                .claim("groups", groups)
                .claim("company_id", companyId)
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();

        log.debug("Generated JWT token for user {} with expiry {} and groups {}",
                user.getEmail(), expiration, groups);

        return token;
    }

    public long getTokenExpiryMinutes() {
        return TOKEN_EXPIRY_MINUTES;
    }

    public long getRefreshBeforeExpiryMinutes() {
        return 15;
    }

    @Override
    public String generateDashboardEmbedUrl(Long dashboardId) {
        Long companyId = com.accounting.security.CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Company context not set");
        }

        Map<String, Object> payload = Map.of(
            "resource", Map.of("dashboard", dashboardId),
            "params", Map.of("company_id", companyId.toString()),
            "exp", Instant.now().plus(TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES).getEpochSecond()
        );

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        String token = Jwts.builder()
            .claims(payload)
            .signWith(key)
            .compact();

        return metabaseSiteUrl + "/embed/dashboard/" + token;
    }

    @Override
    public String generateQuestionEmbedUrl(Long questionId) {
        Long companyId = com.accounting.security.CompanyContext.getCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("Company context not set");
        }

        Map<String, Object> payload = Map.of(
            "resource", Map.of("question", questionId),
            "params", Map.of("company_id", companyId.toString()),
            "exp", Instant.now().plus(TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES).getEpochSecond()
        );

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        String token = Jwts.builder()
            .claims(payload)
            .signWith(key)
            .compact();

        return metabaseSiteUrl + "/embed/question/" + token;
    }

    private MetabaseEmbedConfig buildEmbedConfig(Long companyId) {
        String authProviderUri = "/api/v1/analytics/metabase/sso/token";

        return new MetabaseEmbedConfig(
                metabaseSiteUrl,
                new AuthConfig(
                        true,
                        new AuthProvider(authProviderUri, true),
                        AuthType.JWT
                )
        );
    }

    private List<String> buildGroups(String role, Long companyId) {
        List<String> groups = new ArrayList<>();

        groups.add(TENANT_GROUP_PREFIX + companyId);

        if (role != null) {
            String normalizedRole = role.toUpperCase();
            String metabaseGroup = ROLE_TO_METABASE_GROUP.get(normalizedRole);
            if (metabaseGroup != null) {
                groups.add(metabaseGroup);
            }
        }

        return groups;
    }

    private String[] splitFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"User", ""};
        }

        String[] parts = fullName.trim().split("\\s+", 2);
        if (parts.length == 1) {
            return new String[]{parts[0], ""};
        }
        return parts;
    }

    private void logAuditEvent(Long companyId, Long userId, String action, String resourceType, String resourceId) {
        try {
            DashboardAuditLog auditLog = DashboardAuditLog.create(companyId, userId, action, resourceType);
            auditLog.setResourceId(resourceId);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to log audit event: {}", e.getMessage());
        }
    }
}

package com.accounting.service.analytics;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accounting.entity.dashboard.DashboardAuditLog;
import com.accounting.repository.dashboard.DashboardAuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AnalyticsAuditService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAuditService.class);

    public enum AuditAction {
        EMBED_CONFIG_REQUEST("Requested embed configuration"),
        SSO_TOKEN_GENERATED("Generated SSO JWT token"),
        DASHBOARD_VIEW_LOADED("Dashboard view loaded in frontend"),
        DASHBOARD_VIEW_ERROR("Dashboard view error in frontend"),
        MANUAL_REFRESH_ATTEMPT("Manual refresh attempted"),
        MANUAL_REFRESH_SUCCESS("Manual refresh completed successfully"),
        MANUAL_REFRESH_DENIED("Manual refresh denied - unauthorized"),
        MANUAL_REFRESH_RATE_LIMITED("Manual refresh denied - rate limited"),
        AUTO_REFRESH_STARTED("Auto refresh job started"),
        AUTO_REFRESH_SUCCESS("Auto refresh completed successfully"),
        AUTO_REFRESH_FAILED("Auto refresh failed"),
        EXPORT_TRIGGERED("Data export triggered"),
        WIDGET_ACCESSED("Widget data accessed"),
        WIDGET_ACCESS_DENIED("Widget access denied - unauthorized"),
        USER_PROVISIONED("User provisioned in Metabase"),
        USER_DEACTIVATED("User deactivated in Metabase"),
        GROUP_ASSIGNED("User assigned to Metabase group"),
        TENANT_PROVISIONED("Tenant provisioned in Metabase"),
        DATABASE_CONNECTION_CREATED("Database connection created in Metabase"),
        AUTHORIZATION_DENIED("Authorization denied");

        private final String description;

        AuditAction(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum ResourceType {
        DASHBOARD,
        WIDGET,
        ETL_JOB,
        JWT,
        USER,
        GROUP,
        TENANT,
        DATABASE_CONNECTION,
        EXPORT
    }

    private final DashboardAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AnalyticsAuditService(
            DashboardAuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logAction(
            Long companyId,
            Long userId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId) {
        logAction(companyId, userId, action, resourceType, resourceId, null, null);
    }

    public void logAction(
            Long companyId,
            Long userId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            Map<String, Object> metadata) {
        logAction(companyId, userId, action, resourceType, resourceId, metadata, null);
    }

    public void logAction(
            Long companyId,
            Long userId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            Map<String, Object> metadata,
            HttpServletRequest request) {
        try {
            DashboardAuditLog auditLog = DashboardAuditLog.create(
                    companyId,
                    userId,
                    action.name(),
                    resourceType.name());

            auditLog.setResourceId(resourceId);

            if (request != null) {
                auditLog.setIpAddress(extractIpAddress(request));
                auditLog.setUserAgent(sanitizeUserAgent(request.getHeader("User-Agent")));
                auditLog.setRequestPath(request.getRequestURI());
                auditLog.setRequestMethod(request.getMethod());
            }

            if (metadata != null && !metadata.isEmpty()) {
                auditLog.setMetadata(serializeMetadata(sanitizeMetadata(metadata)));
            }

            auditLogRepository.save(auditLog);

            log.debug("Audit log created: action={}, resource={}/{}, user={}, company={}",
                    action.name(), resourceType.name(), resourceId, userId, companyId);

        } catch (Exception e) {
            log.error("Failed to create audit log: action={}, error={}", action.name(), e.getMessage());
        }
    }

    @Async
    public void logActionAsync(
            Long companyId,
            Long userId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            Map<String, Object> metadata) {
        logAction(companyId, userId, action, resourceType, resourceId, metadata, null);
    }

    public void logAuthorizationDenied(
            Long companyId,
            Long userId,
            String requiredPermission,
            String attemptedResource,
            HttpServletRequest request) {
        Map<String, Object> metadata = Map.of(
                "requiredPermission", requiredPermission,
                "attemptedResource", attemptedResource,
                "denied", true);

        logAction(companyId, userId, AuditAction.AUTHORIZATION_DENIED,
                ResourceType.DASHBOARD, attemptedResource, metadata, request);
    }

    public void logWidgetAccessDenied(
            Long companyId,
            Long userId,
            String widgetKey,
            String userRole) {
        Map<String, Object> metadata = Map.of(
                "widgetKey", widgetKey,
                "userRole", userRole != null ? userRole : "unknown");

        logAction(companyId, userId, AuditAction.WIDGET_ACCESS_DENIED,
                ResourceType.WIDGET, widgetKey, metadata, null);
    }

    public void logManualRefresh(
            Long companyId,
            Long userId,
            AuditAction action,
            String jobId,
            HttpServletRequest request) {
        Map<String, Object> metadata = jobId != null ? Map.of("jobId", jobId) : Map.of();
        logAction(companyId, userId, action, ResourceType.ETL_JOB, jobId, metadata, request);
    }

    public void logETLExecution(
            Long companyId,
            AuditAction action,
            String jobId,
            Long durationMs,
            Integer rowsProcessed,
            String errorMessage) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        if (durationMs != null) {
            metadata.put("durationMs", durationMs);
        }
        if (rowsProcessed != null) {
            metadata.put("rowsProcessed", rowsProcessed);
        }
        if (errorMessage != null) {
            metadata.put("errorMessage", truncate(errorMessage, 500));
        }
        metadata.put("executionTime", Instant.now().toString());

        logAction(companyId, null, action, ResourceType.ETL_JOB, jobId, metadata, null);
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }

    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return truncate(userAgent, 500);
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return Map.of();
        }

        java.util.Map<String, Object> sanitized = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.contains("password") || key.contains("secret") ||
                    key.contains("token") || key.contains("key") ||
                    key.contains("credential")) {
                sanitized.put(entry.getKey(), "[REDACTED]");
            } else {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized;
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit metadata: {}", e.getMessage());
            return "{}";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}

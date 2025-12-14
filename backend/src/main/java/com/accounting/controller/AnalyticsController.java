package com.accounting.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.accounting.entity.User;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.MetabaseService;
import com.accounting.service.analytics.AnalyticsAuditService;
import com.accounting.service.analytics.AnalyticsAuditService.AuditAction;
import com.accounting.service.analytics.AnalyticsAuditService.ResourceType;
import com.accounting.service.analytics.AnalyticsAuthorizationService;
import com.accounting.service.analytics.AnalyticsAuthorizationService.WidgetScope;
import com.accounting.service.analytics.MetabaseEmbedService.MetabaseEmbedConfig;
import com.accounting.service.analytics.MetabaseEmbedServiceImpl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for analytics and BI dashboard operations.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "BI Dashboard and Analytics APIs")
public class AnalyticsController {

    private final MetabaseService metabaseService;
    private final MetabaseEmbedServiceImpl metabaseEmbedService;
    private final UserRepository userRepository;
    private final AnalyticsAuditService auditService;
    private final AnalyticsAuthorizationService authorizationService;

    public AnalyticsController(
            MetabaseService metabaseService,
            MetabaseEmbedServiceImpl metabaseEmbedService,
            UserRepository userRepository,
            AnalyticsAuditService auditService,
            AnalyticsAuthorizationService authorizationService) {
        this.metabaseService = metabaseService;
        this.metabaseEmbedService = metabaseEmbedService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.authorizationService = authorizationService;
    }

    /**
     * Generate a signed JWT token for embedded Metabase dashboard.
     * The token includes the current company context for data filtering.
     *
     * @param dashboardId The Metabase dashboard ID
     * @return JWT token for embedding
     */
    @GetMapping("/metabase/token/{dashboardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Generate Metabase embedding token",
               description = "Generate a signed JWT token for embedding Metabase dashboards with company-scoped data")
    public ResponseEntity<Map<String, String>> getEmbeddingToken(
        @PathVariable Integer dashboardId
    ) {
        String token = metabaseService.generateEmbeddingToken(dashboardId);
        return ResponseEntity.ok(Map.of(
            "token", token,
            "dashboardId", dashboardId.toString()
        ));
    }

    /**
     * Get the complete iframe URL for embedded Metabase dashboard.
     *
     * @param dashboardId The Metabase dashboard ID
     * @return Full iframe URL with signed token
     */
    @GetMapping("/metabase/url/{dashboardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT')")
    @Operation(summary = "Get embedded dashboard URL",
               description = "Get the complete iframe URL for embedding Metabase dashboards")
    public ResponseEntity<Map<String, String>> getEmbeddedDashboardUrl(
        @PathVariable Integer dashboardId
    ) {
        String url = metabaseService.getEmbeddedDashboardUrl(dashboardId);
        return ResponseEntity.ok(Map.of(
            "url", url,
            "dashboardId", dashboardId.toString()
        ));
    }

    /**
     * Get embed configuration for Metabase SDK embedding.
     * Returns the config needed for MetabaseProvider initialization.
     */
    @GetMapping("/metabase/embed/dashboard/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    @Operation(summary = "Get Metabase embed configuration",
               description = "Get configuration for Metabase SDK embedding with JWT SSO")
    public ResponseEntity<EmbedConfigResponse> getEmbedConfig(@PathVariable String key) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        MetabaseEmbedConfig config = metabaseEmbedService.generateEmbedConfig(key, user, companyId);

        return ResponseEntity.ok(new EmbedConfigResponse(
                config.metabaseInstanceUrl(),
                config.authConfig().enabled(),
                config.authConfig().authProviderUri().uri(),
                config.authConfig().authType().name(),
                metabaseEmbedService.getTokenExpiryMinutes(),
                metabaseEmbedService.getRefreshBeforeExpiryMinutes()
        ));
    }

    /**
     * Generate JWT token for Metabase SSO.
     * Called by the Metabase SDK to authenticate the user.
     */
    @GetMapping("/metabase/sso/token")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    @Operation(summary = "Generate Metabase SSO JWT token",
               description = "Generate a JWT token for Metabase SSO with locked company_id")
    public ResponseEntity<Map<String, Object>> getSsoToken(HttpServletRequest request) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        String token = metabaseEmbedService.generateJwtToken(user, companyId);

        auditService.logAction(companyId, userId, AuditAction.SSO_TOKEN_GENERATED,
                ResourceType.JWT, null, null, request);

        return ResponseEntity.ok(Map.of(
                "jwt", token,
                "expiresInMinutes", metabaseEmbedService.getTokenExpiryMinutes()
        ));
    }

    /**
     * Log frontend dashboard view event for audit purposes.
     */
    @PostMapping("/metabase/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    @Operation(summary = "Log analytics event",
               description = "Log frontend analytics events for audit trail")
    public ResponseEntity<Void> logEvent(@RequestBody EventRequest eventRequest, HttpServletRequest request) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        AuditAction action = mapEventTypeToAction(eventRequest.eventType());
        ResourceType resourceType = mapResourceType(eventRequest.resourceType());

        auditService.logAction(companyId, userId, action, resourceType,
                eventRequest.resourceId(), null, request);

        return ResponseEntity.ok().build();
    }

    /**
     * List available dashboards for the current user.
     */
    @GetMapping("/metabase/dashboards")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    @Operation(summary = "List available dashboards",
               description = "Get list of dashboards available to the current user based on role")
    public ResponseEntity<List<DashboardInfo>> listDashboards() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<DashboardInfo> dashboards = getDashboardsForRole(user.getRole());
        return ResponseEntity.ok(dashboards);
    }

    /**
     * Get allowed widgets for the current user based on role.
     */
    @GetMapping("/metabase/widgets")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    @Operation(summary = "List allowed widgets",
               description = "Get list of widgets the current user can access based on role")
    public ResponseEntity<WidgetPermissionsResponse> getWidgetPermissions() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        WidgetScope scope = authorizationService.getWidgetScope(user);
        List<String> allowedWidgets = authorizationService.getAllowedWidgets(user);
        boolean canRefresh = authorizationService.canManualRefresh(user);
        boolean canViewETL = authorizationService.canViewETLStatus(user);

        return ResponseEntity.ok(new WidgetPermissionsResponse(
                scope.name(),
                allowedWidgets,
                canRefresh,
                canViewETL
        ));
    }

    /**
     * Check widget access for specific widget.
     */
    @GetMapping("/metabase/widgets/{widgetKey}/access")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    @Operation(summary = "Check widget access",
               description = "Check if user can access a specific widget")
    public ResponseEntity<Map<String, Object>> checkWidgetAccess(
            @PathVariable String widgetKey,
            HttpServletRequest request) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        boolean allowed = authorizationService.canViewWidget(user, widgetKey);

        if (!allowed) {
            auditService.logWidgetAccessDenied(companyId, userId, widgetKey, user.getRole());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "allowed", false,
                            "widgetKey", widgetKey,
                            "message", "Access denied to widget: " + widgetKey
                    ));
        }

        auditService.logAction(companyId, userId, AuditAction.WIDGET_ACCESSED,
                ResourceType.WIDGET, widgetKey, null, request);

        return ResponseEntity.ok(Map.of(
                "allowed", true,
                "widgetKey", widgetKey
        ));
    }

    private List<DashboardInfo> getDashboardsForRole(String role) {
        DashboardInfo financialOverview = new DashboardInfo(
                "financial-overview",
                "Financial Overview",
                "Revenue, expenses, AR/AP, and cash position",
                List.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_GENERAL", "FINANCE", "ACCOUNTANT")
        );

        DashboardInfo arDashboard = new DashboardInfo(
                "ar-dashboard",
                "Accounts Receivable",
                "AR aging, top debtors, collections",
                List.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_AR")
        );

        DashboardInfo apDashboard = new DashboardInfo(
                "ap-dashboard",
                "Accounts Payable",
                "AP aging, top creditors, payments",
                List.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_AP")
        );

        DashboardInfo cashDashboard = new DashboardInfo(
                "cash-dashboard",
                "Cash Position",
                "Cash flow, bank balances, trends",
                List.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "CASHIER")
        );

        List<DashboardInfo> allDashboards = List.of(financialOverview, arDashboard, apDashboard, cashDashboard);

        String normalizedRole = role != null ? role.toUpperCase() : "";
        return allDashboards.stream()
                .filter(d -> d.allowedRoles().contains(normalizedRole) || normalizedRole.equals("ADMIN"))
                .toList();
    }

    private AuditAction mapEventTypeToAction(String eventType) {
        if (eventType == null) {
            return AuditAction.DASHBOARD_VIEW_LOADED;
        }
        return switch (eventType.toUpperCase()) {
            case "VIEW_LOADED", "DASHBOARD_VIEW_LOADED" -> AuditAction.DASHBOARD_VIEW_LOADED;
            case "VIEW_ERROR", "DASHBOARD_VIEW_ERROR" -> AuditAction.DASHBOARD_VIEW_ERROR;
            case "EXPORT_TRIGGERED" -> AuditAction.EXPORT_TRIGGERED;
            case "WIDGET_ACCESSED" -> AuditAction.WIDGET_ACCESSED;
            default -> AuditAction.DASHBOARD_VIEW_LOADED;
        };
    }

    private ResourceType mapResourceType(String resourceType) {
        if (resourceType == null) {
            return ResourceType.DASHBOARD;
        }
        return switch (resourceType.toUpperCase()) {
            case "WIDGET" -> ResourceType.WIDGET;
            case "ETL_JOB" -> ResourceType.ETL_JOB;
            case "JWT" -> ResourceType.JWT;
            case "EXPORT" -> ResourceType.EXPORT;
            default -> ResourceType.DASHBOARD;
        };
    }

    public record EmbedConfigResponse(
            String metabaseInstanceUrl,
            boolean authEnabled,
            String authProviderUri,
            String authType,
            long tokenExpiryMinutes,
            long refreshBeforeExpiryMinutes) {}

    public record EventRequest(
            String eventType,
            String resourceType,
            String resourceId) {}

    public record DashboardInfo(
            String key,
            String name,
            String description,
            List<String> allowedRoles) {}

    public record WidgetPermissionsResponse(
            String scope,
            List<String> allowedWidgets,
            boolean canRefresh,
            boolean canViewETLStatus) {}
}

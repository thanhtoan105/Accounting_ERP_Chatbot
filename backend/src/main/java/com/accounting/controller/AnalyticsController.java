package com.accounting.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.accounting.config.DashboardProfileConfig;
import com.accounting.dto.analytics.WidgetPermissionsDTO;
import com.accounting.entity.User;
import com.accounting.entity.analytics.WidgetPermissionConfig;
import com.accounting.entity.analytics.WidgetType;
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
import com.accounting.service.analytics.MetabaseProvisioningService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for analytics and BI dashboard operations.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Dashboard analytics, Metabase embedding, and data freshness APIs")
public class AnalyticsController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AnalyticsController.class);

    private final MetabaseService metabaseService;
    private final MetabaseEmbedServiceImpl metabaseEmbedService;
    private final MetabaseProvisioningService metabaseProvisioningService;
    private final UserRepository userRepository;
    private final AnalyticsAuditService auditService;
    private final AnalyticsAuthorizationService authorizationService;
    private final WidgetPermissionConfig widgetPermissionConfig;
    private final DashboardProfileConfig dashboardProfileConfig;

    public AnalyticsController(
            MetabaseService metabaseService,
            MetabaseEmbedServiceImpl metabaseEmbedService,
            MetabaseProvisioningService metabaseProvisioningService,
            UserRepository userRepository,
            AnalyticsAuditService auditService,
            AnalyticsAuthorizationService authorizationService,
            WidgetPermissionConfig widgetPermissionConfig,
            DashboardProfileConfig dashboardProfileConfig) {
        this.metabaseService = metabaseService;
        this.metabaseEmbedService = metabaseEmbedService;
        this.metabaseProvisioningService = metabaseProvisioningService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.authorizationService = authorizationService;
        this.widgetPermissionConfig = widgetPermissionConfig;
        this.dashboardProfileConfig = dashboardProfileConfig;
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
    @Operation(
            summary = "Get Metabase embed configuration",
            description = "Returns signed embed URL and config for the specified dashboard. Uses locked company_id from JWT.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Embed config returned"),
        @ApiResponse(responseCode = "403", description = "User not authorized for this dashboard"),
        @ApiResponse(responseCode = "404", description = "Dashboard not found")
    })
    public ResponseEntity<EmbedConfigResponse> getEmbedConfig(@PathVariable String key) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        try {
            metabaseProvisioningService.provisionTenant(companyId);
            metabaseProvisioningService.provisionUser(user, companyId);
        } catch (Exception e) {
            log.warn("Auto-provisioning failed for company {} user {}: {}",
                    companyId, userId, e.getMessage());
        }

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

        try {
            metabaseProvisioningService.provisionTenant(companyId);
            metabaseProvisioningService.provisionUser(user, companyId);
        } catch (Exception e) {
            log.warn("Auto-provisioning failed for company {} user {}: {}",
                    companyId, userId, e.getMessage());
        }

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
     * Get widget permissions using WidgetType enum.
     * Returns accessible widgets based on user's role per Vietnamese TT200 structure.
     */
    @GetMapping("/widgets/permissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    @Operation(
            summary = "Get widget permissions for current user",
            description = "Returns the list of widgets the current user can access based on their role per Vietnamese TT200 structure.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Widget permissions returned")})
    public ResponseEntity<WidgetPermissionsDTO> getWidgetPermissionsByType() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Set<String> userRoles = Set.of(user.getRole());
        var accessibleWidgets = widgetPermissionConfig.getAccessibleWidgets(userRoles);
        boolean canRefresh = widgetPermissionConfig.canRefresh(userRoles);
        boolean canExport = widgetPermissionConfig.canExport(userRoles);
        boolean isFullAccess = widgetPermissionConfig.hasFullAccess(userRoles);

        return ResponseEntity.ok(new WidgetPermissionsDTO(
                accessibleWidgets,
                canRefresh,
                canExport,
                isFullAccess
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

    /**
     * Get the dashboard configuration for the current user based on their role.
     * Returns the appropriate dashboard ID and accessible widget types.
     */
    @GetMapping("/metabase/dashboard-for-role")
    @PreAuthorize("hasAnyRole('ADMIN', 'CFO', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT_GENERAL', 'ACCOUNTANT_AR', 'ACCOUNTANT_AP', 'CASHIER', 'FINANCE', 'ACCOUNTANT')")
    @Operation(
            summary = "Get dashboard configuration for user role",
            description = "Returns the dashboard ID and accessible widgets based on the current user's role per AC 8.0.18")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dashboard configuration returned"),
        @ApiResponse(responseCode = "403", description = "User not authorized")
    })
    public ResponseEntity<DashboardConfigResponse> getDashboardForRole(HttpServletRequest request) {
        Long companyId = CompanyContext.getCompanyId();
        Long userId = SecurityUtils.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Integer dashboardId = dashboardProfileConfig.getDashboardIdForRole(user.getRole());
        Set<String> userRoles = Set.of(user.getRole());
        Set<WidgetType> accessibleWidgets = widgetPermissionConfig.getAccessibleWidgets(userRoles);
        boolean isFullAccess = widgetPermissionConfig.hasFullAccess(userRoles);

        auditService.logAction(companyId, userId, AuditAction.DASHBOARD_VIEW_LOADED,
                ResourceType.DASHBOARD, String.valueOf(dashboardId), null, request);

        return ResponseEntity.ok(new DashboardConfigResponse(
                dashboardId,
                accessibleWidgets.stream().map(WidgetType::name).toList(),
                isFullAccess,
                getDashboardKeyForId(dashboardId)
        ));
    }

    private String getDashboardKeyForId(Integer dashboardId) {
        // Dashboard IDs mapped to Metabase:
        // ID 4 = Financial Overview (main dashboard for all roles)
        return switch (dashboardId) {
            case 4 -> "financial-overview";
            default -> "financial-overview";
        };
    }

    private List<DashboardInfo> getDashboardsForRole(String role) {
        // All roles use the same Financial Overview dashboard (ID 4) in Metabase
        // Widget-level filtering is handled by WidgetPermissionConfig
        DashboardInfo financialOverview = new DashboardInfo(
                "financial-overview",
                "Financial Overview",
                "Revenue, expenses, AR/AP, and cash position",
                List.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_GENERAL", "FINANCE", "ACCOUNTANT",
                        "ACCOUNTANT_AR", "ACCOUNTANT_AP", "CASHIER"),
                4
        );

        List<DashboardInfo> allDashboards = List.of(financialOverview);

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
            List<String> allowedRoles,
            Integer metabaseDashboardId) {}

    public record WidgetPermissionsResponse(
            String scope,
            List<String> allowedWidgets,
            boolean canRefresh,
            boolean canViewETLStatus) {}

    public record DashboardConfigResponse(
            Integer dashboardId,
            List<String> accessibleWidgets,
            boolean isFullAccess,
            String dashboardKey) {}
}

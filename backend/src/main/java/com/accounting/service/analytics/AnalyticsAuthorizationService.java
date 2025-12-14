package com.accounting.service.analytics;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.accounting.entity.User;

@Service
public class AnalyticsAuthorizationService {

    public enum AnalyticsPermission {
        VIEW_DASHBOARD,
        VIEW_ALL_WIDGETS,
        VIEW_SUMMARY_WIDGETS,
        VIEW_AR_WIDGETS,
        VIEW_AP_WIDGETS,
        VIEW_CASH_WIDGETS,
        MANUAL_REFRESH,
        VIEW_ETL_STATUS,
        ANALYTICS_ADMIN
    }

    public enum WidgetScope {
        ALL,
        SUMMARY,
        AR_ONLY,
        AP_ONLY,
        CASH_ONLY
    }

    private static final Map<String, Set<AnalyticsPermission>> ROLE_PERMISSIONS = Map.of(
            "ADMIN", Set.of(
                    AnalyticsPermission.VIEW_DASHBOARD,
                    AnalyticsPermission.VIEW_ALL_WIDGETS,
                    AnalyticsPermission.MANUAL_REFRESH,
                    AnalyticsPermission.VIEW_ETL_STATUS,
                    AnalyticsPermission.ANALYTICS_ADMIN),
            "CFO", Set.of(
                    AnalyticsPermission.VIEW_DASHBOARD,
                    AnalyticsPermission.VIEW_ALL_WIDGETS,
                    AnalyticsPermission.VIEW_ETL_STATUS),
            "CHIEF_ACCOUNTANT", Set.of(
                    AnalyticsPermission.VIEW_DASHBOARD,
                    AnalyticsPermission.VIEW_ALL_WIDGETS,
                    AnalyticsPermission.MANUAL_REFRESH,
                    AnalyticsPermission.VIEW_ETL_STATUS),
            "ACCOUNTANT_GENERAL", Set.of(
                    AnalyticsPermission.VIEW_DASHBOARD,
                    AnalyticsPermission.VIEW_SUMMARY_WIDGETS),
            "ACCOUNTANT_AR", Set.of(
                    AnalyticsPermission.VIEW_DASHBOARD,
                    AnalyticsPermission.VIEW_AR_WIDGETS),
            "ACCOUNTANT_AP", Set.of(
                    AnalyticsPermission.VIEW_DASHBOARD,
                    AnalyticsPermission.VIEW_AP_WIDGETS),
            "CASHIER", Set.of(
                    AnalyticsPermission.VIEW_DASHBOARD,
                    AnalyticsPermission.VIEW_CASH_WIDGETS),
            "ACCOUNTANT", Set.of(
                    AnalyticsPermission.VIEW_DASHBOARD,
                    AnalyticsPermission.VIEW_SUMMARY_WIDGETS),
            "FINANCE", Set.of(
                    AnalyticsPermission.VIEW_DASHBOARD,
                    AnalyticsPermission.VIEW_SUMMARY_WIDGETS));

    private static final Map<String, WidgetScope> ROLE_WIDGET_SCOPE = Map.of(
            "ADMIN", WidgetScope.ALL,
            "CFO", WidgetScope.ALL,
            "CHIEF_ACCOUNTANT", WidgetScope.ALL,
            "ACCOUNTANT_GENERAL", WidgetScope.SUMMARY,
            "ACCOUNTANT_AR", WidgetScope.AR_ONLY,
            "ACCOUNTANT_AP", WidgetScope.AP_ONLY,
            "CASHIER", WidgetScope.CASH_ONLY,
            "ACCOUNTANT", WidgetScope.SUMMARY,
            "FINANCE", WidgetScope.SUMMARY);

    private static final Set<String> REFRESH_ALLOWED_ROLES = Set.of("ADMIN", "CHIEF_ACCOUNTANT");
    private static final Set<String> ETL_STATUS_ALLOWED_ROLES = Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT");
    private static final Set<String> ADMIN_ALLOWED_ROLES = Set.of("ADMIN");

    public boolean hasPermission(User user, AnalyticsPermission permission) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = normalizeRole(user.getRole());
        Set<AnalyticsPermission> permissions = ROLE_PERMISSIONS.get(role);

        if (permissions == null) {
            return false;
        }

        return permissions.contains(permission);
    }

    public boolean hasPermission(String role, AnalyticsPermission permission) {
        if (role == null) {
            return false;
        }

        String normalizedRole = normalizeRole(role);
        Set<AnalyticsPermission> permissions = ROLE_PERMISSIONS.get(normalizedRole);

        if (permissions == null) {
            return false;
        }

        return permissions.contains(permission);
    }

    public boolean canViewDashboard(User user) {
        return hasPermission(user, AnalyticsPermission.VIEW_DASHBOARD);
    }

    public boolean canManualRefresh(User user) {
        return hasPermission(user, AnalyticsPermission.MANUAL_REFRESH);
    }

    public boolean canManualRefresh(String role) {
        return REFRESH_ALLOWED_ROLES.contains(normalizeRole(role));
    }

    public boolean canViewETLStatus(User user) {
        return hasPermission(user, AnalyticsPermission.VIEW_ETL_STATUS);
    }

    public boolean canViewETLStatus(String role) {
        return ETL_STATUS_ALLOWED_ROLES.contains(normalizeRole(role));
    }

    public boolean isAnalyticsAdmin(User user) {
        return hasPermission(user, AnalyticsPermission.ANALYTICS_ADMIN);
    }

    public boolean isAnalyticsAdmin(String role) {
        return ADMIN_ALLOWED_ROLES.contains(normalizeRole(role));
    }

    public WidgetScope getWidgetScope(User user) {
        if (user == null || user.getRole() == null) {
            return WidgetScope.SUMMARY;
        }

        String role = normalizeRole(user.getRole());
        return ROLE_WIDGET_SCOPE.getOrDefault(role, WidgetScope.SUMMARY);
    }

    public WidgetScope getWidgetScope(String role) {
        if (role == null) {
            return WidgetScope.SUMMARY;
        }

        String normalizedRole = normalizeRole(role);
        return ROLE_WIDGET_SCOPE.getOrDefault(normalizedRole, WidgetScope.SUMMARY);
    }

    public List<String> getAllowedWidgets(User user) {
        WidgetScope scope = getWidgetScope(user);
        return getAllowedWidgetsForScope(scope);
    }

    public List<String> getAllowedWidgets(String role) {
        WidgetScope scope = getWidgetScope(role);
        return getAllowedWidgetsForScope(scope);
    }

    private List<String> getAllowedWidgetsForScope(WidgetScope scope) {
        return switch (scope) {
            case ALL -> List.of(
                    "revenue-vs-expenses",
                    "ar-ap-balances",
                    "cash-position",
                    "top-5-debtors",
                    "top-5-creditors",
                    "period-summary");
            case SUMMARY -> List.of("period-summary");
            case AR_ONLY -> List.of("ar-ap-balances", "top-5-debtors");
            case AP_ONLY -> List.of("ar-ap-balances", "top-5-creditors");
            case CASH_ONLY -> List.of("cash-position");
        };
    }

    public boolean canViewWidget(User user, String widgetKey) {
        List<String> allowedWidgets = getAllowedWidgets(user);
        return allowedWidgets.contains(widgetKey);
    }

    public boolean canViewWidget(String role, String widgetKey) {
        List<String> allowedWidgets = getAllowedWidgets(role);
        return allowedWidgets.contains(widgetKey);
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.toUpperCase().replace("-", "_");
    }

    public Set<AnalyticsPermission> getPermissions(String role) {
        if (role == null) {
            return Set.of();
        }
        String normalizedRole = normalizeRole(role);
        return ROLE_PERMISSIONS.getOrDefault(normalizedRole, Set.of());
    }
}

package com.accounting.entity.analytics;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Configuration for widget-level access control based on Vietnamese TT200 accounting roles.
 * Defines which roles can access which analytics widgets.
 */
@Component
public class WidgetPermissionConfig {

    private static final Map<WidgetType, Set<String>> WIDGET_ROLE_PERMISSIONS = Map.of(
            WidgetType.REVENUE_EXPENSE,
            Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_GENERAL"),
            WidgetType.AR_BALANCES,
            Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_GENERAL", "ACCOUNTANT_AR"),
            WidgetType.AP_BALANCES,
            Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_GENERAL", "ACCOUNTANT_AP"),
            WidgetType.CASH_POSITION,
            Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "CASHIER"),
            WidgetType.TOP_DEBTORS,
            Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_AR"),
            WidgetType.TOP_CREDITORS,
            Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_AP"),
            WidgetType.PERIOD_SUMMARY,
            Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_GENERAL", "FINANCE", "ACCOUNTANT"));

    private static final Set<String> FULL_ACCESS_ROLES = Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT");
    private static final Set<String> REFRESH_ALLOWED_ROLES = Set.of("ADMIN", "CHIEF_ACCOUNTANT");
    private static final Set<String> EXPORT_ALLOWED_ROLES =
            Set.of("ADMIN", "CFO", "CHIEF_ACCOUNTANT", "ACCOUNTANT_GENERAL");

    /**
     * Check if user with given roles can access a specific widget.
     *
     * @param widget the widget type to check
     * @param userRoles the user's roles
     * @return true if access is allowed
     */
    public boolean canAccessWidget(WidgetType widget, Set<String> userRoles) {
        if (widget == null || userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        Set<String> normalizedRoles =
                userRoles.stream().map(this::normalizeRole).collect(Collectors.toSet());

        Set<String> allowedRoles = WIDGET_ROLE_PERMISSIONS.get(widget);
        if (allowedRoles == null) {
            return false;
        }

        return normalizedRoles.stream().anyMatch(allowedRoles::contains);
    }

    /**
     * Get all widgets accessible by the given user roles.
     *
     * @param userRoles the user's roles
     * @return set of accessible widget types
     */
    public Set<WidgetType> getAccessibleWidgets(Set<String> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return EnumSet.noneOf(WidgetType.class);
        }

        Set<String> normalizedRoles =
                userRoles.stream().map(this::normalizeRole).collect(Collectors.toSet());

        return WIDGET_ROLE_PERMISSIONS.entrySet().stream()
                .filter(entry -> normalizedRoles.stream().anyMatch(entry.getValue()::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(WidgetType.class)));
    }

    /**
     * Check if user has full access to all widgets.
     *
     * @param userRoles the user's roles
     * @return true if user has full access
     */
    public boolean hasFullAccess(Set<String> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        Set<String> normalizedRoles =
                userRoles.stream().map(this::normalizeRole).collect(Collectors.toSet());

        return normalizedRoles.stream().anyMatch(FULL_ACCESS_ROLES::contains);
    }

    /**
     * Check if user can refresh analytics data.
     *
     * @param userRoles the user's roles
     * @return true if refresh is allowed
     */
    public boolean canRefresh(Set<String> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        Set<String> normalizedRoles =
                userRoles.stream().map(this::normalizeRole).collect(Collectors.toSet());

        return normalizedRoles.stream().anyMatch(REFRESH_ALLOWED_ROLES::contains);
    }

    /**
     * Check if user can export analytics data.
     *
     * @param userRoles the user's roles
     * @return true if export is allowed
     */
    public boolean canExport(Set<String> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return false;
        }

        Set<String> normalizedRoles =
                userRoles.stream().map(this::normalizeRole).collect(Collectors.toSet());

        return normalizedRoles.stream().anyMatch(EXPORT_ALLOWED_ROLES::contains);
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.toUpperCase().replace("-", "_");
    }
}

package com.accounting.service.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.accounting.entity.User;
import com.accounting.repository.dashboard.WidgetRolePermissionRepository;
import com.accounting.service.analytics.AnalyticsAuthorizationService;
import com.accounting.service.analytics.AnalyticsAuthorizationService.WidgetScope;

@Service
public class WidgetPermissionService {

    private final WidgetRolePermissionRepository widgetRolePermissionRepository;
    private final AnalyticsAuthorizationService analyticsAuthorizationService;

    public WidgetPermissionService(
            WidgetRolePermissionRepository widgetRolePermissionRepository,
            AnalyticsAuthorizationService analyticsAuthorizationService) {
        this.widgetRolePermissionRepository = widgetRolePermissionRepository;
        this.analyticsAuthorizationService = analyticsAuthorizationService;
    }

    public List<String> getAllowedWidgets(User user) {
        if (user == null || user.getRole() == null) {
            return List.of();
        }

        String normalizedRole = normalizeRole(user.getRole());
        List<String> dbPermissions = widgetRolePermissionRepository.findAllowedWidgetKeysByRole(normalizedRole);

        if (!dbPermissions.isEmpty()) {
            return dbPermissions;
        }

        return analyticsAuthorizationService.getAllowedWidgets(user);
    }

    public List<String> getAllowedWidgets(String role) {
        if (role == null) {
            return List.of();
        }

        String normalizedRole = normalizeRole(role);
        List<String> dbPermissions = widgetRolePermissionRepository.findAllowedWidgetKeysByRole(normalizedRole);

        if (!dbPermissions.isEmpty()) {
            return dbPermissions;
        }

        return analyticsAuthorizationService.getAllowedWidgets(role);
    }

    public boolean canViewWidget(User user, String widgetKey) {
        if (user == null || user.getRole() == null || widgetKey == null) {
            return false;
        }

        String normalizedRole = normalizeRole(user.getRole());
        if (widgetRolePermissionRepository.existsByWidgetKeyAndRoleAndCanViewTrue(widgetKey, normalizedRole)) {
            return true;
        }

        return analyticsAuthorizationService.canViewWidget(user, widgetKey);
    }

    public boolean canViewWidget(String role, String widgetKey) {
        if (role == null || widgetKey == null) {
            return false;
        }

        String normalizedRole = normalizeRole(role);
        if (widgetRolePermissionRepository.existsByWidgetKeyAndRoleAndCanViewTrue(widgetKey, normalizedRole)) {
            return true;
        }

        return analyticsAuthorizationService.canViewWidget(role, widgetKey);
    }

    public WidgetScope getWidgetScope(User user) {
        return analyticsAuthorizationService.getWidgetScope(user);
    }

    public WidgetScope getWidgetScope(String role) {
        return analyticsAuthorizationService.getWidgetScope(role);
    }

    public boolean canManualRefresh(User user) {
        return analyticsAuthorizationService.canManualRefresh(user);
    }

    public boolean canViewETLStatus(User user) {
        return analyticsAuthorizationService.canViewETLStatus(user);
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.toLowerCase().replace("-", "_");
    }
}

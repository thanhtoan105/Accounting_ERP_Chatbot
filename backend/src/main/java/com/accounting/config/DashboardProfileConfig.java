package com.accounting.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for role-based dashboard profile mapping.
 * Maps user roles to specific Metabase dashboard IDs for role-based widget rendering.
 * 
 * <p>Per AC 8.0.18:
 * <ul>
 *   <li>ADMIN, CFO, CHIEF_ACCOUNTANT → All 6 widgets (Dashboard 1)</li>
 *   <li>ACCOUNTANT_GENERAL → Summary only (Dashboard 2)</li>
 *   <li>ACCOUNTANT_AR → AR widgets only (Dashboard 3)</li>
 *   <li>ACCOUNTANT_AP → AP widgets only (Dashboard 4)</li>
 *   <li>CASHIER → Cash widgets only (Dashboard 5)</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "analytics.dashboards")
public class DashboardProfileConfig {

    private Map<String, Integer> roleProfiles = new HashMap<>();
    private Integer defaultDashboardId = 1;

    public Map<String, Integer> getRoleProfiles() {
        return roleProfiles;
    }

    public void setRoleProfiles(Map<String, Integer> roleProfiles) {
        this.roleProfiles = roleProfiles;
    }

    public Integer getDefaultDashboardId() {
        return defaultDashboardId;
    }

    public void setDefaultDashboardId(Integer defaultDashboardId) {
        this.defaultDashboardId = defaultDashboardId;
    }

    /**
     * Get the dashboard ID for a specific role.
     *
     * @param role the user's role
     * @return the dashboard ID for the role, or the default dashboard ID if no mapping exists
     */
    public Integer getDashboardIdForRole(String role) {
        if (role == null || role.isBlank()) {
            return defaultDashboardId;
        }
        String normalizedRole = role.toUpperCase().replace("-", "_");
        return roleProfiles.getOrDefault(normalizedRole, defaultDashboardId);
    }

    /**
     * Check if the role has a specific dashboard profile configured.
     *
     * @param role the user's role
     * @return true if a specific profile exists for this role
     */
    public boolean hasProfileForRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String normalizedRole = role.toUpperCase().replace("-", "_");
        return roleProfiles.containsKey(normalizedRole);
    }
}

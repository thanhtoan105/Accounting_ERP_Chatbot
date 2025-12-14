package com.accounting.service.analytics;

import java.util.Optional;

public interface TenantAnalyticsProvisioningService {

    ProvisioningResult provisionTenant(Long companyId);

    ProvisioningResult deprovisionTenant(Long companyId);

    boolean isTenantProvisioned(Long companyId);

    Optional<TenantCredentials> getTenantCredentials(Long companyId);

    record ProvisioningResult(
            boolean success,
            String roleName,
            String schemaName,
            String message) {

        public static ProvisioningResult success(String roleName, String schemaName) {
            return new ProvisioningResult(true, roleName, schemaName, "Provisioning successful");
        }

        public static ProvisioningResult failure(String message) {
            return new ProvisioningResult(false, null, null, message);
        }

        public static ProvisioningResult alreadyExists(String roleName, String schemaName) {
            return new ProvisioningResult(true, roleName, schemaName, "Tenant already provisioned");
        }
    }

    record TenantCredentials(
            String roleName,
            String schemaName,
            String password,
            String jdbcUrl) {}
}

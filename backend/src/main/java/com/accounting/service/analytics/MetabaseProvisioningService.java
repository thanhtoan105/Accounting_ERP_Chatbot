package com.accounting.service.analytics;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.accounting.entity.User;
import com.accounting.integration.metabase.dto.MetabaseUserResponse;

public interface MetabaseProvisioningService {

    ProvisioningResult provisionTenant(Long companyId);

    UserProvisioningResult provisionUser(User user, Long companyId);

    UserProvisioningResult syncUser(User user, Long companyId);

    void deactivateUser(User user);

    void assignUserGroups(User user, Long companyId, Set<String> roles);

    boolean isTenantProvisioned(Long companyId);

    Optional<MetabaseUserResponse> findMetabaseUser(String email);

    record ProvisioningResult(
            boolean success,
            Long databaseId,
            Long groupId,
            Long collectionId,
            String message) {

        public static ProvisioningResult success(Long databaseId, Long groupId, Long collectionId) {
            return new ProvisioningResult(true, databaseId, groupId, collectionId, "Tenant provisioned successfully");
        }

        public static ProvisioningResult failure(String message) {
            return new ProvisioningResult(false, null, null, null, message);
        }

        public static ProvisioningResult alreadyExists(Long databaseId, Long groupId, Long collectionId) {
            return new ProvisioningResult(true, databaseId, groupId, collectionId, "Tenant already provisioned");
        }
    }

    record UserProvisioningResult(
            boolean success,
            Long metabaseUserId,
            List<Long> groupIds,
            String message) {

        public static UserProvisioningResult success(Long userId, List<Long> groupIds) {
            return new UserProvisioningResult(true, userId, groupIds, "User provisioned successfully");
        }

        public static UserProvisioningResult failure(String message) {
            return new UserProvisioningResult(false, null, null, message);
        }

        public static UserProvisioningResult updated(Long userId, List<Long> groupIds) {
            return new UserProvisioningResult(true, userId, groupIds, "User updated successfully");
        }
    }
}

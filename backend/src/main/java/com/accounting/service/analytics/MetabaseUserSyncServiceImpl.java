package com.accounting.service.analytics;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accounting.entity.User;
import com.accounting.event.UserCreatedEvent;
import com.accounting.event.UserDeactivatedEvent;
import com.accounting.event.UserRoleChangedEvent;
import com.accounting.event.UserUpdatedEvent;
import com.accounting.service.analytics.AnalyticsAuditService.AuditAction;
import com.accounting.service.analytics.AnalyticsAuditService.ResourceType;
import com.accounting.service.analytics.MetabaseProvisioningService.UserProvisioningResult;

@Service
public class MetabaseUserSyncServiceImpl implements MetabaseUserSyncService {

    private static final Logger log = LoggerFactory.getLogger(MetabaseUserSyncServiceImpl.class);

    private final MetabaseProvisioningService metabaseProvisioningService;
    private final AnalyticsAuditService auditService;

    public MetabaseUserSyncServiceImpl(
            MetabaseProvisioningService metabaseProvisioningService,
            AnalyticsAuditService auditService) {
        this.metabaseProvisioningService = metabaseProvisioningService;
        this.auditService = auditService;
    }

    @Override
    public UserProvisioningResult syncUser(User user) {
        log.info("Syncing user {} to Metabase", user.getEmail());

        Long companyId = user.getCompanyId();
        if (companyId == null) {
            log.warn("User {} has no company ID, skipping Metabase sync", user.getEmail());
            return UserProvisioningResult.failure("User has no company ID");
        }

        if (!metabaseProvisioningService.isTenantProvisioned(companyId)) {
            log.info("Tenant {} not yet provisioned in Metabase, skipping user sync", companyId);
            return UserProvisioningResult.failure("Tenant not provisioned in Metabase");
        }

        return metabaseProvisioningService.syncUser(user, companyId);
    }

    @Override
    public void deactivateUser(User user) {
        log.info("Deactivating user {} in Metabase", user.getEmail());
        metabaseProvisioningService.deactivateUser(user);
    }

    @Override
    public UserProvisioningResult syncUserGroups(User user) {
        log.info("Syncing user {} groups in Metabase", user.getEmail());

        Long companyId = user.getCompanyId();
        if (companyId == null) {
            log.warn("User {} has no company ID, skipping group sync", user.getEmail());
            return UserProvisioningResult.failure("User has no company ID");
        }

        if (!metabaseProvisioningService.isTenantProvisioned(companyId)) {
            log.info("Tenant {} not yet provisioned in Metabase, skipping group sync", companyId);
            return UserProvisioningResult.failure("Tenant not provisioned in Metabase");
        }

        metabaseProvisioningService.assignUserGroups(user, companyId, Set.of(user.getRole()));
        return UserProvisioningResult.updated(null, null);
    }

    @Override
    @Async
    @EventListener
    public void onUserCreated(UserCreatedEvent event) {
        log.debug("Handling UserCreatedEvent for user {}", event.getUser().getEmail());
        try {
            User user = event.getUser();
            Long companyId = event.getCompanyId() != null ? event.getCompanyId() : user.getCompanyId();

            if (companyId == null) {
                log.warn("User {} has no company ID, skipping Metabase sync", user.getEmail());
                return;
            }

            if (!metabaseProvisioningService.isTenantProvisioned(companyId)) {
                log.info("Tenant {} not yet provisioned in Metabase, skipping user creation sync", companyId);
                return;
            }

            UserProvisioningResult result = metabaseProvisioningService.provisionUser(user, companyId);
            if (result.success()) {
                log.info("Successfully synced new user {} to Metabase (id={})", 
                        user.getEmail(), result.metabaseUserId());
                auditService.logAction(companyId, user.getId(), AuditAction.USER_PROVISIONED,
                        ResourceType.USER, String.valueOf(user.getId()),
                        Map.of("action", "CREATE", "metabaseUserId", result.metabaseUserId()));
            } else {
                log.warn("Failed to sync new user {} to Metabase: {}", 
                        user.getEmail(), result.message());
            }
        } catch (Exception e) {
            log.error("Error handling UserCreatedEvent for user {}: {}", 
                    event.getUser().getEmail(), e.getMessage(), e);
        }
    }

    @Override
    @Async
    @EventListener
    public void onUserUpdated(UserUpdatedEvent event) {
        log.debug("Handling UserUpdatedEvent for user {}", event.getUser().getEmail());
        try {
            User user = event.getUser();
            Long companyId = event.getCompanyId() != null ? event.getCompanyId() : user.getCompanyId();

            if (companyId == null) {
                log.warn("User {} has no company ID, skipping Metabase sync", user.getEmail());
                return;
            }

            if (!metabaseProvisioningService.isTenantProvisioned(companyId)) {
                log.info("Tenant {} not yet provisioned in Metabase, skipping user update sync", companyId);
                return;
            }

            UserProvisioningResult result = metabaseProvisioningService.syncUser(user, companyId);
            if (result.success()) {
                log.info("Successfully synced updated user {} to Metabase", user.getEmail());
                auditService.logAction(companyId, user.getId(), AuditAction.USER_PROVISIONED,
                        ResourceType.USER, String.valueOf(user.getId()),
                        Map.of("action", "UPDATE", "changes", "email/name"));
            } else {
                log.warn("Failed to sync updated user {} to Metabase: {}", 
                        user.getEmail(), result.message());
            }
        } catch (Exception e) {
            log.error("Error handling UserUpdatedEvent for user {}: {}", 
                    event.getUser().getEmail(), e.getMessage(), e);
        }
    }

    @Override
    @Async
    @EventListener
    public void onUserDeactivated(UserDeactivatedEvent event) {
        log.debug("Handling UserDeactivatedEvent for user {}", event.getUser().getEmail());
        try {
            User user = event.getUser();
            Long companyId = event.getCompanyId() != null ? event.getCompanyId() : user.getCompanyId();
            metabaseProvisioningService.deactivateUser(user);
            log.info("Successfully deactivated user {} in Metabase", user.getEmail());
            auditService.logAction(companyId, user.getId(), AuditAction.USER_DEACTIVATED,
                    ResourceType.USER, String.valueOf(user.getId()),
                    Map.of("action", "DEACTIVATE"));
        } catch (Exception e) {
            log.error("Error handling UserDeactivatedEvent for user {}: {}", 
                    event.getUser().getEmail(), e.getMessage(), e);
        }
    }

    @Override
    @Async
    @EventListener
    public void onUserRoleChanged(UserRoleChangedEvent event) {
        log.debug("Handling UserRoleChangedEvent for user {}", event.getUser().getEmail());
        try {
            User user = event.getUser();
            Long companyId = event.getCompanyId() != null ? event.getCompanyId() : user.getCompanyId();

            if (companyId == null) {
                log.warn("User {} has no company ID, skipping Metabase role sync", user.getEmail());
                return;
            }

            if (!metabaseProvisioningService.isTenantProvisioned(companyId)) {
                log.info("Tenant {} not yet provisioned in Metabase, skipping role change sync", companyId);
                return;
            }

            var metabaseUser = metabaseProvisioningService.findMetabaseUser(user.getEmail());
            if (metabaseUser.isEmpty()) {
                log.warn("User {} not found in Metabase, cannot update groups", user.getEmail());
                return;
            }

            metabaseProvisioningService.updateUserGroups(metabaseUser.get().id(), companyId, event.getNewRoles());
            log.info("Successfully synced role change for user {} to Metabase", user.getEmail());
            auditService.logAction(companyId, user.getId(), AuditAction.GROUP_ASSIGNED,
                    ResourceType.USER, String.valueOf(user.getId()),
                    Map.of("action", "ROLE_CHANGE", 
                           "oldRoles", event.getOldRoles().toString(),
                           "newRoles", event.getNewRoles().toString()));
        } catch (Exception e) {
            log.error("Error handling UserRoleChangedEvent for user {}: {}", 
                    event.getUser().getEmail(), e.getMessage(), e);
        }
    }
}

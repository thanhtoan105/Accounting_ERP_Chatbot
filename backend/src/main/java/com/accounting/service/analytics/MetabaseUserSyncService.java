package com.accounting.service.analytics;

import com.accounting.entity.User;
import com.accounting.event.UserCreatedEvent;
import com.accounting.event.UserDeactivatedEvent;
import com.accounting.event.UserRoleChangedEvent;
import com.accounting.event.UserUpdatedEvent;
import com.accounting.service.analytics.MetabaseProvisioningService.UserProvisioningResult;

public interface MetabaseUserSyncService {

    UserProvisioningResult syncUser(User user);

    void deactivateUser(User user);

    UserProvisioningResult syncUserGroups(User user);

    void onUserCreated(UserCreatedEvent event);

    void onUserUpdated(UserUpdatedEvent event);

    void onUserRoleChanged(UserRoleChangedEvent event);

    void onUserDeactivated(UserDeactivatedEvent event);
}

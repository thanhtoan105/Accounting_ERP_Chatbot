package com.accounting.event;

import java.util.Set;

import com.accounting.entity.User;

public class UserRoleChangedEvent extends UserEvent {

    private final Set<String> oldRoles;
    private final Set<String> newRoles;

    public UserRoleChangedEvent(User user, Long companyId, Set<String> oldRoles, Set<String> newRoles) {
        super(user, companyId);
        this.oldRoles = oldRoles;
        this.newRoles = newRoles;
    }

    public Set<String> getOldRoles() {
        return oldRoles;
    }

    public Set<String> getNewRoles() {
        return newRoles;
    }
}

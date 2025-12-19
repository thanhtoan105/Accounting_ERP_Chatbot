package com.accounting.event;

import com.accounting.entity.User;

public class UserDeactivatedEvent extends UserEvent {

    public UserDeactivatedEvent(User user, Long companyId) {
        super(user, companyId);
    }
}

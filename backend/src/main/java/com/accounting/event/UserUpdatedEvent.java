package com.accounting.event;

import com.accounting.entity.User;

public class UserUpdatedEvent extends UserEvent {

    public UserUpdatedEvent(User user, Long companyId) {
        super(user, companyId);
    }
}

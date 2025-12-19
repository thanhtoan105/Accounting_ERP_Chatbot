package com.accounting.event;

import com.accounting.entity.User;

public class UserCreatedEvent extends UserEvent {

    public UserCreatedEvent(User user, Long companyId) {
        super(user, companyId);
    }
}

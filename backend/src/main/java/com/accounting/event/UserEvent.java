package com.accounting.event;

import com.accounting.entity.User;

public abstract class UserEvent {

    private final User user;
    private final Long companyId;

    protected UserEvent(User user, Long companyId) {
        this.user = user;
        this.companyId = companyId;
    }

    public User getUser() {
        return user;
    }

    public Long getCompanyId() {
        return companyId;
    }
}

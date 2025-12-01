package com.accounting.dto;

import java.util.UUID;

/**
 * DTO for AR reminder configuration.
 */
public class ARReminderConfigDTO {

    private UUID id;
    private Long companyId;
    private Integer preDueDays;
    private Boolean dueDateEnabled;
    private Integer postDueCadenceDays;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Integer getPreDueDays() {
        return preDueDays;
    }

    public void setPreDueDays(Integer preDueDays) {
        this.preDueDays = preDueDays;
    }

    public Boolean getDueDateEnabled() {
        return dueDateEnabled;
    }

    public void setDueDateEnabled(Boolean dueDateEnabled) {
        this.dueDateEnabled = dueDateEnabled;
    }

    public Integer getPostDueCadenceDays() {
        return postDueCadenceDays;
    }

    public void setPostDueCadenceDays(Integer postDueCadenceDays) {
        this.postDueCadenceDays = postDueCadenceDays;
    }
}

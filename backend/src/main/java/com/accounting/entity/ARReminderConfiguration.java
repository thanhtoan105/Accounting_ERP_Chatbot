package com.accounting.entity;

import java.time.Instant;
import java.util.UUID;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * ARReminderConfiguration entity for storing AR reminder settings.
 * Configures automated reminder schedule for overdue invoices.
 */
@Entity
@Table(name = "ar_reminder_configuration")
public class ARReminderConfiguration implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @NotNull
    @PositiveOrZero
    @Column(name = "pre_due_days", nullable = false)
    private Integer preDueDays = 3;

    @NotNull
    @Column(name = "due_date_enabled", nullable = false)
    private Boolean dueDateEnabled = true;

    @NotNull
    @PositiveOrZero
    @Column(name = "post_due_cadence_days", nullable = false)
    private Integer postDueCadenceDays = 7;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

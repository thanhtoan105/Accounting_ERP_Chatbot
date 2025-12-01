package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Base StatementDispute entity for tracking discrepancies found during
 * statement reconciliation.
 * Supports both AR (customer) and AP (supplier) disputes through polymorphism.
 */
@Entity
@Table(name = "statement_disputes")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "party_type", discriminatorType = DiscriminatorType.STRING)
public abstract class StatementDispute implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @NotNull
    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "document_id")
    private UUID documentId;

    @Size(max = 100)
    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "reconciliation_id")
    private UUID reconciliationId;

    @Column(name = "system_amount", precision = 19, scale = 2)
    private BigDecimal systemAmount;

    @Column(name = "counterparty_amount", precision = 19, scale = 2)
    private BigDecimal counterpartyAmount;

    @Column(name = "variance", precision = 19, scale = 2)
    private BigDecimal variance;

    @Size(max = 20)
    @Column(name = "variance_type", length = 20)
    private String varianceType;

    @Column(name = "dispute_reason", columnDefinition = "TEXT")
    private String disputeReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @NotNull
    @Size(max = 20)
    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    @Size(max = 1000)
    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "created_by_id")
    private Long createdById;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_by_id")
    private Long resolvedById;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    // Relationships
    @ManyToOne
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private Company company;

    @ManyToOne
    @JoinColumn(name = "resolved_by_id", insertable = false, updatable = false)
    private User resolvedBy;

    @ManyToOne
    @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
    private User createdByUser;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    // Abstract method to get party type
    public abstract StatementPartyType getPartyType();

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getPartyId() {
        return partyId;
    }

    public void setPartyId(Long partyId) {
        this.partyId = partyId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public UUID getReconciliationId() {
        return reconciliationId;
    }

    public void setReconciliationId(UUID reconciliationId) {
        this.reconciliationId = reconciliationId;
    }

    public BigDecimal getSystemAmount() {
        return systemAmount;
    }

    public void setSystemAmount(BigDecimal systemAmount) {
        this.systemAmount = systemAmount;
    }

    public BigDecimal getCounterpartyAmount() {
        return counterpartyAmount;
    }

    public void setCounterpartyAmount(BigDecimal counterpartyAmount) {
        this.counterpartyAmount = counterpartyAmount;
    }

    public BigDecimal getVariance() {
        return variance;
    }

    public void setVariance(BigDecimal variance) {
        this.variance = variance;
    }

    public String getVarianceType() {
        return varianceType;
    }

    public void setVarianceType(String varianceType) {
        this.varianceType = varianceType;
    }

    public String getDisputeReason() {
        return disputeReason;
    }

    public void setDisputeReason(String disputeReason) {
        this.disputeReason = disputeReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public void setCreatedById(Long createdById) {
        this.createdById = createdById;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getResolvedById() {
        return resolvedById;
    }

    public void setResolvedById(Long resolvedById) {
        this.resolvedById = resolvedById;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(User resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }
}

package com.accounting.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Base StatementHistory entity for tracking all generated and sent statements.
 * Supports both AR
 * (customer) and AP (supplier) statements through polymorphism.
 */
@Entity
@Table(name = "statement_history")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "party_type", discriminatorType = DiscriminatorType.STRING)
public abstract class StatementHistory implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @NotNull
    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Size(max = 50)
    @Column(name = "statement_number", length = 50)
    private String statementNumber;

    @Size(max = 20)
    @Column(name = "statement_type", nullable = false, length = 20)
    private String statementType;

    @Size(max = 20)
    @Column(name = "format", nullable = false, length = 20)
    private String format;

    @NotNull
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @NotNull
    @Column(name = "generated_by_id", nullable = false)
    private Long generatedById;

    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Size(max = 64)
    @Column(name = "statement_hash", nullable = false, length = 64)
    private String statementHash;

    @Size(max = 500)
    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "filters_applied", columnDefinition = "JSONB")
    private String filtersApplied;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_to", columnDefinition = "TEXT")
    private String sentTo;

    @NotNull
    @Column(name = "export_count", nullable = false)
    private Integer exportCount = 0;

    @NotNull
    @Column(name = "sent_count", nullable = false)
    private Integer sentCount = 0;

    @NotNull
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @NotNull
    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Relationships
    @ManyToOne
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private Company company;

    @ManyToOne
    @JoinColumn(name = "generated_by_id", insertable = false, updatable = false)
    private User generatedBy;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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

    public String getStatementNumber() {
        return statementNumber;
    }

    public void setStatementNumber(String statementNumber) {
        this.statementNumber = statementNumber;
    }

    public String getStatementType() {
        return statementType;
    }

    public void setStatementType(String statementType) {
        this.statementType = statementType;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Long getGeneratedById() {
        return generatedById;
    }

    public void setGeneratedById(Long generatedById) {
        this.generatedById = generatedById;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getStatementHash() {
        return statementHash;
    }

    public void setStatementHash(String statementHash) {
        this.statementHash = statementHash;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFiltersApplied() {
        return filtersApplied;
    }

    public void setFiltersApplied(String filtersApplied) {
        this.filtersApplied = filtersApplied;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getSentTo() {
        return sentTo;
    }

    public void setSentTo(String sentTo) {
        this.sentTo = sentTo;
    }

    public Integer getExportCount() {
        return exportCount;
    }

    public void setExportCount(Integer exportCount) {
        this.exportCount = exportCount;
    }

    public Integer getSentCount() {
        return sentCount;
    }

    public void setSentCount(Integer sentCount) {
        this.sentCount = sentCount;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public User getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(User generatedBy) {
        this.generatedBy = generatedBy;
    }

    public void incrementExportCount() {
        this.exportCount++;
    }

    public void incrementSentCount() {
        this.sentCount++;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementDownloadCount() {
        this.downloadCount++;
    }
}

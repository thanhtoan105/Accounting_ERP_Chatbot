package com.accounting.entity.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bank reconciliation session entity.
 * Represents a reconciliation between bank statement and ledger for a specific period.
 */
@Entity
@Table(name = "bank_reconciliations")
public class BankReconciliation implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @NotNull
    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @NotNull
    @Column(name = "statement_period_start", nullable = false)
    private LocalDate statementPeriodStart;

    @NotNull
    @Column(name = "statement_period_end", nullable = false)
    private LocalDate statementPeriodEnd;

    @NotNull
    @Column(name = "statement_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal statementBalance;

    @Column(name = "ledger_balance", precision = 19, scale = 2)
    private BigDecimal ledgerBalance;

    @Column(name = "reconciled_balance", precision = 19, scale = 2)
    private BigDecimal reconciledBalance;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationStatus status = ReconciliationStatus.NOT_STARTED;

    @Size(max = 1000)
    @Column(name = "statement_file_url", length = 1000)
    private String statementFileUrl;

    @Size(max = 64)
    @Column(name = "statement_file_hash", length = 64)
    private String statementFileHash;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by_id")
    private Long completedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "reconciliation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BankStatementLine> statementLines = new ArrayList<>();

    @OneToMany(mappedBy = "reconciliation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReconciliationAdjustment> adjustments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Helper methods
    public void addStatementLine(BankStatementLine line) {
        statementLines.add(line);
        line.setReconciliation(this);
    }

    public void removeStatementLine(BankStatementLine line) {
        statementLines.remove(line);
        line.setReconciliation(null);
    }

    public void addAdjustment(ReconciliationAdjustment adjustment) {
        adjustments.add(adjustment);
        adjustment.setReconciliation(this);
    }

    public void removeAdjustment(ReconciliationAdjustment adjustment) {
        adjustments.remove(adjustment);
        adjustment.setReconciliation(null);
    }

    // Getters and Setters
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

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public LocalDate getStatementPeriodStart() {
        return statementPeriodStart;
    }

    public void setStatementPeriodStart(LocalDate statementPeriodStart) {
        this.statementPeriodStart = statementPeriodStart;
    }

    public LocalDate getStatementPeriodEnd() {
        return statementPeriodEnd;
    }

    public void setStatementPeriodEnd(LocalDate statementPeriodEnd) {
        this.statementPeriodEnd = statementPeriodEnd;
    }

    public BigDecimal getStatementBalance() {
        return statementBalance;
    }

    public void setStatementBalance(BigDecimal statementBalance) {
        this.statementBalance = statementBalance;
    }

    public BigDecimal getLedgerBalance() {
        return ledgerBalance;
    }

    public void setLedgerBalance(BigDecimal ledgerBalance) {
        this.ledgerBalance = ledgerBalance;
    }

    public BigDecimal getReconciledBalance() {
        return reconciledBalance;
    }

    public void setReconciledBalance(BigDecimal reconciledBalance) {
        this.reconciledBalance = reconciledBalance;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationStatus status) {
        this.status = status;
    }

    public String getStatementFileUrl() {
        return statementFileUrl;
    }

    public void setStatementFileUrl(String statementFileUrl) {
        this.statementFileUrl = statementFileUrl;
    }

    public String getStatementFileHash() {
        return statementFileHash;
    }

    public void setStatementFileHash(String statementFileHash) {
        this.statementFileHash = statementFileHash;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getCompletedById() {
        return completedById;
    }

    public void setCompletedById(Long completedById) {
        this.completedById = completedById;
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

    public List<BankStatementLine> getStatementLines() {
        return statementLines;
    }

    public void setStatementLines(List<BankStatementLine> statementLines) {
        this.statementLines = statementLines;
    }

    public List<ReconciliationAdjustment> getAdjustments() {
        return adjustments;
    }

    public void setAdjustments(List<ReconciliationAdjustment> adjustments) {
        this.adjustments = adjustments;
    }
}

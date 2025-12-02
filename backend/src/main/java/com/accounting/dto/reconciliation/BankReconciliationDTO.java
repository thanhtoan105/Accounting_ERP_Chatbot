package com.accounting.dto.reconciliation;

import com.accounting.entity.reconciliation.ReconciliationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full DTO for BankReconciliation responses with details.
 */
public class BankReconciliationDTO {

    private UUID id;
    private Long companyId;
    private Long bankAccountId;
    private String bankAccountNumber;
    private String bankName;
    private LocalDate statementPeriodStart;
    private LocalDate statementPeriodEnd;
    private BigDecimal statementBalance;
    private BigDecimal ledgerBalance;
    private BigDecimal reconciledBalance;
    private BigDecimal difference;
    private ReconciliationStatus status;
    private String statementFileUrl;
    private String statementFileHash;
    private String notes;
    private Instant completedAt;
    private Long completedById;
    private String completedByName;
    private Instant createdAt;
    private Instant updatedAt;

    // Summary counts
    private Integer totalLines;
    private Integer matchedLines;
    private Integer unmatchedLines;
    private Integer adjustmentRequiredLines;
    private BigDecimal matchedAmount;
    private BigDecimal unmatchedAmount;

    // Nested data (optional, populated on detail view)
    private List<BankStatementLineDTO> statementLines;
    private List<ReconciliationAdjustmentDTO> adjustments;

    public BankReconciliationDTO() {
    }

    // Helper to calculate difference
    public BigDecimal getDifference() {
        if (difference != null) return difference;
        if (statementBalance == null || ledgerBalance == null) return null;
        return statementBalance.subtract(ledgerBalance);
    }

    // Getters and Setters
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

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
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

    public void setDifference(BigDecimal difference) {
        this.difference = difference;
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

    public String getCompletedByName() {
        return completedByName;
    }

    public void setCompletedByName(String completedByName) {
        this.completedByName = completedByName;
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

    public Integer getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(Integer totalLines) {
        this.totalLines = totalLines;
    }

    public Integer getMatchedLines() {
        return matchedLines;
    }

    public void setMatchedLines(Integer matchedLines) {
        this.matchedLines = matchedLines;
    }

    public Integer getUnmatchedLines() {
        return unmatchedLines;
    }

    public void setUnmatchedLines(Integer unmatchedLines) {
        this.unmatchedLines = unmatchedLines;
    }

    public Integer getAdjustmentRequiredLines() {
        return adjustmentRequiredLines;
    }

    public void setAdjustmentRequiredLines(Integer adjustmentRequiredLines) {
        this.adjustmentRequiredLines = adjustmentRequiredLines;
    }

    public BigDecimal getMatchedAmount() {
        return matchedAmount;
    }

    public void setMatchedAmount(BigDecimal matchedAmount) {
        this.matchedAmount = matchedAmount;
    }

    public BigDecimal getUnmatchedAmount() {
        return unmatchedAmount;
    }

    public void setUnmatchedAmount(BigDecimal unmatchedAmount) {
        this.unmatchedAmount = unmatchedAmount;
    }

    public List<BankStatementLineDTO> getStatementLines() {
        return statementLines;
    }

    public void setStatementLines(List<BankStatementLineDTO> statementLines) {
        this.statementLines = statementLines;
    }

    public List<ReconciliationAdjustmentDTO> getAdjustments() {
        return adjustments;
    }

    public void setAdjustments(List<ReconciliationAdjustmentDTO> adjustments) {
        this.adjustments = adjustments;
    }
}

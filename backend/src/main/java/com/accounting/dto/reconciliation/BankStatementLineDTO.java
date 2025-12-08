package com.accounting.dto.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.accounting.entity.reconciliation.MatchStatus;

/**
 * DTO for BankStatementLine responses.
 */
public class BankStatementLineDTO {

    private UUID id;
    private UUID reconciliationId;
    private Integer lineNumber;
    private LocalDate transactionDate;
    private String description;
    private String reference;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private BigDecimal balance;
    private MatchStatus matchStatus;
    private UUID matchedVoucherId;
    private String matchedVoucherNumber;
    private Instant matchedAt;
    private Long matchedById;
    private String matchedByName;
    private BigDecimal matchConfidence;
    private String matchReason;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    public BankStatementLineDTO() {
    }

    // Helper to get net amount
    public BigDecimal getNetAmount() {
        if (creditAmount == null && debitAmount == null) return BigDecimal.ZERO;
        BigDecimal credit = creditAmount != null ? creditAmount : BigDecimal.ZERO;
        BigDecimal debit = debitAmount != null ? debitAmount : BigDecimal.ZERO;
        return credit.subtract(debit);
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReconciliationId() {
        return reconciliationId;
    }

    public void setReconciliationId(UUID reconciliationId) {
        this.reconciliationId = reconciliationId;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(BigDecimal debitAmount) {
        this.debitAmount = debitAmount;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public void setCreditAmount(BigDecimal creditAmount) {
        this.creditAmount = creditAmount;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public MatchStatus getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(MatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }

    public UUID getMatchedVoucherId() {
        return matchedVoucherId;
    }

    public void setMatchedVoucherId(UUID matchedVoucherId) {
        this.matchedVoucherId = matchedVoucherId;
    }

    public String getMatchedVoucherNumber() {
        return matchedVoucherNumber;
    }

    public void setMatchedVoucherNumber(String matchedVoucherNumber) {
        this.matchedVoucherNumber = matchedVoucherNumber;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(Instant matchedAt) {
        this.matchedAt = matchedAt;
    }

    public Long getMatchedById() {
        return matchedById;
    }

    public void setMatchedById(Long matchedById) {
        this.matchedById = matchedById;
    }

    public String getMatchedByName() {
        return matchedByName;
    }

    public void setMatchedByName(String matchedByName) {
        this.matchedByName = matchedByName;
    }

    public BigDecimal getMatchConfidence() {
        return matchConfidence;
    }

    public void setMatchConfidence(BigDecimal matchConfidence) {
        this.matchConfidence = matchConfidence;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
}

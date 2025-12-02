package com.accounting.dto.reconciliation;

import com.accounting.entity.reconciliation.ReconciliationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight DTO for BankReconciliation list views.
 */
public class BankReconciliationListDTO {

    private UUID id;
    private Long bankAccountId;
    private String bankAccountNumber;
    private String bankName;
    private LocalDate statementPeriodStart;
    private LocalDate statementPeriodEnd;
    private BigDecimal statementBalance;
    private BigDecimal ledgerBalance;
    private ReconciliationStatus status;
    private Integer totalLines;
    private Integer matchedLines;
    private Integer unmatchedLines;
    private Instant updatedAt;

    public BankReconciliationListDTO() {
    }

    // Helper to calculate delta
    public BigDecimal getDelta() {
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

    public ReconciliationStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationStatus status) {
        this.status = status;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

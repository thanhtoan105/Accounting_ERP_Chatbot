package com.accounting.dto.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for ledger transaction (voucher line) used in reconciliation matching.
 */
public class LedgerTransactionDTO {

    private UUID voucherId;
    private String voucherNumber;
    private String voucherType;
    private LocalDate transactionDate;
    private String description;
    private String reference;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String status;
    private boolean alreadyMatched;

    public LedgerTransactionDTO() {
    }

    // Helper to get net amount
    public BigDecimal getNetAmount() {
        BigDecimal credit = creditAmount != null ? creditAmount : BigDecimal.ZERO;
        BigDecimal debit = debitAmount != null ? debitAmount : BigDecimal.ZERO;
        return credit.subtract(debit);
    }

    // Getters and Setters
    public UUID getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(UUID voucherId) {
        this.voucherId = voucherId;
    }

    public String getVoucherNumber() {
        return voucherNumber;
    }

    public void setVoucherNumber(String voucherNumber) {
        this.voucherNumber = voucherNumber;
    }

    public String getVoucherType() {
        return voucherType;
    }

    public void setVoucherType(String voucherType) {
        this.voucherType = voucherType;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAlreadyMatched() {
        return alreadyMatched;
    }

    public void setAlreadyMatched(boolean alreadyMatched) {
        this.alreadyMatched = alreadyMatched;
    }
}

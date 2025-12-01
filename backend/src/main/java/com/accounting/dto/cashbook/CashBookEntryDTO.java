package com.accounting.dto.cashbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO representing a single cash book transaction entry with running balance.
 * Used for per-account ledger view (AC6.4-01).
 */
public class CashBookEntryDTO {

    private UUID voucherId;
    private String voucherNumber;
    private LocalDate transactionDate;
    private String description;
    private String reference;
    private String transactionType; // "receipt" or "payment"
    private BigDecimal debit; // Inflow (receipts)
    private BigDecimal credit; // Outflow (payments)
    private BigDecimal runningBalance;
    private String postedByName;
    private Instant postedAt;
    private Long customerId;
    private String customerName;
    private Long supplierId;
    private String supplierName;
    private int attachmentCount;

    // Default constructor
    public CashBookEntryDTO() {
    }

    // Full constructor
    public CashBookEntryDTO(
            UUID voucherId,
            String voucherNumber,
            LocalDate transactionDate,
            String description,
            String reference,
            String transactionType,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance,
            String postedByName,
            Instant postedAt) {
        this.voucherId = voucherId;
        this.voucherNumber = voucherNumber;
        this.transactionDate = transactionDate;
        this.description = description;
        this.reference = reference;
        this.transactionType = transactionType;
        this.debit = debit;
        this.credit = credit;
        this.runningBalance = runningBalance;
        this.postedByName = postedByName;
        this.postedAt = postedAt;
    }

    // Getters and setters
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

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public void setDebit(BigDecimal debit) {
        this.debit = debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(BigDecimal credit) {
        this.credit = credit;
    }

    public BigDecimal getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(BigDecimal runningBalance) {
        this.runningBalance = runningBalance;
    }

    public String getPostedByName() {
        return postedByName;
    }

    public void setPostedByName(String postedByName) {
        this.postedByName = postedByName;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public int getAttachmentCount() {
        return attachmentCount;
    }

    public void setAttachmentCount(int attachmentCount) {
        this.attachmentCount = attachmentCount;
    }

    /**
     * Check if this entry has a negative running balance.
     * Used for highlighting in UI (AC6.4-02).
     */
    public boolean isNegativeBalance() {
        return runningBalance != null && runningBalance.signum() < 0;
    }
}

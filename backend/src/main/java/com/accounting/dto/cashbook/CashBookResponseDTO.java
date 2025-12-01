package com.accounting.dto.cashbook;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for cash book response containing account info, transactions, and totals.
 * Used for GET /api/v1/cash-book/:bankAccountId endpoint (AC6.4-01).
 */
public class CashBookResponseDTO {

    private Long bankAccountId;
    private String accountNumber;
    private String bankName;
    private String accountType; // "CASH" or "BANK"
    private String glAccountCode;
    private BigDecimal openingBalance;
    private BigDecimal totalInflow;
    private BigDecimal totalOutflow;
    private BigDecimal closingBalance;
    private List<CashBookEntryDTO> transactions;
    private int totalCount;
    private int page;
    private int size;

    // Default constructor
    public CashBookResponseDTO() {
    }

    // Getters and setters
    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getGlAccountCode() {
        return glAccountCode;
    }

    public void setGlAccountCode(String glAccountCode) {
        this.glAccountCode = glAccountCode;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getTotalInflow() {
        return totalInflow;
    }

    public void setTotalInflow(BigDecimal totalInflow) {
        this.totalInflow = totalInflow;
    }

    public BigDecimal getTotalOutflow() {
        return totalOutflow;
    }

    public void setTotalOutflow(BigDecimal totalOutflow) {
        this.totalOutflow = totalOutflow;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance;
    }

    public List<CashBookEntryDTO> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<CashBookEntryDTO> transactions) {
        this.transactions = transactions;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}

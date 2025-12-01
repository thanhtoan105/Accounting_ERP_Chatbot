package com.accounting.dto.cashbook;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for multi-account cash book summary (AC6.4-03).
 * Provides aggregated totals across multiple bank/cash accounts.
 */
public class CashBookSummaryDTO {

    private List<AccountSummaryDTO> accounts;
    private GrandTotalsDTO grandTotals;

    // Default constructor
    public CashBookSummaryDTO() {
    }

    public CashBookSummaryDTO(List<AccountSummaryDTO> accounts, GrandTotalsDTO grandTotals) {
        this.accounts = accounts;
        this.grandTotals = grandTotals;
    }

    public List<AccountSummaryDTO> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<AccountSummaryDTO> accounts) {
        this.accounts = accounts;
    }

    public GrandTotalsDTO getGrandTotals() {
        return grandTotals;
    }

    public void setGrandTotals(GrandTotalsDTO grandTotals) {
        this.grandTotals = grandTotals;
    }

    /**
     * Individual account summary within the multi-account view.
     */
    public static class AccountSummaryDTO {
        private Long bankAccountId;
        private String accountNumber;
        private String bankName;
        private String accountType; // "CASH" or "BANK"
        private String glAccountCode;
        private BigDecimal openingBalance;
        private BigDecimal totalInflow;
        private BigDecimal totalOutflow;
        private BigDecimal closingBalance;
        private int transactionCount;

        // Default constructor
        public AccountSummaryDTO() {
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

        public int getTransactionCount() {
            return transactionCount;
        }

        public void setTransactionCount(int transactionCount) {
            this.transactionCount = transactionCount;
        }

        /**
         * Check if this account has a negative closing balance.
         */
        public boolean isNegativeBalance() {
            return closingBalance != null && closingBalance.signum() < 0;
        }
    }

    /**
     * Grand totals across all accounts.
     */
    public static class GrandTotalsDTO {
        private BigDecimal totalOpeningBalance;
        private BigDecimal totalInflow;
        private BigDecimal totalOutflow;
        private BigDecimal totalClosingBalance;
        private int totalTransactionCount;

        // Default constructor
        public GrandTotalsDTO() {
        }

        public GrandTotalsDTO(
                BigDecimal totalOpeningBalance,
                BigDecimal totalInflow,
                BigDecimal totalOutflow,
                BigDecimal totalClosingBalance,
                int totalTransactionCount) {
            this.totalOpeningBalance = totalOpeningBalance;
            this.totalInflow = totalInflow;
            this.totalOutflow = totalOutflow;
            this.totalClosingBalance = totalClosingBalance;
            this.totalTransactionCount = totalTransactionCount;
        }

        // Getters and setters
        public BigDecimal getTotalOpeningBalance() {
            return totalOpeningBalance;
        }

        public void setTotalOpeningBalance(BigDecimal totalOpeningBalance) {
            this.totalOpeningBalance = totalOpeningBalance;
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

        public BigDecimal getTotalClosingBalance() {
            return totalClosingBalance;
        }

        public void setTotalClosingBalance(BigDecimal totalClosingBalance) {
            this.totalClosingBalance = totalClosingBalance;
        }

        public int getTotalTransactionCount() {
            return totalTransactionCount;
        }

        public void setTotalTransactionCount(int totalTransactionCount) {
            this.totalTransactionCount = totalTransactionCount;
        }
    }
}

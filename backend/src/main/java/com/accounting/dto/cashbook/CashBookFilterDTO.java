package com.accounting.dto.cashbook;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for cash book query filters.
 * Used to capture filter parameters for querying and audit logging.
 */
public class CashBookFilterDTO {

    private Long bankAccountId;
    private List<Long> accountIds; // For multi-account summary
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String transactionType; // "receipt", "payment", or "all"
    private String reference;
    private String search;
    private int page;
    private int size;

    // Default constructor
    public CashBookFilterDTO() {
        this.transactionType = "all";
        this.page = 0;
        this.size = 20;
    }

    // Builder-style setters for fluent API
    public CashBookFilterDTO withBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
        return this;
    }

    public CashBookFilterDTO withAccountIds(List<Long> accountIds) {
        this.accountIds = accountIds;
        return this;
    }

    public CashBookFilterDTO withDateRange(LocalDate dateFrom, LocalDate dateTo) {
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        return this;
    }

    public CashBookFilterDTO withTransactionType(String transactionType) {
        this.transactionType = transactionType != null ? transactionType : "all";
        return this;
    }

    public CashBookFilterDTO withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public CashBookFilterDTO withSearch(String search) {
        this.search = search;
        return this;
    }

    public CashBookFilterDTO withPagination(int page, int size) {
        this.page = page;
        this.size = size;
        return this;
    }

    // Getters and setters
    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public List<Long> getAccountIds() {
        return accountIds;
    }

    public void setAccountIds(List<Long> accountIds) {
        this.accountIds = accountIds;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
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

    /**
     * Convert filter to string for audit logging.
     */
    public String toAuditString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (bankAccountId != null)
            sb.append("bankAccountId=").append(bankAccountId).append(", ");
        if (accountIds != null && !accountIds.isEmpty())
            sb.append("accountIds=").append(accountIds).append(", ");
        if (dateFrom != null)
            sb.append("dateFrom=").append(dateFrom).append(", ");
        if (dateTo != null)
            sb.append("dateTo=").append(dateTo).append(", ");
        if (transactionType != null && !"all".equals(transactionType))
            sb.append("type=").append(transactionType).append(", ");
        if (reference != null && !reference.isBlank())
            sb.append("reference=").append(reference).append(", ");
        if (search != null && !search.isBlank())
            sb.append("search=").append(search).append(", ");
        sb.append("page=").append(page).append(", size=").append(size);
        sb.append("}");
        return sb.toString();
    }
}

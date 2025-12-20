package com.accounting.dto.reconciliation;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for BankStatementFormat responses.
 */
public class BankStatementFormatDTO {

    private UUID id;
    private Long companyId;
    private Long bankAccountId;
    private String formatName;
    private String dateColumn;
    private String descriptionColumn;
    private String referenceColumn;
    private String debitColumn;
    private String creditColumn;
    private String balanceColumn;
    private String dateFormat;
    private Integer skipHeaderRows;
    private Instant createdAt;
    private Instant updatedAt;

    public BankStatementFormatDTO() {
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

    public String getFormatName() {
        return formatName;
    }

    public void setFormatName(String formatName) {
        this.formatName = formatName;
    }

    public String getDateColumn() {
        return dateColumn;
    }

    public void setDateColumn(String dateColumn) {
        this.dateColumn = dateColumn;
    }

    public String getDescriptionColumn() {
        return descriptionColumn;
    }

    public void setDescriptionColumn(String descriptionColumn) {
        this.descriptionColumn = descriptionColumn;
    }

    public String getReferenceColumn() {
        return referenceColumn;
    }

    public void setReferenceColumn(String referenceColumn) {
        this.referenceColumn = referenceColumn;
    }

    public String getDebitColumn() {
        return debitColumn;
    }

    public void setDebitColumn(String debitColumn) {
        this.debitColumn = debitColumn;
    }

    public String getCreditColumn() {
        return creditColumn;
    }

    public void setCreditColumn(String creditColumn) {
        this.creditColumn = creditColumn;
    }

    public String getBalanceColumn() {
        return balanceColumn;
    }

    public void setBalanceColumn(String balanceColumn) {
        this.balanceColumn = balanceColumn;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public Integer getSkipHeaderRows() {
        return skipHeaderRows;
    }

    public void setSkipHeaderRows(Integer skipHeaderRows) {
        this.skipHeaderRows = skipHeaderRows;
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

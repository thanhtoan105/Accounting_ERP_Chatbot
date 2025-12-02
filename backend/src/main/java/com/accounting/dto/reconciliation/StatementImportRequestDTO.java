package com.accounting.dto.reconciliation;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for statement import with column mapping.
 */
public class StatementImportRequestDTO {

    @NotBlank(message = "Date column is required")
    private String dateColumn;

    private String descriptionColumn;

    private String referenceColumn;

    private String debitColumn;

    private String creditColumn;

    private String balanceColumn;

    private String dateFormat = "yyyy-MM-dd";

    private Integer skipHeaderRows = 1;

    private Boolean saveFormatProfile = false;

    private String formatProfileName;

    public StatementImportRequestDTO() {
    }

    // Getters and Setters
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

    public Boolean getSaveFormatProfile() {
        return saveFormatProfile;
    }

    public void setSaveFormatProfile(Boolean saveFormatProfile) {
        this.saveFormatProfile = saveFormatProfile;
    }

    public String getFormatProfileName() {
        return formatProfileName;
    }

    public void setFormatProfileName(String formatProfileName) {
        this.formatProfileName = formatProfileName;
    }
}

package com.accounting.dto.reconciliation;

import java.util.List;

/**
 * DTO for column mapping auto-detection suggestions.
 */
public class ColumnMappingSuggestionDTO {

    private List<String> headers;
    private String suggestedDateColumn;
    private String suggestedDescriptionColumn;
    private String suggestedReferenceColumn;
    private String suggestedDebitColumn;
    private String suggestedCreditColumn;
    private String suggestedBalanceColumn;
    private String suggestedDateFormat;
    private BankStatementFormatDTO savedProfile;
    private boolean hasSavedProfile;

    public ColumnMappingSuggestionDTO() {
    }

    // Getters and Setters
    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public String getSuggestedDateColumn() {
        return suggestedDateColumn;
    }

    public void setSuggestedDateColumn(String suggestedDateColumn) {
        this.suggestedDateColumn = suggestedDateColumn;
    }

    public String getSuggestedDescriptionColumn() {
        return suggestedDescriptionColumn;
    }

    public void setSuggestedDescriptionColumn(String suggestedDescriptionColumn) {
        this.suggestedDescriptionColumn = suggestedDescriptionColumn;
    }

    public String getSuggestedReferenceColumn() {
        return suggestedReferenceColumn;
    }

    public void setSuggestedReferenceColumn(String suggestedReferenceColumn) {
        this.suggestedReferenceColumn = suggestedReferenceColumn;
    }

    public String getSuggestedDebitColumn() {
        return suggestedDebitColumn;
    }

    public void setSuggestedDebitColumn(String suggestedDebitColumn) {
        this.suggestedDebitColumn = suggestedDebitColumn;
    }

    public String getSuggestedCreditColumn() {
        return suggestedCreditColumn;
    }

    public void setSuggestedCreditColumn(String suggestedCreditColumn) {
        this.suggestedCreditColumn = suggestedCreditColumn;
    }

    public String getSuggestedBalanceColumn() {
        return suggestedBalanceColumn;
    }

    public void setSuggestedBalanceColumn(String suggestedBalanceColumn) {
        this.suggestedBalanceColumn = suggestedBalanceColumn;
    }

    public String getSuggestedDateFormat() {
        return suggestedDateFormat;
    }

    public void setSuggestedDateFormat(String suggestedDateFormat) {
        this.suggestedDateFormat = suggestedDateFormat;
    }

    public BankStatementFormatDTO getSavedProfile() {
        return savedProfile;
    }

    public void setSavedProfile(BankStatementFormatDTO savedProfile) {
        this.savedProfile = savedProfile;
    }

    public boolean isHasSavedProfile() {
        return hasSavedProfile;
    }

    public void setHasSavedProfile(boolean hasSavedProfile) {
        this.hasSavedProfile = hasSavedProfile;
    }
}

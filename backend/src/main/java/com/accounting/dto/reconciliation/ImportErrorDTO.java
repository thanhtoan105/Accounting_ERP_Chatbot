package com.accounting.dto.reconciliation;

/**
 * DTO for import error details.
 */
public class ImportErrorDTO {

    private int rowNumber;
    private String field;
    private String value;
    private String errorMessage;

    public ImportErrorDTO() {
    }

    public ImportErrorDTO(int rowNumber, String field, String value, String errorMessage) {
        this.rowNumber = rowNumber;
        this.field = field;
        this.value = value;
        this.errorMessage = errorMessage;
    }

    // Getters and Setters
    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

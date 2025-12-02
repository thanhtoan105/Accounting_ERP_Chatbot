package com.accounting.dto.reconciliation;

import java.util.ArrayList;
import java.util.List;

/**
 * Result DTO for statement import operation.
 */
public class StatementImportResultDTO {

    private boolean success;
    private int totalRows;
    private int importedRows;
    private int errorRows;
    private String fileHash;
    private boolean duplicateDetected;
    private String duplicateReconciliationId;
    private List<ImportErrorDTO> errors = new ArrayList<>();
    private String errorReportId;

    public StatementImportResultDTO() {
    }

    // Builder-style methods
    public StatementImportResultDTO success(boolean success) {
        this.success = success;
        return this;
    }

    public StatementImportResultDTO totalRows(int totalRows) {
        this.totalRows = totalRows;
        return this;
    }

    public StatementImportResultDTO importedRows(int importedRows) {
        this.importedRows = importedRows;
        return this;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public void setImportedRows(int importedRows) {
        this.importedRows = importedRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public void setErrorRows(int errorRows) {
        this.errorRows = errorRows;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public boolean isDuplicateDetected() {
        return duplicateDetected;
    }

    public void setDuplicateDetected(boolean duplicateDetected) {
        this.duplicateDetected = duplicateDetected;
    }

    public String getDuplicateReconciliationId() {
        return duplicateReconciliationId;
    }

    public void setDuplicateReconciliationId(String duplicateReconciliationId) {
        this.duplicateReconciliationId = duplicateReconciliationId;
    }

    public List<ImportErrorDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportErrorDTO> errors) {
        this.errors = errors;
    }

    public String getErrorReportId() {
        return errorReportId;
    }

    public void setErrorReportId(String errorReportId) {
        this.errorReportId = errorReportId;
    }
}

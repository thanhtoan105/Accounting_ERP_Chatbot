package com.accounting.dto;

import java.util.List;
import java.util.Map;

/**
 * Result of sales invoice validation.
 */
public class SalesInvoiceValidationResult {

    private boolean isValid;
    private Map<String, String> headerErrors;
    private Map<Integer, Map<String, String>> lineErrors;

    public SalesInvoiceValidationResult() {
    }

    public SalesInvoiceValidationResult(
            boolean isValid,
            Map<String, String> headerErrors,
            Map<Integer, Map<String, String>> lineErrors) {
        this.isValid = isValid;
        this.headerErrors = headerErrors;
        this.lineErrors = lineErrors;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public Map<String, String> getHeaderErrors() {
        return headerErrors;
    }

    public void setHeaderErrors(Map<String, String> headerErrors) {
        this.headerErrors = headerErrors;
    }

    public Map<Integer, Map<String, String>> getLineErrors() {
        return lineErrors;
    }

    public void setLineErrors(Map<Integer, Map<String, String>> lineErrors) {
        this.lineErrors = lineErrors;
    }
}

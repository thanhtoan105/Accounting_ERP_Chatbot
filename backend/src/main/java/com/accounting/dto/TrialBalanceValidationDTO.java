package com.accounting.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Trial Balance validation results.
 * Used in export preflight check to ensure data integrity before generating reports.
 */
public class TrialBalanceValidationDTO {

    private boolean valid;
    private List<ValidationError> errors;

    public TrialBalanceValidationDTO() {
        this.valid = true;
        this.errors = new ArrayList<>();
    }

    public TrialBalanceValidationDTO(boolean valid, List<ValidationError> errors) {
        this.valid = valid;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void setErrors(List<ValidationError> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public void addError(ValidationError error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
        this.valid = false;
    }

    /**
     * Nested class representing a single validation error.
     */
    public static class ValidationError {
        private String code;
        private String message;
        private ValidationDetails details;
        private String helpUrl;

        public ValidationError() {
        }

        public ValidationError(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public ValidationError(String code, String message, ValidationDetails details, String helpUrl) {
            this.code = code;
            this.message = message;
            this.details = details;
            this.helpUrl = helpUrl;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public ValidationDetails getDetails() {
            return details;
        }

        public void setDetails(ValidationDetails details) {
            this.details = details;
        }

        public String getHelpUrl() {
            return helpUrl;
        }

        public void setHelpUrl(String helpUrl) {
            this.helpUrl = helpUrl;
        }
    }

    /**
     * Details about the validation error (e.g., amounts for imbalance).
     */
    public static class ValidationDetails {
        private BigDecimal totalDebit;
        private BigDecimal totalCredit;
        private BigDecimal difference;

        public ValidationDetails() {
        }

        public ValidationDetails(BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal difference) {
            this.totalDebit = totalDebit;
            this.totalCredit = totalCredit;
            this.difference = difference;
        }

        public BigDecimal getTotalDebit() {
            return totalDebit;
        }

        public void setTotalDebit(BigDecimal totalDebit) {
            this.totalDebit = totalDebit;
        }

        public BigDecimal getTotalCredit() {
            return totalCredit;
        }

        public void setTotalCredit(BigDecimal totalCredit) {
            this.totalCredit = totalCredit;
        }

        public BigDecimal getDifference() {
            return difference;
        }

        public void setDifference(BigDecimal difference) {
            this.difference = difference;
        }
    }
}

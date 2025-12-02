package com.accounting.dto.reconciliation;

import java.math.BigDecimal;

/**
 * Configuration DTO for auto-match algorithm.
 */
public class AutoMatchConfigDTO {

    private Integer dateTolerance = 3; // ±N days
    private BigDecimal amountTolerance = BigDecimal.ZERO; // percentage (0 = exact match)
    private BigDecimal minimumConfidence = new BigDecimal("0.7"); // minimum score to auto-match
    private Boolean autoApply = false; // automatically apply high-confidence matches

    public AutoMatchConfigDTO() {
    }

    // Getters and Setters
    public Integer getDateTolerance() {
        return dateTolerance;
    }

    public void setDateTolerance(Integer dateTolerance) {
        this.dateTolerance = dateTolerance;
    }

    public BigDecimal getAmountTolerance() {
        return amountTolerance;
    }

    public void setAmountTolerance(BigDecimal amountTolerance) {
        this.amountTolerance = amountTolerance;
    }

    public BigDecimal getMinimumConfidence() {
        return minimumConfidence;
    }

    public void setMinimumConfidence(BigDecimal minimumConfidence) {
        this.minimumConfidence = minimumConfidence;
    }

    public Boolean getAutoApply() {
        return autoApply;
    }

    public void setAutoApply(Boolean autoApply) {
        this.autoApply = autoApply;
    }
}

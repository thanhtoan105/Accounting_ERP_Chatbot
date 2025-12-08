package com.accounting.dto.reconciliation;

import java.math.BigDecimal;
import java.util.UUID;

import com.accounting.entity.reconciliation.AdjustmentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a reconciliation adjustment.
 */
public class CreateAdjustmentRequestDTO {

    private UUID statementLineId;

    @NotNull(message = "Adjustment type is required")
    private AdjustmentType adjustmentType;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @NotBlank(message = "Description is required")
    private String description;

    private String accountCode;

    public CreateAdjustmentRequestDTO() {
    }

    // Getters and Setters
    public UUID getStatementLineId() {
        return statementLineId;
    }

    public void setStatementLineId(UUID statementLineId) {
        this.statementLineId = statementLineId;
    }

    public AdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(AdjustmentType adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public void setAccountCode(String accountCode) {
        this.accountCode = accountCode;
    }
}

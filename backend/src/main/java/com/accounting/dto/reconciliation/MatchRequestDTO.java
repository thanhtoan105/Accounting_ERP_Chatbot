package com.accounting.dto.reconciliation;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for manual match operation.
 */
public class MatchRequestDTO {

    @NotNull(message = "Statement line ID is required")
    private UUID statementLineId;

    @NotNull(message = "Voucher ID is required")
    private UUID voucherId;

    private String notes;

    public MatchRequestDTO() {
    }

    // Getters and Setters
    public UUID getStatementLineId() {
        return statementLineId;
    }

    public void setStatementLineId(UUID statementLineId) {
        this.statementLineId = statementLineId;
    }

    public UUID getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(UUID voucherId) {
        this.voucherId = voucherId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

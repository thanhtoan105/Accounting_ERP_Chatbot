package com.accounting.dto.reconciliation;

import com.accounting.entity.reconciliation.AdjustmentStatus;
import com.accounting.entity.reconciliation.AdjustmentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for ReconciliationAdjustment responses.
 */
public class ReconciliationAdjustmentDTO {

    private UUID id;
    private UUID reconciliationId;
    private UUID statementLineId;
    private AdjustmentType adjustmentType;
    private BigDecimal amount;
    private String description;
    private String accountCode;
    private UUID voucherId;
    private AdjustmentStatus status;
    private Long createdById;
    private String createdByName;
    private Long approvedById;
    private String approvedByName;
    private Instant createdAt;
    private Instant approvedAt;

    public ReconciliationAdjustmentDTO() {
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReconciliationId() {
        return reconciliationId;
    }

    public void setReconciliationId(UUID reconciliationId) {
        this.reconciliationId = reconciliationId;
    }

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

    public UUID getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(UUID voucherId) {
        this.voucherId = voucherId;
    }

    public AdjustmentStatus getStatus() {
        return status;
    }

    public void setStatus(AdjustmentStatus status) {
        this.status = status;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public void setCreatedById(Long createdById) {
        this.createdById = createdById;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public Long getApprovedById() {
        return approvedById;
    }

    public void setApprovedById(Long approvedById) {
        this.approvedById = approvedById;
    }

    public String getApprovedByName() {
        return approvedByName;
    }

    public void setApprovedByName(String approvedByName) {
        this.approvedByName = approvedByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }
}

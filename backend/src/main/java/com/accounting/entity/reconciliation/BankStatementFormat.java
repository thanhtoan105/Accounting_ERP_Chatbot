package com.accounting.entity.reconciliation;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistent column mapping profile for bank statement imports.
 * Stores the column mapping configuration per bank account for reuse across imports.
 */
@Entity
@Table(name = "bank_statement_formats")
public class BankStatementFormat implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @NotNull
    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @Size(max = 100)
    @Column(name = "format_name", length = 100)
    private String formatName;

    @Size(max = 100)
    @Column(name = "date_column", length = 100)
    private String dateColumn;

    @Size(max = 100)
    @Column(name = "description_column", length = 100)
    private String descriptionColumn;

    @Size(max = 100)
    @Column(name = "reference_column", length = 100)
    private String referenceColumn;

    @Size(max = 100)
    @Column(name = "debit_column", length = 100)
    private String debitColumn;

    @Size(max = 100)
    @Column(name = "credit_column", length = 100)
    private String creditColumn;

    @Size(max = 100)
    @Column(name = "balance_column", length = 100)
    private String balanceColumn;

    @Size(max = 50)
    @Column(name = "date_format", length = 50)
    private String dateFormat = "yyyy-MM-dd";

    @Column(name = "skip_header_rows")
    private Integer skipHeaderRows = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public String getFormatName() {
        return formatName;
    }

    public void setFormatName(String formatName) {
        this.formatName = formatName;
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

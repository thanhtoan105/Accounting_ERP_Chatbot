package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO representing a voucher in Trial Balance drill-down results.
 * Contains summary information for display in the drill-down panel.
 */
public class DrillDownVoucherDTO {

    private UUID id;
    private String voucherNumber;
    private LocalDate voucherDate;
    private String description;
    private BigDecimal debit;
    private BigDecimal credit;
    private String voucherType;
    private String status;

    public DrillDownVoucherDTO() {
        this.debit = BigDecimal.ZERO;
        this.credit = BigDecimal.ZERO;
    }

    public DrillDownVoucherDTO(UUID id, String voucherNumber, LocalDate voucherDate,
            String description, BigDecimal debit, BigDecimal credit,
            String voucherType, String status) {
        this.id = id;
        this.voucherNumber = voucherNumber;
        this.voucherDate = voucherDate;
        this.description = description;
        this.debit = debit != null ? debit : BigDecimal.ZERO;
        this.credit = credit != null ? credit : BigDecimal.ZERO;
        this.voucherType = voucherType;
        this.status = status;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getVoucherNumber() {
        return voucherNumber;
    }

    public void setVoucherNumber(String voucherNumber) {
        this.voucherNumber = voucherNumber;
    }

    public LocalDate getVoucherDate() {
        return voucherDate;
    }

    public void setVoucherDate(LocalDate voucherDate) {
        this.voucherDate = voucherDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public void setDebit(BigDecimal debit) {
        this.debit = debit != null ? debit : BigDecimal.ZERO;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(BigDecimal credit) {
        this.credit = credit != null ? credit : BigDecimal.ZERO;
    }

    public String getVoucherType() {
        return voucherType;
    }

    public void setVoucherType(String voucherType) {
        this.voucherType = voucherType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

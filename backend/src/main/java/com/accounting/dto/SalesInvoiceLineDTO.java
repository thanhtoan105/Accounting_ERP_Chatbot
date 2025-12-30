package com.accounting.dto;

import java.math.BigDecimal;

import com.accounting.entity.VatRate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO for a sales invoice line item.
 */
public class SalesInvoiceLineDTO {

    private Integer lineNumber; // Optional - auto-assigned if not provided

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description; // Optional - can be derived from account/item

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be non-negative")
    private BigDecimal quantity = BigDecimal.ONE;

    @NotNull(message = "Unit price is required")
    @Positive(message = "Unit price must be positive")
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount = BigDecimal.ZERO;

    @NotNull(message = "VAT rate is required")
    private VatRate vatRate = VatRate.ZERO;

    @NotNull(message = "VAT amount is required")
    private BigDecimal vatAmount = BigDecimal.ZERO;

    // Optional dimension fields
    private Long itemId;

    public SalesInvoiceLineDTO() {
    }

    public SalesInvoiceLineDTO(
            Integer lineNumber,
            Long accountId,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount,
            VatRate vatRate,
            BigDecimal vatAmount,
            Long itemId) {
        this.lineNumber = lineNumber;
        this.accountId = accountId;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = amount;
        this.vatRate = vatRate;
        this.vatAmount = vatAmount;
        this.itemId = itemId;
    }

    // Getters and setters
    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public VatRate getVatRate() {
        return vatRate;
    }

    public void setVatRate(VatRate vatRate) {
        this.vatRate = vatRate;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public void setVatAmount(BigDecimal vatAmount) {
        this.vatAmount = vatAmount;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }
}

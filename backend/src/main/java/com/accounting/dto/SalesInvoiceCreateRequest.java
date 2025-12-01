package com.accounting.dto;

import com.accounting.entity.SalesInvoiceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for creating or updating a sales invoice with its line items.
 */
public class SalesInvoiceCreateRequest {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotBlank(message = "Invoice number is required")
    @Size(max = 50, message = "Invoice number must not exceed 50 characters")
    private String invoiceNumber;

    @NotNull(message = "Invoice date is required")
    private LocalDate invoiceDate;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    @NotBlank(message = "Reference is required")
    @Size(max = 100, message = "Reference must not exceed 100 characters")
    private String reference;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private java.math.BigDecimal vatAmount; // Header VAT amount for validation

    private SalesInvoiceStatus status = SalesInvoiceStatus.DRAFT;

    @Valid
    @NotNull(message = "Line items are required")
    private List<SalesInvoiceLineDTO> lines;

    private UUID id; // For updates

    private UUID originalInvoiceId; // For credit notes - references the original invoice

    public SalesInvoiceCreateRequest() {
    }

    public SalesInvoiceCreateRequest(
            Long customerId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String reference,
            String description,
            java.math.BigDecimal vatAmount,
            SalesInvoiceStatus status,
            List<SalesInvoiceLineDTO> lines,
            UUID id) {
        this.customerId = customerId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.reference = reference;
        this.description = description;
        this.vatAmount = vatAmount;
        this.status = status;
        this.lines = lines;
        this.id = id;
    }

    // Getters and setters
    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public java.math.BigDecimal getVatAmount() {
        return vatAmount;
    }

    public void setVatAmount(java.math.BigDecimal vatAmount) {
        this.vatAmount = vatAmount;
    }

    public SalesInvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(SalesInvoiceStatus status) {
        this.status = status;
    }

    public List<SalesInvoiceLineDTO> getLines() {
        return lines;
    }

    public void setLines(List<SalesInvoiceLineDTO> lines) {
        this.lines = lines;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOriginalInvoiceId() {
        return originalInvoiceId;
    }

    public void setOriginalInvoiceId(UUID originalInvoiceId) {
        this.originalInvoiceId = originalInvoiceId;
    }
}

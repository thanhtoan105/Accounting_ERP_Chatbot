package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.accounting.entity.SalesInvoiceStatus;

/**
 * DTO for listing sales invoices in a data table.
 * Contains summary information and status badges.
 */
public class SalesInvoiceListDTO {

    private UUID id;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String customerName;
    private String reference;
    private BigDecimal totalAmount;
    private SalesInvoiceStatus status;
    private Integer attachmentCount;

    public SalesInvoiceListDTO() {
    }

    public SalesInvoiceListDTO(
            UUID id,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String customerName,
            String reference,
            BigDecimal totalAmount,
            SalesInvoiceStatus status,
            Integer attachmentCount) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.customerName = customerName;
        this.reference = reference;
        this.totalAmount = totalAmount;
        this.status = status;
        this.attachmentCount = attachmentCount;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public SalesInvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(SalesInvoiceStatus status) {
        this.status = status;
    }

    public Integer getAttachmentCount() {
        return attachmentCount;
    }

    public void setAttachmentCount(Integer attachmentCount) {
        this.attachmentCount = attachmentCount;
    }
}

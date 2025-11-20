package com.accounting.dto;

import com.accounting.entity.SalesInvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for full SalesInvoice details. Used for single sales invoice retrieval.
 */
public class SalesInvoiceDTO {

    private UUID id;
    private Long companyId;
    private Long customerId;
    private String customerName;
    private String customerCode;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String reference;
    private String description;
    private SalesInvoiceStatus status;
    private BigDecimal totalAmount;
    private BigDecimal vatAmount;
    private Long createdById;
    private String createdByName;
    private Long approvedById;
    private String approvedByName;
    private UUID postedVoucherId;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer attachmentCount;
    private List<SalesInvoiceLineDTO> lines; // Sales invoice line items

    public SalesInvoiceDTO() {
    }

    public SalesInvoiceDTO(
            UUID id,
            Long companyId,
            Long customerId,
            String customerName,
            String customerCode,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            String reference,
            String description,
            SalesInvoiceStatus status,
            BigDecimal totalAmount,
            BigDecimal vatAmount,
            Long createdById,
            String createdByName,
            Long approvedById,
            String approvedByName,
            UUID postedVoucherId,
            Instant createdAt,
            Instant updatedAt,
            Integer attachmentCount) {
        this.id = id;
        this.companyId = companyId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerCode = customerCode;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.reference = reference;
        this.description = description;
        this.status = status;
        this.totalAmount = totalAmount;
        this.vatAmount = vatAmount;
        this.createdById = createdById;
        this.createdByName = createdByName;
        this.approvedById = approvedById;
        this.approvedByName = approvedByName;
        this.postedVoucherId = postedVoucherId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.attachmentCount = attachmentCount;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
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

    public SalesInvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(SalesInvoiceStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public void setVatAmount(BigDecimal vatAmount) {
        this.vatAmount = vatAmount;
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

    public UUID getPostedVoucherId() {
        return postedVoucherId;
    }

    public void setPostedVoucherId(UUID postedVoucherId) {
        this.postedVoucherId = postedVoucherId;
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

    public Integer getAttachmentCount() {
        return attachmentCount;
    }

    public void setAttachmentCount(Integer attachmentCount) {
        this.attachmentCount = attachmentCount;
    }

    public List<SalesInvoiceLineDTO> getLines() {
        return lines;
    }

    public void setLines(List<SalesInvoiceLineDTO> lines) {
        this.lines = lines;
    }
}

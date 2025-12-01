package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for detailed AR invoice view with payment history.
 * Used in aging drill-down to show complete invoice information.
 */
public class ARInvoiceDetailDTO {

    private UUID invoiceId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String reference;
    private String description;
    private String status;

    // Customer information
    private Long customerId;
    private String customerName;
    private String customerCode;

    // Amounts
    private BigDecimal totalAmount;
    private BigDecimal vatAmount;
    private BigDecimal amountPaid;
    private BigDecimal remainingBalance;

    // Aging information
    private Long daysOverdue;
    private String agingBucket;

    // Payment history
    private List<PaymentHistoryItem> paymentHistory;

    // Invoice lines (optional, for detailed view)
    private List<InvoiceLineItem> lineItems;

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    public void setRemainingBalance(BigDecimal remainingBalance) {
        this.remainingBalance = remainingBalance;
    }

    public Long getDaysOverdue() {
        return daysOverdue;
    }

    public void setDaysOverdue(Long daysOverdue) {
        this.daysOverdue = daysOverdue;
    }

    public String getAgingBucket() {
        return agingBucket;
    }

    public void setAgingBucket(String agingBucket) {
        this.agingBucket = agingBucket;
    }

    public List<PaymentHistoryItem> getPaymentHistory() {
        return paymentHistory;
    }

    public void setPaymentHistory(List<PaymentHistoryItem> paymentHistory) {
        this.paymentHistory = paymentHistory;
    }

    public List<InvoiceLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<InvoiceLineItem> lineItems) {
        this.lineItems = lineItems;
    }

    /**
     * Inner class for payment history items.
     */
    public static class PaymentHistoryItem {
        private UUID receiptId;
        private String receiptNumber;
        private LocalDate receiptDate;
        private BigDecimal allocatedAmount;
        private String paymentMethod;

        public UUID getReceiptId() {
            return receiptId;
        }

        public void setReceiptId(UUID receiptId) {
            this.receiptId = receiptId;
        }

        public String getReceiptNumber() {
            return receiptNumber;
        }

        public void setReceiptNumber(String receiptNumber) {
            this.receiptNumber = receiptNumber;
        }

        public LocalDate getReceiptDate() {
            return receiptDate;
        }

        public void setReceiptDate(LocalDate receiptDate) {
            this.receiptDate = receiptDate;
        }

        public BigDecimal getAllocatedAmount() {
            return allocatedAmount;
        }

        public void setAllocatedAmount(BigDecimal allocatedAmount) {
            this.allocatedAmount = allocatedAmount;
        }

        public String getPaymentMethod() {
            return paymentMethod;
        }

        public void setPaymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
        }
    }

    /**
     * Inner class for invoice line items.
     */
    public static class InvoiceLineItem {
        private Integer lineNumber;
        private String description;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private String accountCode;

        public Integer getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
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

        public String getAccountCode() {
            return accountCode;
        }

        public void setAccountCode(String accountCode) {
            this.accountCode = accountCode;
        }
    }
}

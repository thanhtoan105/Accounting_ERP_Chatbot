package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for detailed supplier statement with line-by-line bill items and payment events.
 */
public class DetailedStatementDTO extends SupplierStatementDTO {

  private List<DetailedBillItemDTO> billDetails = new ArrayList<>();

  public List<DetailedBillItemDTO> getBillDetails() {
    return billDetails;
  }

  public void setBillDetails(List<DetailedBillItemDTO> billDetails) {
    this.billDetails = billDetails;
  }

  /**
   * DTO for detailed bill with line items and payment events.
   */
  public static class DetailedBillItemDTO {

    private UUID billId;
    private String billNumber;
    private LocalDate billDate;
    private LocalDate dueDate;
    private String reference;
    private String description;
    private BigDecimal totalAmount;
    private BigDecimal remainingBalance;
    private List<BillLineItemDTO> lineItems = new ArrayList<>();
    private List<PaymentEventDTO> paymentEvents = new ArrayList<>();

    // Getters and setters
    public UUID getBillId() {
      return billId;
    }

    public void setBillId(UUID billId) {
      this.billId = billId;
    }

    public String getBillNumber() {
      return billNumber;
    }

    public void setBillNumber(String billNumber) {
      this.billNumber = billNumber;
    }

    public LocalDate getBillDate() {
      return billDate;
    }

    public void setBillDate(LocalDate billDate) {
      this.billDate = billDate;
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

    public BigDecimal getTotalAmount() {
      return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
      this.totalAmount = totalAmount;
    }

    public BigDecimal getRemainingBalance() {
      return remainingBalance;
    }

    public void setRemainingBalance(BigDecimal remainingBalance) {
      this.remainingBalance = remainingBalance;
    }

    public List<BillLineItemDTO> getLineItems() {
      return lineItems;
    }

    public void setLineItems(List<BillLineItemDTO> lineItems) {
      this.lineItems = lineItems;
    }

    public List<PaymentEventDTO> getPaymentEvents() {
      return paymentEvents;
    }

    public void setPaymentEvents(List<PaymentEventDTO> paymentEvents) {
      this.paymentEvents = paymentEvents;
    }
  }

  /**
   * DTO for bill line item detail.
   */
  public static class BillLineItemDTO {

    private String accountCode;
    private String accountName;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal vatAmount;

    // Getters and setters
    public String getAccountCode() {
      return accountCode;
    }

    public void setAccountCode(String accountCode) {
      this.accountCode = accountCode;
    }

    public String getAccountName() {
      return accountName;
    }

    public void setAccountName(String accountName) {
      this.accountName = accountName;
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

    public BigDecimal getVatAmount() {
      return vatAmount;
    }

    public void setVatAmount(BigDecimal vatAmount) {
      this.vatAmount = vatAmount;
    }
  }

  /**
   * DTO for payment event.
   */
  public static class PaymentEventDTO {

    private UUID paymentId;
    private String paymentNumber;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private BigDecimal allocatedAmount;
    private String reference;

    // Getters and setters
    public UUID getPaymentId() {
      return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
      this.paymentId = paymentId;
    }

    public String getPaymentNumber() {
      return paymentNumber;
    }

    public void setPaymentNumber(String paymentNumber) {
      this.paymentNumber = paymentNumber;
    }

    public LocalDate getPaymentDate() {
      return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
      this.paymentDate = paymentDate;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    public void setAmount(BigDecimal amount) {
      this.amount = amount;
    }

    public BigDecimal getAllocatedAmount() {
      return allocatedAmount;
    }

    public void setAllocatedAmount(BigDecimal allocatedAmount) {
      this.allocatedAmount = allocatedAmount;
    }

    public String getReference() {
      return reference;
    }

    public void setReference(String reference) {
      this.reference = reference;
    }
  }
}


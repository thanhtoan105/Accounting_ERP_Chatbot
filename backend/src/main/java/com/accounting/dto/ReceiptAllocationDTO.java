package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for receipt allocation details.
 */
public class ReceiptAllocationDTO {

  private UUID id;
  private UUID receiptId;
  private UUID salesInvoiceId;
  private String salesInvoiceNumber;
  private LocalDate salesInvoiceDate;
  private LocalDate salesInvoiceDueDate;
  private BigDecimal salesInvoiceTotalAmount;
  private BigDecimal salesInvoiceRemainingBalance;
  private BigDecimal allocatedAmount;
  private Integer allocationOrder;
  private Instant createdAt;

  public ReceiptAllocationDTO() {}

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getReceiptId() {
    return receiptId;
  }

  public void setReceiptId(UUID receiptId) {
    this.receiptId = receiptId;
  }

  public UUID getSalesInvoiceId() {
    return salesInvoiceId;
  }

  public void setSalesInvoiceId(UUID salesInvoiceId) {
    this.salesInvoiceId = salesInvoiceId;
  }

  public String getSalesInvoiceNumber() {
    return salesInvoiceNumber;
  }

  public void setSalesInvoiceNumber(String salesInvoiceNumber) {
    this.salesInvoiceNumber = salesInvoiceNumber;
  }

  public LocalDate getSalesInvoiceDate() {
    return salesInvoiceDate;
  }

  public void setSalesInvoiceDate(LocalDate salesInvoiceDate) {
    this.salesInvoiceDate = salesInvoiceDate;
  }

  public LocalDate getSalesInvoiceDueDate() {
    return salesInvoiceDueDate;
  }

  public void setSalesInvoiceDueDate(LocalDate salesInvoiceDueDate) {
    this.salesInvoiceDueDate = salesInvoiceDueDate;
  }

  public BigDecimal getSalesInvoiceTotalAmount() {
    return salesInvoiceTotalAmount;
  }

  public void setSalesInvoiceTotalAmount(BigDecimal salesInvoiceTotalAmount) {
    this.salesInvoiceTotalAmount = salesInvoiceTotalAmount;
  }

  public BigDecimal getSalesInvoiceRemainingBalance() {
    return salesInvoiceRemainingBalance;
  }

  public void setSalesInvoiceRemainingBalance(BigDecimal salesInvoiceRemainingBalance) {
    this.salesInvoiceRemainingBalance = salesInvoiceRemainingBalance;
  }

  public BigDecimal getAllocatedAmount() {
    return allocatedAmount;
  }

  public void setAllocatedAmount(BigDecimal allocatedAmount) {
    this.allocatedAmount = allocatedAmount;
  }

  public Integer getAllocationOrder() {
    return allocationOrder;
  }

  public void setAllocationOrder(Integer allocationOrder) {
    this.allocationOrder = allocationOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}

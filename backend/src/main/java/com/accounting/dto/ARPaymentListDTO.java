package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.accounting.entity.PaymentMethod;
import com.accounting.entity.ReceiptStatus;

/**
 * DTO for receipt list view (summary information).
 */
public class ARPaymentListDTO {

  private UUID id;
  private String receiptNumber;
  private LocalDate receiptDate;
  private Long customerId;
  private String customerName;
  private String customerCode;
  private BigDecimal amount;
  private Long cashAccountId;
  private String cashAccountName;
  private Long bankAccountId;
  private String bankAccountName;
  private PaymentMethod paymentMethod;
  private Boolean isStandalone;
  private ReceiptStatus status;
  private Integer allocationCount;
  private UUID linkedVoucherId;
  private String linkedVoucherNumber;

  public ARPaymentListDTO() {}

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public Long getCashAccountId() {
    return cashAccountId;
  }

  public void setCashAccountId(Long cashAccountId) {
    this.cashAccountId = cashAccountId;
  }

  public String getCashAccountName() {
    return cashAccountName;
  }

  public void setCashAccountName(String cashAccountName) {
    this.cashAccountName = cashAccountName;
  }

  public Long getBankAccountId() {
    return bankAccountId;
  }

  public void setBankAccountId(Long bankAccountId) {
    this.bankAccountId = bankAccountId;
  }

  public String getBankAccountName() {
    return bankAccountName;
  }

  public void setBankAccountName(String bankAccountName) {
    this.bankAccountName = bankAccountName;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(PaymentMethod paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  public Boolean getIsStandalone() {
    return isStandalone;
  }

  public void setIsStandalone(Boolean isStandalone) {
    this.isStandalone = isStandalone;
  }

  public ReceiptStatus getStatus() {
    return status;
  }

  public void setStatus(ReceiptStatus status) {
    this.status = status;
  }

  public Integer getAllocationCount() {
    return allocationCount;
  }

  public void setAllocationCount(Integer allocationCount) {
    this.allocationCount = allocationCount;
  }

  public UUID getLinkedVoucherId() {
    return linkedVoucherId;
  }

  public void setLinkedVoucherId(UUID linkedVoucherId) {
    this.linkedVoucherId = linkedVoucherId;
  }

  public String getLinkedVoucherNumber() {
    return linkedVoucherNumber;
  }

  public void setLinkedVoucherNumber(String linkedVoucherNumber) {
    this.linkedVoucherNumber = linkedVoucherNumber;
  }
}

package com.accounting.dto;

import com.accounting.entity.PaymentMethod;
import com.accounting.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for payment list view (summary information).
 */
public class APPaymentListDTO {

  private UUID id;
  private String paymentNumber;
  private LocalDate paymentDate;
  private Long supplierId;
  private String supplierName;
  private String supplierCode;
  private BigDecimal amount;
  private Long cashAccountId;
  private String cashAccountName;
  private Long bankAccountId;
  private String bankAccountName;
  private PaymentMethod paymentMethod;
  private Boolean isStandalone;
  private PaymentStatus status;
  private Integer allocationCount;
  private UUID linkedVoucherId;
  private String linkedVoucherNumber;

  public APPaymentListDTO() {}

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public String getSupplierName() {
    return supplierName;
  }

  public void setSupplierName(String supplierName) {
    this.supplierName = supplierName;
  }

  public String getSupplierCode() {
    return supplierCode;
  }

  public void setSupplierCode(String supplierCode) {
    this.supplierCode = supplierCode;
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

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
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


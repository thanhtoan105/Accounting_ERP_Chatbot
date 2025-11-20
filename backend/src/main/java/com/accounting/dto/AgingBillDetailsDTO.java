package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing bill details for aging drill-down.
 */
public class AgingBillDetailsDTO {

  private UUID billId;
  private String billNumber;
  private LocalDate billDate;
  private LocalDate dueDate;
  private BigDecimal totalAmount;
  private BigDecimal remainingBalance;
  private String status;
  private String reference;
  private List<PaymentHistoryDTO> paymentHistory = new ArrayList<>();

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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public List<PaymentHistoryDTO> getPaymentHistory() {
    return paymentHistory;
  }

  public void setPaymentHistory(List<PaymentHistoryDTO> paymentHistory) {
    this.paymentHistory = paymentHistory;
  }

  /**
   * DTO for payment history entry.
   */
  public static class PaymentHistoryDTO {
    private UUID paymentId;
    private String paymentNumber;
    private LocalDate paymentDate;
    private BigDecimal paymentAmount;
    private BigDecimal allocatedAmount;

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

    public BigDecimal getPaymentAmount() {
      return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
      this.paymentAmount = paymentAmount;
    }

    public BigDecimal getAllocatedAmount() {
      return allocatedAmount;
    }

    public void setAllocatedAmount(BigDecimal allocatedAmount) {
      this.allocatedAmount = allocatedAmount;
    }
  }
}


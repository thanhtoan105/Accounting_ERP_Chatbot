package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.accounting.entity.ARStatementHistory;

/**
 * DTO for AR customer statement (summary view). Contains statement metadata and invoice-level items.
 */
public class ARStatementSummaryDTO {

  private UUID id;
  private Long customerId;
  private String customerName;
  private String customerCode;
  private String customerAddress;
  private String customerTaxCode;
  private ARStatementHistory.StatementFormat format;
  private LocalDate asOfDate;
  private Instant generatedAt;
  private String generatedByName;
  private String statementHash;
  private BigDecimal totalInvoices;
  private BigDecimal totalPaid;
  private BigDecimal totalOutstanding;
  private List<StatementInvoiceDTO> invoices = new ArrayList<>();

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public String getCustomerAddress() {
    return customerAddress;
  }

  public void setCustomerAddress(String customerAddress) {
    this.customerAddress = customerAddress;
  }

  public String getCustomerTaxCode() {
    return customerTaxCode;
  }

  public void setCustomerTaxCode(String customerTaxCode) {
    this.customerTaxCode = customerTaxCode;
  }

  public ARStatementHistory.StatementFormat getFormat() {
    return format;
  }

  public void setFormat(ARStatementHistory.StatementFormat format) {
    this.format = format;
  }

  public LocalDate getAsOfDate() {
    return asOfDate;
  }

  public void setAsOfDate(LocalDate asOfDate) {
    this.asOfDate = asOfDate;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant generatedAt) {
    this.generatedAt = generatedAt;
  }

  public String getGeneratedByName() {
    return generatedByName;
  }

  public void setGeneratedByName(String generatedByName) {
    this.generatedByName = generatedByName;
  }

  public String getStatementHash() {
    return statementHash;
  }

  public void setStatementHash(String statementHash) {
    this.statementHash = statementHash;
  }

  public BigDecimal getTotalInvoices() {
    return totalInvoices;
  }

  public void setTotalInvoices(BigDecimal totalInvoices) {
    this.totalInvoices = totalInvoices;
  }

  public BigDecimal getTotalPaid() {
    return totalPaid;
  }

  public void setTotalPaid(BigDecimal totalPaid) {
    this.totalPaid = totalPaid;
  }

  public BigDecimal getTotalOutstanding() {
    return totalOutstanding;
  }

  public void setTotalOutstanding(BigDecimal totalOutstanding) {
    this.totalOutstanding = totalOutstanding;
  }

  public List<StatementInvoiceDTO> getInvoices() {
    return invoices;
  }

  public void setInvoices(List<StatementInvoiceDTO> invoices) {
    this.invoices = invoices;
  }

  /**
   * DTO for a statement invoice line item (summary view).
   */
  public static class StatementInvoiceDTO {
    private UUID invoiceId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private BigDecimal invoiceAmount;
    private BigDecimal amountPaid;
    private BigDecimal balance;
    private BigDecimal runningBalance;

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

    public BigDecimal getInvoiceAmount() {
      return invoiceAmount;
    }

    public void setInvoiceAmount(BigDecimal invoiceAmount) {
      this.invoiceAmount = invoiceAmount;
    }

    public BigDecimal getAmountPaid() {
      return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
      this.amountPaid = amountPaid;
    }

    public BigDecimal getBalance() {
      return balance;
    }

    public void setBalance(BigDecimal balance) {
      this.balance = balance;
    }

    public BigDecimal getRunningBalance() {
      return runningBalance;
    }

    public void setRunningBalance(BigDecimal runningBalance) {
      this.runningBalance = runningBalance;
    }
  }
}

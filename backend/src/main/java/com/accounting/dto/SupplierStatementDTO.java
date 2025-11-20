package com.accounting.dto;

import com.accounting.entity.SupplierStatementHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for supplier statement (summary or detailed). Contains statement metadata and bill/payment
 * items.
 */
public class SupplierStatementDTO {

  private UUID id;
  private Long supplierId;
  private String supplierName;
  private String supplierCode;
  private SupplierStatementHistory.StatementType statementType;
  private LocalDate startDate;
  private LocalDate endDate;
  private Instant generationDate;
  private String generatedByName;
  private SupplierStatementHistory.ExportFormat format;
  private BigDecimal openingBalance;
  private BigDecimal closingBalance;
  private BigDecimal totalDebits; // Bills added
  private BigDecimal totalCredits; // Payments made
  private List<StatementLineItemDTO> items = new ArrayList<>();

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public SupplierStatementHistory.StatementType getStatementType() {
    return statementType;
  }

  public void setStatementType(SupplierStatementHistory.StatementType statementType) {
    this.statementType = statementType;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public Instant getGenerationDate() {
    return generationDate;
  }

  public void setGenerationDate(Instant generationDate) {
    this.generationDate = generationDate;
  }

  public String getGeneratedByName() {
    return generatedByName;
  }

  public void setGeneratedByName(String generatedByName) {
    this.generatedByName = generatedByName;
  }

  public SupplierStatementHistory.ExportFormat getFormat() {
    return format;
  }

  public void setFormat(SupplierStatementHistory.ExportFormat format) {
    this.format = format;
  }

  public BigDecimal getOpeningBalance() {
    return openingBalance;
  }

  public void setOpeningBalance(BigDecimal openingBalance) {
    this.openingBalance = openingBalance;
  }

  public BigDecimal getClosingBalance() {
    return closingBalance;
  }

  public void setClosingBalance(BigDecimal closingBalance) {
    this.closingBalance = closingBalance;
  }

  public BigDecimal getTotalDebits() {
    return totalDebits;
  }

  public void setTotalDebits(BigDecimal totalDebits) {
    this.totalDebits = totalDebits;
  }

  public BigDecimal getTotalCredits() {
    return totalCredits;
  }

  public void setTotalCredits(BigDecimal totalCredits) {
    this.totalCredits = totalCredits;
  }

  public List<StatementLineItemDTO> getItems() {
    return items;
  }

  public void setItems(List<StatementLineItemDTO> items) {
    this.items = items;
  }

  /**
   * DTO for a statement line item (bill or payment).
   */
  public static class StatementLineItemDTO {

    private String type; // "BILL" or "PAYMENT"
    private LocalDate date;
    private String reference;
    private String description;
    private BigDecimal debit; // Bill amount
    private BigDecimal credit; // Payment amount
    private BigDecimal balance;
    private UUID billId;
    private String billNumber;
    private UUID paymentId;
    private String paymentNumber;

    // Getters and setters
    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public LocalDate getDate() {
      return date;
    }

    public void setDate(LocalDate date) {
      this.date = date;
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

    public BigDecimal getDebit() {
      return debit;
    }

    public void setDebit(BigDecimal debit) {
      this.debit = debit;
    }

    public BigDecimal getCredit() {
      return credit;
    }

    public void setCredit(BigDecimal credit) {
      this.credit = credit;
    }

    public BigDecimal getBalance() {
      return balance;
    }

    public void setBalance(BigDecimal balance) {
      this.balance = balance;
    }

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
  }
}


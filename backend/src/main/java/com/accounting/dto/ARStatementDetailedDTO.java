package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for AR customer statement (detailed view). Contains statement metadata and transaction-level items
 * including receipts, credits, and adjustments with running balances.
 */
public class ARStatementDetailedDTO extends ARStatementSummaryDTO {

  private List<StatementTransactionDTO> transactions = new ArrayList<>();

  public List<StatementTransactionDTO> getTransactions() {
    return transactions;
  }

  public void setTransactions(List<StatementTransactionDTO> transactions) {
    this.transactions = transactions;
  }

  /**
   * DTO for a statement transaction (invoice, receipt, credit, adjustment).
   */
  public static class StatementTransactionDTO {
    private String type; // "INVOICE", "RECEIPT", "CREDIT", "ADJUSTMENT"
    private UUID invoiceId;
    private String invoiceNumber;
    private LocalDate transactionDate;
    private String reference;
    private String description;
    private BigDecimal debit; // Invoice amount
    private BigDecimal credit; // Payment/receipt amount
    private BigDecimal runningBalance;
    private UUID receiptId;
    private String receiptNumber;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

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

    public LocalDate getTransactionDate() {
      return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
      this.transactionDate = transactionDate;
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

    public BigDecimal getRunningBalance() {
      return runningBalance;
    }

    public void setRunningBalance(BigDecimal runningBalance) {
      this.runningBalance = runningBalance;
    }

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
  }
}

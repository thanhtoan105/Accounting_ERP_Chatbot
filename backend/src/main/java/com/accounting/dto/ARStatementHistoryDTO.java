package com.accounting.dto;

import com.accounting.entity.ARStatementHistory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for AR statement history record.
 */
public class ARStatementHistoryDTO {

  private UUID id;
  private Long customerId;
  private String customerName;
  private String statementNumber;
  private ARStatementHistory.StatementFormat format;
  private Instant generatedAt;
  private Long generatedById;
  private String generatedByName;
  private LocalDate asOfDate;
  private Integer exportCount;
  private Integer sentCount;
  private String statementHash;

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

  public String getStatementNumber() {
    return statementNumber;
  }

  public void setStatementNumber(String statementNumber) {
    this.statementNumber = statementNumber;
  }

  public ARStatementHistory.StatementFormat getFormat() {
    return format;
  }

  public void setFormat(ARStatementHistory.StatementFormat format) {
    this.format = format;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant generatedAt) {
    this.generatedAt = generatedAt;
  }

  public Long getGeneratedById() {
    return generatedById;
  }

  public void setGeneratedById(Long generatedById) {
    this.generatedById = generatedById;
  }

  public String getGeneratedByName() {
    return generatedByName;
  }

  public void setGeneratedByName(String generatedByName) {
    this.generatedByName = generatedByName;
  }

  public LocalDate getAsOfDate() {
    return asOfDate;
  }

  public void setAsOfDate(LocalDate asOfDate) {
    this.asOfDate = asOfDate;
  }

  public Integer getExportCount() {
    return exportCount;
  }

  public void setExportCount(Integer exportCount) {
    this.exportCount = exportCount;
  }

  public Integer getSentCount() {
    return sentCount;
  }

  public void setSentCount(Integer sentCount) {
    this.sentCount = sentCount;
  }

  public String getStatementHash() {
    return statementHash;
  }

  public void setStatementHash(String statementHash) {
    this.statementHash = statementHash;
  }
}


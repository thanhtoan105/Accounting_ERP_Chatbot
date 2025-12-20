package com.accounting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.accounting.entity.SupplierStatementHistory;

/**
 * DTO for supplier statement history listing.
 */
public class SupplierStatementHistoryDTO {

  private UUID id;
  private Long supplierId;
  private String supplierName;
  private String supplierCode;
  private SupplierStatementHistory.StatementType statementType;
  private Instant generationDate;
  private String generatedByName;
  private SupplierStatementHistory.ExportFormat format;
  private String hash;
  private Instant sentDate;
  private List<String> sentTo;
  private Integer viewCount;
  private Integer downloadCount;
  private LocalDate startDate;
  private LocalDate endDate;

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

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  public Instant getSentDate() {
    return sentDate;
  }

  public void setSentDate(Instant sentDate) {
    this.sentDate = sentDate;
  }

  public List<String> getSentTo() {
    return sentTo;
  }

  public void setSentTo(List<String> sentTo) {
    this.sentTo = sentTo;
  }

  public Integer getViewCount() {
    return viewCount;
  }

  public void setViewCount(Integer viewCount) {
    this.viewCount = viewCount;
  }

  public Integer getDownloadCount() {
    return downloadCount;
  }

  public void setDownloadCount(Integer downloadCount) {
    this.downloadCount = downloadCount;
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
}

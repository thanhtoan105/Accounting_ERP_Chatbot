package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight DTO for Voucher list view. Excludes full details for performance.
 */
public class VoucherListDTO {

  private UUID id;
  private String voucherNumber;
  private LocalDate voucherDate;
  private String type; // Description or voucher type/category
  private BigDecimal totalDebit;
  private BigDecimal totalCredit;
  private String status;
  private String enteredByName;
  private String postedByName;
  private String arApEntity; // Customer/Supplier name if applicable
  private Boolean hasReversal; // Reversal badge indicator
  private Integer attachmentCount;
  private String currency;

  public VoucherListDTO() {}

  public VoucherListDTO(
      UUID id,
      String voucherNumber,
      LocalDate voucherDate,
      String type,
      BigDecimal totalDebit,
      BigDecimal totalCredit,
      String status,
      String enteredByName,
      String postedByName,
      String arApEntity,
      Boolean hasReversal,
      Integer attachmentCount,
      String currency) {
    this.id = id;
    this.voucherNumber = voucherNumber;
    this.voucherDate = voucherDate;
    this.type = type;
    this.totalDebit = totalDebit;
    this.totalCredit = totalCredit;
    this.status = status;
    this.enteredByName = enteredByName;
    this.postedByName = postedByName;
    this.arApEntity = arApEntity;
    this.hasReversal = hasReversal;
    this.attachmentCount = attachmentCount;
    this.currency = currency;
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getVoucherNumber() {
    return voucherNumber;
  }

  public void setVoucherNumber(String voucherNumber) {
    this.voucherNumber = voucherNumber;
  }

  public LocalDate getVoucherDate() {
    return voucherDate;
  }

  public void setVoucherDate(LocalDate voucherDate) {
    this.voucherDate = voucherDate;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public BigDecimal getTotalDebit() {
    return totalDebit;
  }

  public void setTotalDebit(BigDecimal totalDebit) {
    this.totalDebit = totalDebit;
  }

  public BigDecimal getTotalCredit() {
    return totalCredit;
  }

  public void setTotalCredit(BigDecimal totalCredit) {
    this.totalCredit = totalCredit;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getEnteredByName() {
    return enteredByName;
  }

  public void setEnteredByName(String enteredByName) {
    this.enteredByName = enteredByName;
  }

  public String getPostedByName() {
    return postedByName;
  }

  public void setPostedByName(String postedByName) {
    this.postedByName = postedByName;
  }

  public String getArApEntity() {
    return arApEntity;
  }

  public void setArApEntity(String arApEntity) {
    this.arApEntity = arApEntity;
  }

  public Boolean getHasReversal() {
    return hasReversal;
  }

  public void setHasReversal(Boolean hasReversal) {
    this.hasReversal = hasReversal;
  }

  public Integer getAttachmentCount() {
    return attachmentCount;
  }

  public void setAttachmentCount(Integer attachmentCount) {
    this.attachmentCount = attachmentCount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }
}

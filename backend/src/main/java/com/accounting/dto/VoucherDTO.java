package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for full Voucher details. Used for single voucher retrieval.
 */
public class VoucherDTO {

  private UUID id;
  private Long companyId;
  private String voucherNumber;
  private LocalDate voucherDate;
  private UUID periodId;
  private String description;
  private String status;
  private String currency;
  private BigDecimal totalDebit;
  private BigDecimal totalCredit;
  private Long enteredBy;
  private String enteredByName;
  private Long postedBy;
  private String postedByName;
  private Instant postedAt;
  private UUID reversalOf;
  private UUID reversedByVoucherId; // The reversal voucher that reverses this voucher
  private Long reversedBy; // Deprecated: User ID who created the reversal
  private String reversedByName;
  private Instant createdAt;
  private Instant updatedAt;
  private Long version; // Optimistic locking version
  private Integer attachmentCount;
  private List<VoucherLineDTO> lines; // Voucher line items

  public VoucherDTO() {}

  public VoucherDTO(
      UUID id,
      Long companyId,
      String voucherNumber,
      LocalDate voucherDate,
      UUID periodId,
      String description,
      String status,
      String currency,
      BigDecimal totalDebit,
      BigDecimal totalCredit,
      Long enteredBy,
      String enteredByName,
      Long postedBy,
      String postedByName,
      Instant postedAt,
      UUID reversalOf,
      UUID reversedByVoucherId,
      Long reversedBy,
      String reversedByName,
      Instant createdAt,
      Instant updatedAt,
      Long version,
      Integer attachmentCount) {
    this.id = id;
    this.companyId = companyId;
    this.voucherNumber = voucherNumber;
    this.voucherDate = voucherDate;
    this.periodId = periodId;
    this.description = description;
    this.status = status;
    this.currency = currency;
    this.totalDebit = totalDebit;
    this.totalCredit = totalCredit;
    this.enteredBy = enteredBy;
    this.enteredByName = enteredByName;
    this.postedBy = postedBy;
    this.postedByName = postedByName;
    this.postedAt = postedAt;
    this.reversalOf = reversalOf;
    this.reversedByVoucherId = reversedByVoucherId;
    this.reversedBy = reversedBy;
    this.reversedByName = reversedByName;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
    this.attachmentCount = attachmentCount;
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
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

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
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

  public Long getEnteredBy() {
    return enteredBy;
  }

  public void setEnteredBy(Long enteredBy) {
    this.enteredBy = enteredBy;
  }

  public String getEnteredByName() {
    return enteredByName;
  }

  public void setEnteredByName(String enteredByName) {
    this.enteredByName = enteredByName;
  }

  public Long getPostedBy() {
    return postedBy;
  }

  public void setPostedBy(Long postedBy) {
    this.postedBy = postedBy;
  }

  public String getPostedByName() {
    return postedByName;
  }

  public void setPostedByName(String postedByName) {
    this.postedByName = postedByName;
  }

  public Instant getPostedAt() {
    return postedAt;
  }

  public void setPostedAt(Instant postedAt) {
    this.postedAt = postedAt;
  }

  public UUID getReversalOf() {
    return reversalOf;
  }

  public void setReversalOf(UUID reversalOf) {
    this.reversalOf = reversalOf;
  }

  public UUID getReversedByVoucherId() {
    return reversedByVoucherId;
  }

  public void setReversedByVoucherId(UUID reversedByVoucherId) {
    this.reversedByVoucherId = reversedByVoucherId;
  }

  public Long getReversedBy() {
    return reversedBy;
  }

  public void setReversedBy(Long reversedBy) {
    this.reversedBy = reversedBy;
  }

  public String getReversedByName() {
    return reversedByName;
  }

  public void setReversedByName(String reversedByName) {
    this.reversedByName = reversedByName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public Integer getAttachmentCount() {
    return attachmentCount;
  }

  public void setAttachmentCount(Integer attachmentCount) {
    this.attachmentCount = attachmentCount;
  }

  public List<VoucherLineDTO> getLines() {
    return lines;
  }

  public void setLines(List<VoucherLineDTO> lines) {
    this.lines = lines;
  }
}

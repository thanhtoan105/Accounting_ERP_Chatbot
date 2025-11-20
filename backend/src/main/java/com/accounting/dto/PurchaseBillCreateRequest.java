package com.accounting.dto;

import com.accounting.entity.PurchaseBillStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for creating or updating a purchase bill with its line items.
 */
public class PurchaseBillCreateRequest {

  @NotNull(message = "Supplier ID is required")
  private Long supplierId;

  @NotBlank(message = "Bill number is required")
  @Size(max = 50, message = "Bill number must not exceed 50 characters")
  private String billNumber;

  @NotNull(message = "Bill date is required")
  private LocalDate billDate;

  @NotNull(message = "Due date is required")
  private LocalDate dueDate;

  @NotBlank(message = "Reference is required")
  @Size(max = 100, message = "Reference must not exceed 100 characters")
  private String reference;

  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  private java.math.BigDecimal vatAmount; // Header VAT amount for validation

  private PurchaseBillStatus status = PurchaseBillStatus.DRAFT;

  @Valid
  @NotNull(message = "Line items are required")
  private List<PurchaseBillLineDTO> lines;

  private UUID id; // For updates

  public PurchaseBillCreateRequest() {}

  public PurchaseBillCreateRequest(
      Long supplierId,
      String billNumber,
      LocalDate billDate,
      LocalDate dueDate,
      String reference,
      String description,
      java.math.BigDecimal vatAmount,
      PurchaseBillStatus status,
      List<PurchaseBillLineDTO> lines,
      UUID id) {
    this.supplierId = supplierId;
    this.billNumber = billNumber;
    this.billDate = billDate;
    this.dueDate = dueDate;
    this.reference = reference;
    this.description = description;
    this.vatAmount = vatAmount;
    this.status = status;
    this.lines = lines;
    this.id = id;
  }

  // Getters and setters
  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
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

  public java.math.BigDecimal getVatAmount() {
    return vatAmount;
  }

  public void setVatAmount(java.math.BigDecimal vatAmount) {
    this.vatAmount = vatAmount;
  }

  public PurchaseBillStatus getStatus() {
    return status;
  }

  public void setStatus(PurchaseBillStatus status) {
    this.status = status;
  }

  public List<PurchaseBillLineDTO> getLines() {
    return lines;
  }

  public void setLines(List<PurchaseBillLineDTO> lines) {
    this.lines = lines;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }
}


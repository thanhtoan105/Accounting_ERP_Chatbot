package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ReceiptAllocation entity representing the allocation of an AR receipt to sales invoices.
 * Supports partial and full allocation to multiple invoices.
 */
@Entity
@Table(name = "receipt_allocations")
public class ReceiptAllocation implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "receipt_id", nullable = false)
  private UUID receiptId;

  @NotNull
  @Column(name = "sales_invoice_id", nullable = false)
  private UUID salesInvoiceId;

  @NotNull
  @Positive
  @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal allocatedAmount;

  @NotNull
  @Column(name = "allocation_order", nullable = false)
  private Integer allocationOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "receipt_id", insertable = false, updatable = false)
  private ARPayment receipt;

  @ManyToOne
  @JoinColumn(name = "sales_invoice_id", insertable = false, updatable = false)
  private SalesInvoice salesInvoice;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public UUID getReceiptId() {
    return receiptId;
  }

  public void setReceiptId(UUID receiptId) {
    this.receiptId = receiptId;
  }

  public UUID getSalesInvoiceId() {
    return salesInvoiceId;
  }

  public void setSalesInvoiceId(UUID salesInvoiceId) {
    this.salesInvoiceId = salesInvoiceId;
  }

  public BigDecimal getAllocatedAmount() {
    return allocatedAmount;
  }

  public void setAllocatedAmount(BigDecimal allocatedAmount) {
    this.allocatedAmount = allocatedAmount;
  }

  public Integer getAllocationOrder() {
    return allocationOrder;
  }

  public void setAllocationOrder(Integer allocationOrder) {
    this.allocationOrder = allocationOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  // Relationship getters (read-only)
  public ARPayment getReceipt() {
    return receipt;
  }

  public void setReceipt(ARPayment receipt) {
    this.receipt = receipt;
  }

  public SalesInvoice getSalesInvoice() {
    return salesInvoice;
  }

  public void setSalesInvoice(SalesInvoice salesInvoice) {
    this.salesInvoice = salesInvoice;
  }
}

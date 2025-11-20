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
 * PaymentAllocation entity representing the allocation of a payment to purchase bills.
 * Implements FIFO allocation with manual override capability.
 */
@Entity
@Table(name = "payment_allocations")
public class PaymentAllocation implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "payment_id", nullable = false)
  private UUID paymentId;

  @NotNull
  @Column(name = "purchase_bill_id", nullable = false)
  private UUID purchaseBillId;

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
  @JoinColumn(name = "payment_id", insertable = false, updatable = false)
  private APPayment payment;

  @ManyToOne
  @JoinColumn(name = "purchase_bill_id", insertable = false, updatable = false)
  private PurchaseBill purchaseBill;

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

  public UUID getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(UUID paymentId) {
    this.paymentId = paymentId;
  }

  public UUID getPurchaseBillId() {
    return purchaseBillId;
  }

  public void setPurchaseBillId(UUID purchaseBillId) {
    this.purchaseBillId = purchaseBillId;
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
  public APPayment getPayment() {
    return payment;
  }

  public void setPayment(APPayment payment) {
    this.payment = payment;
  }

  public PurchaseBill getPurchaseBill() {
    return purchaseBill;
  }

  public void setPurchaseBill(PurchaseBill purchaseBill) {
    this.purchaseBill = purchaseBill;
  }
}


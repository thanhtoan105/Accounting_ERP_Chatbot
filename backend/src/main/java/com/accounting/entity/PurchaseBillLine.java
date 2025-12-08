package com.accounting.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * PurchaseBillLine entity representing individual line items within a purchase bill.
 * Each line represents a single expense or asset entry with quantity, price, and VAT.
 */
@Entity
@Table(name = "purchase_bill_lines")
public class PurchaseBillLine implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "purchase_bill_id", nullable = false)
  private UUID purchaseBillId;

  @NotNull
  @Column(name = "line_number", nullable = false)
  private Integer lineNumber;

  @NotNull
  @Column(name = "account_id", nullable = false)
  private Long accountId; // ChartOfAccount ID

  @NotBlank
  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @NotNull
  @Min(0)
  @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantity = BigDecimal.ONE;

  @NotNull
  @Positive
  @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal unitPrice = BigDecimal.ZERO;

  @NotNull
  @Positive
  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount = BigDecimal.ZERO;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "vat_rate", nullable = false, length = 10)
  private VatRate vatRate = VatRate.ZERO;

  @NotNull
  @Column(name = "vat_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal vatAmount = BigDecimal.ZERO;

  // Optional dimension fields
  @Column(name = "cost_center_id")
  private Long costCenterId;

  @Column(name = "item_id")
  private Long itemId;

  // Company ID for company scoping (inherited via PurchaseBill relationship, but stored for direct queries)
  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "purchase_bill_id", insertable = false, updatable = false)
  private PurchaseBill purchaseBill;

  @ManyToOne
  @JoinColumn(name = "account_id", insertable = false, updatable = false)
  private ChartOfAccount account;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getPurchaseBillId() {
    return purchaseBillId;
  }

  public void setPurchaseBillId(UUID purchaseBillId) {
    this.purchaseBillId = purchaseBillId;
  }

  public Integer getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(Integer lineNumber) {
    this.lineNumber = lineNumber;
  }

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal unitPrice) {
    this.unitPrice = unitPrice;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public VatRate getVatRate() {
    return vatRate;
  }

  public void setVatRate(VatRate vatRate) {
    this.vatRate = vatRate;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }

  public void setVatAmount(BigDecimal vatAmount) {
    this.vatAmount = vatAmount;
  }

  public Long getCostCenterId() {
    return costCenterId;
  }

  public void setCostCenterId(Long costCenterId) {
    this.costCenterId = costCenterId;
  }

  public Long getItemId() {
    return itemId;
  }

  public void setItemId(Long itemId) {
    this.itemId = itemId;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
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

  // Relationship getters (read-only)
  public PurchaseBill getPurchaseBill() {
    return purchaseBill;
  }

  public void setPurchaseBill(PurchaseBill purchaseBill) {
    this.purchaseBill = purchaseBill;
  }

  public ChartOfAccount getAccount() {
    return account;
  }

  public void setAccount(ChartOfAccount account) {
    this.account = account;
  }
}

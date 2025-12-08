package com.accounting.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * APPayment entity representing supplier payments in the accounts payable module.
 * Payments can be linked to purchase bills or standalone (advance payments).
 */
@Entity
@Table(name = "ap_payments")
public class APPayment implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "supplier_id", nullable = false)
  private Long supplierId;

  @NotBlank
  @Size(max = 50)
  @Column(name = "payment_number", nullable = false, length = 50)
  private String paymentNumber;

  @NotNull
  @Column(name = "payment_date", nullable = false)
  private LocalDate paymentDate;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "cash_account_id")
  private Long cashAccountId;

  @Column(name = "bank_account_id")
  private Long bankAccountId;

  @NotBlank
  @Size(max = 255)
  @Column(name = "payee", nullable = false, length = 255)
  private String payee;

  @NotNull
  @Positive
  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Size(max = 500)
  @Column(name = "reference", length = 500)
  private String reference;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false, length = 20)
  private PaymentMethod paymentMethod;

  @Size(max = 1000)
  @Column(name = "payment_proof_url", length = 1000)
  private String paymentProofUrl;

  @NotNull
  @Column(name = "is_standalone", nullable = false)
  private Boolean isStandalone = false;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PaymentStatus status = PaymentStatus.DRAFT;

  @NotNull
  @Column(name = "created_by_id", nullable = false)
  private Long createdById;

  @Column(name = "approved_by_id")
  private Long approvedById;

  @Column(name = "linked_voucher_id")
  private UUID linkedVoucherId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "posted_at")
  private Instant postedAt;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne
  @JoinColumn(name = "supplier_id", insertable = false, updatable = false)
  private Supplier supplier;

  @ManyToOne
  @JoinColumn(name = "cash_account_id", insertable = false, updatable = false)
  private BankAccount cashAccount;

  @ManyToOne
  @JoinColumn(name = "bank_account_id", insertable = false, updatable = false)
  private BankAccount bankAccount;

  @ManyToOne
  @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
  private User createdBy;

  @ManyToOne
  @JoinColumn(name = "approved_by_id", insertable = false, updatable = false)
  private User approvedBy;

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

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public String getPaymentNumber() {
    return paymentNumber;
  }

  public void setPaymentNumber(String paymentNumber) {
    this.paymentNumber = paymentNumber;
  }

  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  public void setPaymentDate(LocalDate paymentDate) {
    this.paymentDate = paymentDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  public Long getCashAccountId() {
    return cashAccountId;
  }

  public void setCashAccountId(Long cashAccountId) {
    this.cashAccountId = cashAccountId;
  }

  public Long getBankAccountId() {
    return bankAccountId;
  }

  public void setBankAccountId(Long bankAccountId) {
    this.bankAccountId = bankAccountId;
  }

  public String getPayee() {
    return payee;
  }

  public void setPayee(String payee) {
    this.payee = payee;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(PaymentMethod paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  public String getPaymentProofUrl() {
    return paymentProofUrl;
  }

  public void setPaymentProofUrl(String paymentProofUrl) {
    this.paymentProofUrl = paymentProofUrl;
  }

  public Boolean getIsStandalone() {
    return isStandalone;
  }

  public void setIsStandalone(Boolean isStandalone) {
    this.isStandalone = isStandalone;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public Long getCreatedById() {
    return createdById;
  }

  public void setCreatedById(Long createdById) {
    this.createdById = createdById;
  }

  public Long getApprovedById() {
    return approvedById;
  }

  public void setApprovedById(Long approvedById) {
    this.approvedById = approvedById;
  }

  public UUID getLinkedVoucherId() {
    return linkedVoucherId;
  }

  public void setLinkedVoucherId(UUID linkedVoucherId) {
    this.linkedVoucherId = linkedVoucherId;
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

  public Instant getPostedAt() {
    return postedAt;
  }

  public void setPostedAt(Instant postedAt) {
    this.postedAt = postedAt;
  }

  // Relationship getters (read-only)
  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public Supplier getSupplier() {
    return supplier;
  }

  public void setSupplier(Supplier supplier) {
    this.supplier = supplier;
  }

  public BankAccount getCashAccount() {
    return cashAccount;
  }

  public void setCashAccount(BankAccount cashAccount) {
    this.cashAccount = cashAccount;
  }

  public BankAccount getBankAccount() {
    return bankAccount;
  }

  public void setBankAccount(BankAccount bankAccount) {
    this.bankAccount = bankAccount;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public User getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(User approvedBy) {
    this.approvedBy = approvedBy;
  }
}

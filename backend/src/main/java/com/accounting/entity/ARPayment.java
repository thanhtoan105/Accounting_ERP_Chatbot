package com.accounting.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ARPayment entity representing customer payment receipts in the accounts receivable module.
 * Receipts can be linked to sales invoices or standalone (advance payments/on-account).
 * Supports allocation to multiple invoices and reversal with audit trail.
 */
@Entity
@Table(name = "ar_payments")
public class ARPayment implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "customer_id", nullable = false)
  private Long customerId;

  @NotBlank
  @Size(max = 50)
  @Column(name = "receipt_number", nullable = false, length = 50)
  private String receiptNumber;

  @NotNull
  @Column(name = "receipt_date", nullable = false)
  private LocalDate receiptDate;

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
  @Column(name = "receipt_proof_url", length = 1000)
  private String receiptProofUrl;

  @NotNull
  @Column(name = "is_standalone", nullable = false)
  private Boolean isStandalone = false;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ReceiptStatus status = ReceiptStatus.DRAFT;

  @NotNull
  @Column(name = "created_by_id", nullable = false)
  private Long createdById;

  @Column(name = "posted_by_id")
  private Long postedById;

  @Column(name = "linked_voucher_id")
  private UUID linkedVoucherId;

  @Column(name = "reversal_reason", length = 500)
  private String reversalReason;

  @Column(name = "original_receipt_id")
  private UUID originalReceiptId;

  @Column(name = "reversing_receipt_id")
  private UUID reversingReceiptId;

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
  @JoinColumn(name = "customer_id", insertable = false, updatable = false)
  private Customer customer;

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
  @JoinColumn(name = "posted_by_id", insertable = false, updatable = false)
  private User postedBy;

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

  public Long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Long customerId) {
    this.customerId = customerId;
  }

  public String getReceiptNumber() {
    return receiptNumber;
  }

  public void setReceiptNumber(String receiptNumber) {
    this.receiptNumber = receiptNumber;
  }

  public LocalDate getReceiptDate() {
    return receiptDate;
  }

  public void setReceiptDate(LocalDate receiptDate) {
    this.receiptDate = receiptDate;
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

  public String getReceiptProofUrl() {
    return receiptProofUrl;
  }

  public void setReceiptProofUrl(String receiptProofUrl) {
    this.receiptProofUrl = receiptProofUrl;
  }

  public Boolean getIsStandalone() {
    return isStandalone;
  }

  public void setIsStandalone(Boolean isStandalone) {
    this.isStandalone = isStandalone;
  }

  public ReceiptStatus getStatus() {
    return status;
  }

  public void setStatus(ReceiptStatus status) {
    this.status = status;
  }

  public Long getCreatedById() {
    return createdById;
  }

  public void setCreatedById(Long createdById) {
    this.createdById = createdById;
  }

  public Long getPostedById() {
    return postedById;
  }

  public void setPostedById(Long postedById) {
    this.postedById = postedById;
  }

  public UUID getLinkedVoucherId() {
    return linkedVoucherId;
  }

  public void setLinkedVoucherId(UUID linkedVoucherId) {
    this.linkedVoucherId = linkedVoucherId;
  }

  public String getReversalReason() {
    return reversalReason;
  }

  public void setReversalReason(String reversalReason) {
    this.reversalReason = reversalReason;
  }

  public UUID getOriginalReceiptId() {
    return originalReceiptId;
  }

  public void setOriginalReceiptId(UUID originalReceiptId) {
    this.originalReceiptId = originalReceiptId;
  }

  public UUID getReversingReceiptId() {
    return reversingReceiptId;
  }

  public void setReversingReceiptId(UUID reversingReceiptId) {
    this.reversingReceiptId = reversingReceiptId;
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

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer customer) {
    this.customer = customer;
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

  public User getPostedBy() {
    return postedBy;
  }

  public void setPostedBy(User postedBy) {
    this.postedBy = postedBy;
  }
}

package com.accounting.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Entity representing an approval workflow for purchase bills and sales
 * invoices.
 * Implements maker-checker pattern with approver ≠ creator constraint.
 * Tracks approval lifecycle, threshold validation, and audit history.
 * Supports both AP (purchase bills) and AR (sales invoices) workflows.
 */
@Entity
@Table(name = "approval_workflows", indexes = {
    @Index(name = "idx_approval_workflows_company_id", columnList = "company_id"),
    @Index(name = "idx_approval_workflows_purchase_bill_id", columnList = "purchase_bill_id"),
    @Index(name = "idx_approval_workflows_sales_invoice_id", columnList = "sales_invoice_id"),
    @Index(name = "idx_approval_workflows_status", columnList = "status"),
    @Index(name = "idx_approval_workflows_company_status", columnList = "company_id,status")
})
public class ApprovalWorkflow implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "purchase_bill_id")
  private UUID purchaseBillId;

  @Column(name = "sales_invoice_id")
  private UUID salesInvoiceId;

  @NotNull
  @Column(name = "created_by_id", nullable = false)
  private Long createdById;

  @Column(name = "approved_by_id")
  private Long approvedById;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ApprovalWorkflowStatus status = ApprovalWorkflowStatus.PENDING;

  @NotNull
  @Positive
  @Column(name = "threshold_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal thresholdAmount;

  @NotNull
  @Column(name = "bill_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal billAmount;

  @NotNull
  @Column(name = "is_sensitive", nullable = false)
  private Boolean isSensitive = false;

  @Size(max = 1000)
  @Column(name = "approval_reason", length = 1000)
  private String approvalReason;

  @Size(max = 1000)
  @Column(name = "rejection_reason", length = 1000)
  private String rejectionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne
  @JoinColumn(name = "purchase_bill_id", insertable = false, updatable = false)
  private PurchaseBill purchaseBill;

  @ManyToOne
  @JoinColumn(name = "sales_invoice_id", insertable = false, updatable = false)
  private SalesInvoice salesInvoice;

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

  public UUID getPurchaseBillId() {
    return purchaseBillId;
  }

  public void setPurchaseBillId(UUID purchaseBillId) {
    this.purchaseBillId = purchaseBillId;
  }

  public UUID getSalesInvoiceId() {
    return salesInvoiceId;
  }

  public void setSalesInvoiceId(UUID salesInvoiceId) {
    this.salesInvoiceId = salesInvoiceId;
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

  public ApprovalWorkflowStatus getStatus() {
    return status;
  }

  public void setStatus(ApprovalWorkflowStatus status) {
    this.status = status;
  }

  public BigDecimal getThresholdAmount() {
    return thresholdAmount;
  }

  public void setThresholdAmount(BigDecimal thresholdAmount) {
    this.thresholdAmount = thresholdAmount;
  }

  public BigDecimal getBillAmount() {
    return billAmount;
  }

  public void setBillAmount(BigDecimal billAmount) {
    this.billAmount = billAmount;
  }

  public Boolean getIsSensitive() {
    return isSensitive;
  }

  public void setIsSensitive(Boolean isSensitive) {
    this.isSensitive = isSensitive;
  }

  public String getApprovalReason() {
    return approvalReason;
  }

  public void setApprovalReason(String approvalReason) {
    this.approvalReason = approvalReason;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
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

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(Instant approvedAt) {
    this.approvedAt = approvedAt;
  }

  public Instant getRejectedAt() {
    return rejectedAt;
  }

  public void setRejectedAt(Instant rejectedAt) {
    this.rejectedAt = rejectedAt;
  }

  // Relationship getters
  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public PurchaseBill getPurchaseBill() {
    return purchaseBill;
  }

  public void setPurchaseBill(PurchaseBill purchaseBill) {
    this.purchaseBill = purchaseBill;
  }

  public SalesInvoice getSalesInvoice() {
    return salesInvoice;
  }

  public void setSalesInvoice(SalesInvoice salesInvoice) {
    this.salesInvoice = salesInvoice;
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

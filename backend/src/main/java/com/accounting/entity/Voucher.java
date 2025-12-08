package com.accounting.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Voucher entity representing transactional documents in the accounting system.
 * Vouchers can be in draft, posted, or unposted status.
 */
@Entity
@Table(name = "vouchers")
public class Voucher implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotBlank
  @Column(name = "voucher_number", nullable = false, length = 50)
  private String voucherNumber;

  @NotNull
  @Column(name = "voucher_date", nullable = false)
  private LocalDate voucherDate;

  @Column(name = "period_id")
  private UUID periodId; // Foreign key to AccountingPeriod.id (auto-determined from voucher_date)

  @NotBlank
  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @NotBlank
  @Pattern(regexp = "^(draft|posted|unposted)$", message = "Status must be draft, posted, or unposted")
  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency = "VND"; // Default currency

  @NotNull
  @Column(name = "total_debit", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalDebit = BigDecimal.ZERO;

  @NotNull
  @Column(name = "total_credit", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalCredit = BigDecimal.ZERO;

  @NotNull
  @Column(name = "entered_by", nullable = false)
  private Long enteredBy; // User ID who created the voucher

  @Column(name = "posted_by")
  private Long postedBy; // User ID who posted the voucher (null for drafts)

  @Column(name = "posted_at")
  private Instant postedAt; // Timestamp when voucher was posted (null for drafts)

  @Column(name = "reversal_of")
  private UUID reversalOf; // Reference to original voucher if this is a reversal

  @Column(name = "reversed_by")
  private Long reversedBy; // User ID who created the reversal (deprecated, use reversedByVoucherId for voucher reference)

  @Column(name = "reversed_by_voucher_id")
  private UUID reversedByVoucherId; // Reference to reversal voucher if this voucher has been reversed

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 0L; // Optimistic locking version

  @Column(name = "is_locked", nullable = false)
  private Boolean isLocked = false; // Lock flag set when period is closed (prevents edits)

  // Relationships
  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne
  @JoinColumn(name = "entered_by", insertable = false, updatable = false)
  private User enteredByUser;

  @ManyToOne
  @JoinColumn(name = "posted_by", insertable = false, updatable = false)
  private User postedByUser;

  @ManyToOne
  @JoinColumn(name = "reversed_by", insertable = false, updatable = false)
  private User reversedByUser; // User ID who created the reversal (deprecated, use reversedByVoucherId)

  @ManyToOne
  @JoinColumn(name = "period_id", insertable = false, updatable = false)
  private AccountingPeriod period; // Period relationship (read-only)

  // OneToOne: This voucher reverses another voucher (if this is a reversal)
  // Owning side: reversal voucher has FK to original
  @jakarta.persistence.OneToOne
  @JoinColumn(name = "reversal_of", insertable = false, updatable = false)
  private Voucher reversalVoucher; // The original voucher that this voucher reverses

  // OneToOne: The voucher that reverses this voucher (if this voucher has been reversed)
  // Inverse side: original voucher has FK to reversal
  @jakarta.persistence.OneToOne
  @JoinColumn(name = "reversed_by_voucher_id", insertable = false, updatable = false)
  private Voucher reversedByVoucher; // The reversal voucher that reverses this voucher

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

  public AccountingPeriod getPeriod() {
    return period;
  }

  public void setPeriod(AccountingPeriod period) {
    this.period = period;
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

  public Long getPostedBy() {
    return postedBy;
  }

  public void setPostedBy(Long postedBy) {
    this.postedBy = postedBy;
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

  public Long getReversedBy() {
    return reversedBy;
  }

  public void setReversedBy(Long reversedBy) {
    this.reversedBy = reversedBy;
  }

  public UUID getReversedByVoucherId() {
    return reversedByVoucherId;
  }

  public void setReversedByVoucherId(UUID reversedByVoucherId) {
    this.reversedByVoucherId = reversedByVoucherId;
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

  public Boolean getIsLocked() {
    return isLocked;
  }

  public void setIsLocked(Boolean isLocked) {
    this.isLocked = isLocked;
  }

  // Relationship getters (read-only)
  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public User getEnteredByUser() {
    return enteredByUser;
  }

  public void setEnteredByUser(User enteredByUser) {
    this.enteredByUser = enteredByUser;
  }

  public User getPostedByUser() {
    return postedByUser;
  }

  public void setPostedByUser(User postedByUser) {
    this.postedByUser = postedByUser;
  }

  public User getReversedByUser() {
    return reversedByUser;
  }

  public void setReversedByUser(User reversedByUser) {
    this.reversedByUser = reversedByUser;
  }

  public Voucher getReversalVoucher() {
    return reversalVoucher;
  }

  public void setReversalVoucher(Voucher reversalVoucher) {
    this.reversalVoucher = reversalVoucher;
  }

  public Voucher getReversedByVoucher() {
    return reversedByVoucher;
  }

  public void setReversedByVoucher(Voucher reversedByVoucher) {
    this.reversedByVoucher = reversedByVoucher;
  }
}

package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * VoucherLine entity representing individual line items within a voucher.
 * Each line represents a single accounting entry with debit/credit amounts.
 */
@Entity
@Table(name = "voucher_lines")
public class VoucherLine implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "voucher_id", nullable = false)
  private UUID voucherId;

  @NotNull
  @Column(name = "line_number", nullable = false)
  private Integer lineNumber;

  @NotNull
  @Column(name = "account_id", nullable = false)
  private Long accountId; // ChartOfAccount ID

  @NotNull
  @Column(name = "debit", nullable = false, precision = 19, scale = 2)
  private BigDecimal debit = BigDecimal.ZERO;

  @NotNull
  @Column(name = "credit", nullable = false, precision = 19, scale = 2)
  private BigDecimal credit = BigDecimal.ZERO;

  @Column(name = "description", length = 500)
  private String description;

  // Dimension fields (optional - nullable)
  @Column(name = "customer_id")
  private Long customerId;

  @Column(name = "vendor_id")
  private Long vendorId; // Supplier/Vendor ID (entity may not exist yet)

  @Column(name = "cost_center_id")
  private Long costCenterId; // CostCenter ID (entity may not exist yet)

  @Column(name = "item_id")
  private Long itemId; // Item ID (entity may not exist yet)

  // Bank account ID for cash/bank tracking (used when account is 1121, 1122,
  // etc.)
  @Column(name = "bank_account_id")
  private Long bankAccountId;

  // Company ID for company scoping (inherited via Voucher relationship, but
  // stored for direct queries)
  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "voucher_id", insertable = false, updatable = false)
  private Voucher voucher;

  @ManyToOne
  @JoinColumn(name = "account_id", insertable = false, updatable = false)
  private ChartOfAccount account;

  @ManyToOne
  @JoinColumn(name = "customer_id", insertable = false, updatable = false)
  private Customer customer;

  @ManyToOne
  @JoinColumn(name = "bank_account_id", insertable = false, updatable = false)
  private BankAccount bankAccount;

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getVoucherId() {
    return voucherId;
  }

  public void setVoucherId(UUID voucherId) {
    this.voucherId = voucherId;
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

  public BigDecimal getDebit() {
    return debit;
  }

  public void setDebit(BigDecimal debit) {
    this.debit = debit;
  }

  public BigDecimal getCredit() {
    return credit;
  }

  public void setCredit(BigDecimal credit) {
    this.credit = credit;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Long customerId) {
    this.customerId = customerId;
  }

  public Long getVendorId() {
    return vendorId;
  }

  public void setVendorId(Long vendorId) {
    this.vendorId = vendorId;
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

  public Long getBankAccountId() {
    return bankAccountId;
  }

  public void setBankAccountId(Long bankAccountId) {
    this.bankAccountId = bankAccountId;
  }

  public BankAccount getBankAccount() {
    return bankAccount;
  }

  public void setBankAccount(BankAccount bankAccount) {
    this.bankAccount = bankAccount;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  // Relationship getters (read-only)
  public Voucher getVoucher() {
    return voucher;
  }

  public void setVoucher(Voucher voucher) {
    this.voucher = voucher;
  }

  public ChartOfAccount getAccount() {
    return account;
  }

  public void setAccount(ChartOfAccount account) {
    this.account = account;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer customer) {
    this.customer = customer;
  }
}

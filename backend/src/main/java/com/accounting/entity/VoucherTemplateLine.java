package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "voucher_template_lines")
public class VoucherTemplateLine implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "line_number", nullable = false)
  private Integer lineNumber;

  @Column(name = "debit_account_id", nullable = false)
  private Long debitAccountId;

  @Column(name = "credit_account_id", nullable = false)
  private Long creditAccountId;

  @Column(name = "default_description", length = 500)
  private String defaultDescription;

  @Column(name = "requires_customer", nullable = false)
  private boolean requiresCustomer;

  @Column(name = "requires_supplier", nullable = false)
  private boolean requiresSupplier;

  @Column(name = "lock_accounts", nullable = false)
  private boolean lockAccounts;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "voucher_template_id", nullable = false)
  private VoucherTemplate template;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "debit_account_id", insertable = false, updatable = false)
  private ChartOfAccount debitAccount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "credit_account_id", insertable = false, updatable = false)
  private ChartOfAccount creditAccount;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public Integer getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(Integer lineNumber) {
    this.lineNumber = lineNumber;
  }

  public Long getDebitAccountId() {
    return debitAccountId;
  }

  public void setDebitAccountId(Long debitAccountId) {
    this.debitAccountId = debitAccountId;
  }

  public Long getCreditAccountId() {
    return creditAccountId;
  }

  public void setCreditAccountId(Long creditAccountId) {
    this.creditAccountId = creditAccountId;
  }

  public String getDefaultDescription() {
    return defaultDescription;
  }

  public void setDefaultDescription(String defaultDescription) {
    this.defaultDescription = defaultDescription;
  }

  public boolean isRequiresCustomer() {
    return requiresCustomer;
  }

  public void setRequiresCustomer(boolean requiresCustomer) {
    this.requiresCustomer = requiresCustomer;
  }

  public boolean isRequiresSupplier() {
    return requiresSupplier;
  }

  public void setRequiresSupplier(boolean requiresSupplier) {
    this.requiresSupplier = requiresSupplier;
  }

  public boolean isLockAccounts() {
    return lockAccounts;
  }

  public void setLockAccounts(boolean lockAccounts) {
    this.lockAccounts = lockAccounts;
  }

  public VoucherTemplate getTemplate() {
    return template;
  }

  public void setTemplate(VoucherTemplate template) {
    this.template = template;
  }

  public ChartOfAccount getDebitAccount() {
    return debitAccount;
  }

  public void setDebitAccount(ChartOfAccount debitAccount) {
    this.debitAccount = debitAccount;
  }

  public ChartOfAccount getCreditAccount() {
    return creditAccount;
  }

  public void setCreditAccount(ChartOfAccount creditAccount) {
    this.creditAccount = creditAccount;
  }
}

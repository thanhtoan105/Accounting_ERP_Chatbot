package com.accounting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Default Account Entry entity representing individual account defaults within a DefaultAccount.
 * Each entry represents one row in the sub-table (e.g., Debit Account, Credit Account).
 */
@Entity
@Table(name = "default_account_entries")
public class DefaultAccountEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Column(name = "default_account_id", nullable = false)
  private Long defaultAccountId;

  @NotBlank
  @Size(max = 255)
  @Column(name = "column_name", nullable = false, length = 255)
  private String columnName;

  @Column(name = "account_id")
  private Long accountId;

  @NotNull
  @Column(name = "ordering_position", nullable = false)
  private Integer orderingPosition = 0;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "default_account_id", insertable = false, updatable = false)
  private DefaultAccount defaultAccount;

  @ManyToOne
  @JoinColumn(name = "account_id", insertable = false, updatable = false)
  private ChartOfAccount account;

  // Getters and setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getDefaultAccountId() {
    return defaultAccountId;
  }

  public void setDefaultAccountId(Long defaultAccountId) {
    this.defaultAccountId = defaultAccountId;
  }

  public String getColumnName() {
    return columnName;
  }

  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public Integer getOrderingPosition() {
    return orderingPosition;
  }

  public void setOrderingPosition(Integer orderingPosition) {
    this.orderingPosition = orderingPosition;
  }

  public DefaultAccount getDefaultAccount() {
    return defaultAccount;
  }

  public void setDefaultAccount(DefaultAccount defaultAccount) {
    this.defaultAccount = defaultAccount;
  }

  public ChartOfAccount getAccount() {
    return account;
  }

  public void setAccount(ChartOfAccount account) {
    this.account = account;
  }
}


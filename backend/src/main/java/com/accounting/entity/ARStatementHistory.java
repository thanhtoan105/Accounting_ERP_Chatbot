package com.accounting.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;

/**
 * ARStatementHistory entity for tracking all generated and sent customer
 * statements. Extends
 * StatementHistory with party_type = 'CUSTOMER'.
 */
@Entity
@DiscriminatorValue("CUSTOMER")
public class ARStatementHistory extends StatementHistory {

  // Customer relationship
  @ManyToOne
  @JoinColumn(name = "party_id", insertable = false, updatable = false)
  private Customer customer;

  @Override
  public StatementPartyType getPartyType() {
    return StatementPartyType.CUSTOMER;
  }

  @PrePersist
  @Override
  protected void onCreate() {
    super.onCreate();
    if (getStatementNumber() == null || getStatementNumber().isEmpty()) {
      setStatementNumber("STMT-" + System.currentTimeMillis());
    }
    // Set statement type to match format for backward compatibility
    if (getStatementType() == null && getFormat() != null) {
      setStatementType(getFormat());
    }
  }

  // Enum for statement format (kept for backward compatibility)
  public enum StatementFormat {
    SUMMARY,
    DETAILED
  }

  // Customer-specific getters/setters
  public Long getCustomerId() {
    return getPartyId();
  }

  public void setCustomerId(Long customerId) {
    setPartyId(customerId);
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer customer) {
    this.customer = customer;
  }

  // Format conversion helpers
  public StatementFormat getFormatEnum() {
    String fmt = getFormat();
    if (fmt == null)
      return null;
    try {
      return StatementFormat.valueOf(fmt);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public void setFormatEnum(StatementFormat format) {
    setFormat(format != null ? format.name() : null);
    setStatementType(format != null ? format.name() : null);
  }
}

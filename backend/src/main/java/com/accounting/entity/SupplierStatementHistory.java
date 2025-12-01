package com.accounting.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/**
 * SupplierStatementHistory entity for tracking all generated and sent supplier
 * statements. Extends
 * StatementHistory with party_type = 'SUPPLIER'.
 */
@Entity
@DiscriminatorValue("SUPPLIER")
public class SupplierStatementHistory extends StatementHistory {

  // Supplier relationship
  @ManyToOne
  @JoinColumn(name = "party_id", insertable = false, updatable = false)
  private Supplier supplier;

  @Override
  public StatementPartyType getPartyType() {
    return StatementPartyType.SUPPLIER;
  }

  // Enum for statement type (kept for backward compatibility)
  public enum StatementType {
    SUMMARY,
    DETAILED
  }

  // Enum for export format (kept for backward compatibility)
  public enum ExportFormat {
    PDF,
    EXCEL
  }

  // Supplier-specific getters/setters
  public Long getSupplierId() {
    return getPartyId();
  }

  public void setSupplierId(Long supplierId) {
    setPartyId(supplierId);
  }

  public Supplier getSupplier() {
    return supplier;
  }

  public void setSupplier(Supplier supplier) {
    this.supplier = supplier;
  }

  // Backward compatibility aliases
  public Instant getGenerationDate() {
    return getGeneratedAt();
  }

  public void setGenerationDate(Instant generationDate) {
    setGeneratedAt(generationDate);
  }

  public Long getGeneratedByUserId() {
    return getGeneratedById();
  }

  public void setGeneratedByUserId(Long generatedBy) {
    setGeneratedById(generatedBy);
  }

  public String getHash() {
    return getStatementHash();
  }

  public void setHash(String hash) {
    setStatementHash(hash);
  }

  public Instant getSentDate() {
    return getSentAt();
  }

  public void setSentDate(Instant sentDate) {
    setSentAt(sentDate);
  }

  // Statement type conversion helpers
  public StatementType getStatementTypeEnum() {
    String type = getStatementType();
    if (type == null)
      return null;
    try {
      return StatementType.valueOf(type);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public void setStatementTypeEnum(StatementType statementType) {
    setStatementType(statementType != null ? statementType.name() : null);
  }

  // Export format conversion helpers
  public ExportFormat getExportFormatEnum() {
    String fmt = getFormat();
    if (fmt == null)
      return null;
    try {
      return ExportFormat.valueOf(fmt);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public void setExportFormatEnum(ExportFormat format) {
    setFormat(format != null ? format.name() : null);
  }

  public User getGeneratedByUser() {
    return getGeneratedBy() != null ? super.getGeneratedBy() : null;
  }

  public void setGeneratedByUser(User user) {
    super.setGeneratedBy(user);
  }
}

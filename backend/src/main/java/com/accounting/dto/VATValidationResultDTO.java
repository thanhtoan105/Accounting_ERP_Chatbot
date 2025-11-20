package com.accounting.dto;

import com.accounting.entity.VatRate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for VAT validation results.
 */
public class VATValidationResultDTO {

  private boolean valid;
  private List<String> errors;
  private List<String> warnings;
  private VatRate validatedRate;
  private BigDecimal calculatedVATAmount;
  private BigDecimal documentVATAmount;
  private BigDecimal difference;
  private BigDecimal vatRatio;

  public VATValidationResultDTO() {
    this.errors = new ArrayList<>();
    this.warnings = new ArrayList<>();
  }

  public VATValidationResultDTO(boolean valid) {
    this();
    this.valid = valid;
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public List<String> getErrors() {
    return errors;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }

  public void addError(String error) {
    this.errors.add(error);
    this.valid = false;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings = warnings;
  }

  public void addWarning(String warning) {
    this.warnings.add(warning);
  }

  public VatRate getValidatedRate() {
    return validatedRate;
  }

  public void setValidatedRate(VatRate validatedRate) {
    this.validatedRate = validatedRate;
  }

  public BigDecimal getCalculatedVATAmount() {
    return calculatedVATAmount;
  }

  public void setCalculatedVATAmount(BigDecimal calculatedVATAmount) {
    this.calculatedVATAmount = calculatedVATAmount;
  }

  public BigDecimal getDocumentVATAmount() {
    return documentVATAmount;
  }

  public void setDocumentVATAmount(BigDecimal documentVATAmount) {
    this.documentVATAmount = documentVATAmount;
  }

  public BigDecimal getDifference() {
    return difference;
  }

  public void setDifference(BigDecimal difference) {
    this.difference = difference;
  }

  public BigDecimal getVatRatio() {
    return vatRatio;
  }

  public void setVatRatio(BigDecimal vatRatio) {
    this.vatRatio = vatRatio;
  }
}


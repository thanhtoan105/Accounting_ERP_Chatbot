package com.accounting.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for applying a voucher template.
 */
public class ApplyVoucherTemplateRequest {

  @NotNull(message = "Template ID is required")
  private UUID templateId;

  @NotNull(message = "Voucher date is required")
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate voucherDate;

  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  public UUID getTemplateId() {
    return templateId;
  }

  public void setTemplateId(UUID templateId) {
    this.templateId = templateId;
  }

  public LocalDate getVoucherDate() {
    return voucherDate;
  }

  public void setVoucherDate(LocalDate voucherDate) {
    this.voucherDate = voucherDate;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}


package com.accounting.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for posting a voucher.
 */
public class PostVoucherRequest {

  @NotNull(message = "Voucher ID is required")
  private UUID voucherId;

  private Boolean validateOnly = false; // If true, only validate without posting

  public PostVoucherRequest() {}

  public PostVoucherRequest(UUID voucherId, Boolean validateOnly) {
    this.voucherId = voucherId;
    this.validateOnly = validateOnly != null ? validateOnly : false;
  }

  public UUID getVoucherId() {
    return voucherId;
  }

  public void setVoucherId(UUID voucherId) {
    this.voucherId = voucherId;
  }

  public Boolean getValidateOnly() {
    return validateOnly;
  }

  public void setValidateOnly(Boolean validateOnly) {
    this.validateOnly = validateOnly != null ? validateOnly : false;
  }
}


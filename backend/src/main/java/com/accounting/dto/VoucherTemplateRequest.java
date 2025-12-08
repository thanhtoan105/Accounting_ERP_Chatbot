package com.accounting.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class VoucherTemplateRequest {

  @NotBlank(message = "Template name is required")
  @Size(max = 255, message = "Template name must be 255 characters or less")
  private String name;

  @Size(max = 1000, message = "Description must be 1000 characters or less")
  private String description;

  @JsonProperty("isActive")
  private Boolean active = Boolean.TRUE;

  @NotEmpty(message = "At least one template line is required")
  @Valid
  private List<VoucherTemplateLineRequest> lines = new ArrayList<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @JsonProperty("isActive")
  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public List<VoucherTemplateLineRequest> getLines() {
    return lines;
  }

  public void setLines(List<VoucherTemplateLineRequest> lines) {
    this.lines = lines;
  }
}

package com.accounting.dto;

import java.time.Instant;

/**
 * DTO for Customer responses. Used in API endpoints.
 */
public class CustomerDTO {

  private Long id;
  private Long companyId;
  private String code;
  private String name;
  private String taxCode;
  private String address;
  private String email;
  private String phone;
  private Boolean active;
  private Instant createdAt;
  private Instant updatedAt;

  public CustomerDTO() {}

  public CustomerDTO(
      Long id,
      Long companyId,
      String code,
      String name,
      String taxCode,
      String address,
      String email,
      String phone,
      Boolean active,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.companyId = companyId;
    this.code = code;
    this.name = name;
    this.taxCode = taxCode;
    this.address = address;
    this.email = email;
    this.phone = phone;
    this.active = active;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTaxCode() {
    return taxCode;
  }

  public void setTaxCode(String taxCode) {
    this.taxCode = taxCode;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
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
}


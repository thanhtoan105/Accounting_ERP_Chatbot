package com.accounting.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new Customer.
 * Customer code is auto-generated if not provided.
 */
public class CustomerCreateRequest {

  @Size(max = 32)
  private String code;

  @NotBlank(message = "Name is required")
  @Size(max = 255)
  private String name;

  @Size(max = 20)
  @Pattern(regexp = "^\\d{10}$", message = "Tax code must be exactly 10 digits")
  private String taxCode;

  @Size(max = 512)
  private String address;

  @Email(message = "Email must be a valid email address")
  @Size(max = 255)
  private String email;

  @Pattern(regexp = "^[0-9+\\-() ]+$", message = "Phone number must contain only digits, +, -, (, ), or spaces")
  @Size(max = 20)
  private String phone;

  private Boolean active = true;

  public CustomerCreateRequest() {}

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
}


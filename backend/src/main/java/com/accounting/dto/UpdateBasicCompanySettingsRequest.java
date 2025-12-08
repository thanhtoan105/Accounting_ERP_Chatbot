package com.accounting.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateBasicCompanySettingsRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "taxCode is required")
    @Pattern(regexp = "^\\d{10}$", message = "taxCode must be 10 digits")
    private String taxCode;

    @NotBlank(message = "address is required")
    private String address;

    @Email(message = "contactEmail must be a valid email")
    private String contactEmail;

    @Pattern(regexp = "^[0-9+()\\-\\s]{6,32}$", message = "contactPhone must be a valid phone number")
    private String contactPhone;

    // Represents the start date (year component ignored by business logic)
    private LocalDate fiscalYearStart;

    // Optional: if client already has a URL stored from upload integration
    private String logoUrl;

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

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public LocalDate getFiscalYearStart() {
        return fiscalYearStart;
    }

    public void setFiscalYearStart(LocalDate fiscalYearStart) {
        this.fiscalYearStart = fiscalYearStart;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }
}

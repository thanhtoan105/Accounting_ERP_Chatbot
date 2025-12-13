package com.accounting.dto.admin;

/** Response DTO for tenant provisioning. */
public record TenantProvisionResponse(
    Long companyId, String companyName, Long invitationId, String adminEmail, String message) {}
